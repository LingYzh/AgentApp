package com.example.myapplication.agent

import android.os.Process as AndroidProcess
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Runs a command through the app UID's normal Android shell, without root or Shizuku.
 *
 * The fixed launcher waits for a parent acknowledgement before evaluating [command]. This
 * lets us prove that `setsid` created a process group owned by the launcher first, avoiding
 * untracked commands when a device does not provide a usable `setsid` implementation.
 */
object ShellCommandRunner {
    private const val MAX_OUTPUT_BYTES = 64 * 1024
    private const val MAX_PROTOCOL_LINE_BYTES = 512
    private const val COMMAND_TIMEOUT_MILLIS = 30_000L
    private const val STARTUP_TIMEOUT_MILLIS = 4_000L
    private const val TERMINATE_GRACE_MILLIS = 700L
    private const val READY_PREFIX = "__agent_shell_ready__:"
    private const val EXIT_PREFIX = "__agent_shell_exit__:"

    /*
     * $1 is the command passed as one ProcessBuilder argument and $2 is the trusted nonce.
     * Nothing supplied by the model is interpolated into this script.
     *
     * The launcher deliberately remains alive after reporting the command exit status. Its
     * PID remains the process-group leader until the parent signals the group, so a later
     * signal cannot accidentally target a recycled PGID.
     */
    private val LAUNCHER_SCRIPT = """
        printf '%s%s:%s\n' '__agent_shell_ready__:' "${'$'}2" "${'$'}${'$'}"
        IFS= read -r gate || exit 125
        [ "${'$'}gate" = "go:${'$'}2" ] || exit 125
        trap ':' TERM
        (
            trap - TERM
            exec /system/bin/sh -c "${'$'}1"
        ) </dev/null &
        command_pid=${'$'}!
        wait "${'$'}command_pid"
        command_status=${'$'}?
        printf '\n%s%s:%s\n' '__agent_shell_exit__:' "${'$'}2" "${'$'}command_status"
        while :; do
            IFS= read -r hold
        done
    """.trimIndent()

    /** Returns command text on success and an `错误:`-prefixed message for execution failures. */
    suspend fun run(command: String, cwd: String): String {
        if (command.isBlank()) return "错误: 命令不能为空"
        val startedAt = System.nanoTime()
        var running: RunningCommand? = null
        try {
            val startupBudget = remainingMillis(startedAt)
            if (startupBudget <= 0) return timeoutMessage()
            running = startVerified(command, cwd, startupBudget)
                ?: return "错误: 当前设备不支持受控 shell 会话（无法验证 setsid 进程组）"

            val gate = "go:${running.nonce}\n".toByteArray(StandardCharsets.UTF_8)
            withContext(Dispatchers.IO) {
                running.process.outputStream.write(gate)
                running.process.outputStream.flush()
            }

            val commandExit = withTimeoutOrNull(remainingMillis(startedAt).coerceAtLeast(1L)) {
                running.exitStatus.await()
            } ?: return timeoutMessage()

            val output = running.output.text().trimEnd()
            return if (commandExit == 0) {
                if (output.isBlank()) "退出码 0" else "退出码 0\n$output"
            } else {
                buildString {
                    append("错误: 命令退出码 ")
                    append(commandExit)
                    if (output.isNotBlank()) {
                        append('\n')
                        append(output)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return "错误: 命令执行失败：${error.message ?: error.javaClass.simpleName}"
        } finally {
            running?.let { command ->
                withContext(NonCancellable) { cleanupVerified(command) }
            }
        }
    }

    /**
     * Tries platform `setsid` first, then toybox directly. The command is still behind the
     * launcher gate while each candidate is being checked.
     */
    private suspend fun startVerified(command: String, cwd: String, timeoutMillis: Long): RunningCommand? {
        val candidates = listOf(
            listOf("/system/bin/setsid"),
            listOf("/system/bin/toybox", "setsid")
        )
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        for (prefix in candidates) {
            val remaining = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
            if (remaining == 0L) return null
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val process = startLauncher(prefix, command, cwd, nonce) ?: continue
            val running = RunningCommand(process, nonce)
            try {
                val handshake = withTimeoutOrNull(minOf(STARTUP_TIMEOUT_MILLIS, remaining)) {
                    running.readyPid.await()
                }
                val identity = handshake?.let(::processIdentity)
                if (handshake != null && identity != null && ownsIsolatedGroup(handshake)) {
                    running.leaderPid = handshake
                    running.leaderStartTime = identity.second
                    return running
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) { cleanupBeforeGate(running) }
                throw error
            } catch (_: Exception) {
                // A missing or unsupported launcher can close stdout before handshaking.
            }
            withContext(NonCancellable) { cleanupBeforeGate(running) }
        }
        return null
    }

    private suspend fun startLauncher(
        setsidPrefix: List<String>,
        command: String,
        cwd: String,
        nonce: String
    ): Process? = withContext(NonCancellable) {
        try {
            val arguments = buildList {
                addAll(setsidPrefix)
                add("/system/bin/sh")
                add("-c")
                add(LAUNCHER_SCRIPT)
                add("agent-shell-launcher")
                add(command)
                add(nonce)
            }
            ProcessBuilder(arguments)
                .directory(File(cwd))
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            null
        }
    }

    /** The PID must be both the reported launcher and its own PGID, never the app's group. */
    private fun ownsIsolatedGroup(leaderPid: Int): Boolean = runCatching {
        val groupId = processIdentity(leaderPid)?.first
        val appGroupId = processIdentity(AndroidProcess.myPid())?.first
        leaderPid > 1 && appGroupId != null && groupId == leaderPid && groupId != appGroupId
    }.getOrDefault(false)

    // stat's comm field may contain spaces or parentheses; fields after its last ')' are stable.
    private fun processIdentity(pid: Int): Pair<Int, Long>? = runCatching {
        val stat = File("/proc/$pid/stat").readText()
        val fields = stat.substringAfterLast(')').trim().split(Regex("\\s+"))
        fields[2].toInt() to fields[19].toLong() // pgrp (5), starttime (22)
    }.getOrNull()

    /** No command has crossed the launcher gate yet, so terminating only the launcher is safe. */
    private suspend fun cleanupBeforeGate(running: RunningCommand) {
        withContext(Dispatchers.IO) { running.process.destroy() }
        closeStreams(running.process)
        joinReader(running)
    }

    /**
     * The launcher traps TERM while it holds the PGID, so a second identity check can safely
     * KILL TERM-ignoring descendants without allowing a recycled PGID to be signalled.
     */
    private suspend fun cleanupVerified(running: RunningCommand) {
        val leaderPid = running.leaderPid
        if (leaderPid != null) {
            signalOwnedGroup(running, OsConstants.SIGTERM)
            delay(TERMINATE_GRACE_MILLIS)
            signalOwnedGroup(running, OsConstants.SIGKILL)
        } else {
            withContext(Dispatchers.IO) { running.process.destroy() }
        }
        closeStreams(running.process)
        joinReader(running)
    }

    private fun signalOwnedGroup(running: RunningCommand, signal: Int) {
        val leaderPid = running.leaderPid ?: return
        val startTime = running.leaderStartTime ?: return
        if (processIdentity(leaderPid)?.second != startTime) return
        if (!ownsIsolatedGroup(leaderPid)) return
        runCatching { Os.kill(-leaderPid, signal) }
    }

    private suspend fun joinReader(running: RunningCommand) {
        withContext(Dispatchers.IO) {
            running.reader.interrupt()
            running.reader.join(2_000L)
        }
    }

    private fun closeStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun remainingMillis(startedAt: Long): Long =
        COMMAND_TIMEOUT_MILLIS - (System.nanoTime() - startedAt) / 1_000_000L

    private fun timeoutMessage(): String = "错误: 命令执行超时（30 秒）"

    private class RunningCommand(
        val process: Process,
        val nonce: String,
        val output: BoundedOutput = BoundedOutput()
    ) {
        val readyPid = CompletableDeferred<Int>()
        val exitStatus = CompletableDeferred<Int>()
        var leaderPid: Int? = null
        var leaderStartTime: Long? = null
        val reader: Thread = Thread(
            ProtocolReader(process.inputStream, nonce, output, readyPid, exitStatus),
            "agent-shell-output"
        ).apply {
            isDaemon = true
            start()
        }
    }

    /** Merged stdout/stderr buffer. It keeps draining after the visible 64 KiB is full. */
    private class BoundedOutput {
        private val data = ByteArrayOutputStream(MAX_OUTPUT_BYTES)

        @Synchronized
        fun append(bytes: ByteArray, offset: Int, length: Int) {
            val remaining = MAX_OUTPUT_BYTES - data.size()
            if (remaining > 0) data.write(bytes, offset, minOf(length, remaining))
        }

        @Synchronized
        fun appendByte(value: Int) {
            if (data.size() < MAX_OUTPUT_BYTES) data.write(value)
        }

        @Synchronized
        fun text(): String = data.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Removes only exact nonce-bound control lines. Long command output lines are streamed into
     * [BoundedOutput] without being accumulated in a parser buffer. A command able to inspect
     * its parent process may deliberately escape this protocol or call setsid itself; this is
     * process cleanup, not a hardened OS sandbox.
     */
    private class ProtocolReader(
        private val input: InputStream,
        private val nonce: String,
        private val output: BoundedOutput,
        private val readyPid: CompletableDeferred<Int>,
        private val exitStatus: CompletableDeferred<Int>
    ) : Runnable {
        override fun run() {
            val chunk = ByteArray(4 * 1024)
            val line = ByteArray(MAX_PROTOCOL_LINE_BYTES)
            var lineSize = 0
            var lineOverflowed = false
            try {
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    for (index in 0 until count) {
                        val value = chunk[index].toInt() and 0xff
                        if (value == '\n'.code) {
                            if (lineOverflowed) {
                                output.appendByte(value)
                            } else if (!consumeProtocolLine(line, lineSize)) {
                                output.append(line, 0, lineSize)
                                output.appendByte(value)
                            }
                            lineSize = 0
                            lineOverflowed = false
                        } else if (lineOverflowed) {
                            output.appendByte(value)
                        } else if (lineSize < line.size) {
                            line[lineSize++] = value.toByte()
                        } else {
                            output.append(line, 0, lineSize)
                            output.appendByte(value)
                            lineOverflowed = true
                        }
                    }
                }
                if (!lineOverflowed && lineSize > 0 && !consumeProtocolLine(line, lineSize)) {
                    output.append(line, 0, lineSize)
                }
            } catch (_: Exception) {
                // Stream closure during cleanup is expected.
            } finally {
                if (!readyPid.isCompleted) readyPid.completeExceptionally(IllegalStateException("shell launcher did not handshake"))
                if (!exitStatus.isCompleted) exitStatus.completeExceptionally(IllegalStateException("shell command ended without exit status"))
                runCatching { input.close() }
            }
        }

        private fun consumeProtocolLine(line: ByteArray, size: Int): Boolean {
            val text = String(line, 0, size, StandardCharsets.UTF_8).removeSuffix("\r")
            val readyStart = "$READY_PREFIX$nonce:"
            if (!readyPid.isCompleted && text.startsWith(readyStart)) {
                text.removePrefix(readyStart).toIntOrNull()?.let {
                    readyPid.complete(it)
                    return true
                }
            }
            val exitStart = "$EXIT_PREFIX$nonce:"
            if (!exitStatus.isCompleted && text.startsWith(exitStart)) {
                text.removePrefix(exitStart).toIntOrNull()?.let {
                    exitStatus.complete(it)
                    return true
                }
            }
            return false
        }
    }
}
