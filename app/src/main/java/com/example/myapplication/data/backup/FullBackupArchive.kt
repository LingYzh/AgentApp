package com.example.myapplication.data.backup

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.store.FileStore
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Stream-based full archive shared by SAF export and the pre-restore recovery copy. */
internal class FullBackupArchive(private val store: FileStore) {
    private val root = store.configFile.parentFile!!.canonicalFile
    private val entries = listOf("config.json", "agents.json", "conversations", "memory", "skills", "workspace", "avatars")

    data class RestoreResult(val fileCount: Int, val missingAvatars: Int, val safetyBackup: File)

    fun export(output: OutputStream): Int {
        var count = 0
        ZipOutputStream(output.buffered()).use { zip ->
            entries.forEach { name ->
                val base = File(root, name)
                if (!base.exists()) return@forEach
                base.walkTopDown().onEnter { directory ->
                    require(directory.canonicalPath.startsWith(root.path + File.separator)) { "备份目录越界：$name" }
                    true
                }.filter { it.isFile }.forEach { file ->
                    require(file.canonicalPath.startsWith(root.path + File.separator)) { "备份文件越界：$name" }
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    if (relative == "agents.json") {
                        val agents = store.json.decodeFromString<List<AgentProfile>>(file.readText())
                        // Only the archive copy uses portable references; never rewrite the live index.
                        val portable = agents.map { agent ->
                            agent.copy(avatarPath = agent.avatarPath?.let { avatarReference(it) })
                        }
                        zip.write(store.json.encodeToString(portable).toByteArray(Charsets.UTF_8))
                    } else file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    fun restore(input: InputStream): RestoreResult {
        val stage = File(root, ".restore-${UUID.randomUUID()}").apply { check(mkdir()) }
        val rollback = File(root, ".restore-old-${UUID.randomUUID()}")
        var keepRollback = false
        try {
            var count = 0
            val seen = mutableSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(!name.contains('\\') && !name.startsWith('/') && ':' !in name &&
                        name.split('/').none { it == ".." || it == "." }) { "备份包含非法路径：$name" }
                    require(name.substringBefore('/') in entries) { "不是完整备份条目：$name" }
                    val destination = File(stage, name).canonicalFile
                    require(destination.path.startsWith(stage.canonicalPath + File.separator)) { "备份路径越界" }
                    require(seen.add(destination.path)) { "备份包含重复条目：$name" }
                    if (entry.isDirectory) check(destination.mkdirs() || destination.isDirectory)
                    else {
                        check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                        destination.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(count > 0) { "备份为空或不是 ZIP 文件" }
            File(stage, "config.json").takeIf { it.exists() }?.let {
                store.json.decodeFromString<AppConfig>(it.readText())
            }
            File(stage, "conversations").walkTopDown().filter { it.isFile && it.extension == "json" }.forEach {
                store.json.decodeFromString<Conversation>(it.readText())
            }
            var missing = 0
            File(stage, "agents.json").takeIf { it.exists() }?.let { index ->
                val agents = store.json.decodeFromString<List<AgentProfile>>(index.readText())
                val restored = agents.map { agent ->
                    val relative = agent.avatarPath?.let(::avatarReference)
                    val avatar = relative?.let { File(stage, it) }
                    val path = if (avatar?.isFile == true && avatar.length() > 0) File(root, relative).absolutePath
                    else { if (agent.avatarPath != null) missing++; null }
                    agent.copy(avatarPath = path)
                }
                index.writeText(store.json.encodeToString(restored))
            }

            // Do not remove anything until both the input and a portable safety copy are complete.
            store.backupsDir.mkdirs()
            val backup = File(store.backupsDir, "backup-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")
            try { backup.outputStream().use { export(it) } }
            catch (error: Exception) { backup.delete(); throw error }
            check(rollback.mkdir())
            val movedOld = mutableListOf<String>()
            val installed = mutableListOf<String>()
            try {
                entries.forEach { name ->
                    val old = File(root, name)
                    if (old.exists()) {
                        check(old.renameTo(File(rollback, name))) { "无法暂存现有数据：$name" }
                        movedOld += name
                    }
                    val incoming = File(stage, name)
                    if (incoming.exists()) {
                        check(incoming.renameTo(File(root, name))) { "无法恢复：$name" }
                        installed += name
                    }
                }
                store.init()
            } catch (error: Exception) {
                installed.asReversed().forEach { File(root, it).deleteRecursively() }
                movedOld.asReversed().forEach { name ->
                    if (!File(rollback, name).renameTo(File(root, name))) keepRollback = true
                }
                if (keepRollback) throw IllegalStateException("恢复失败，原数据保留在 ${rollback.name}；自动备份：${backup.name}", error)
                throw error
            }
            return RestoreResult(count, missing, backup)
        } finally {
            stage.deleteRecursively()
            if (!keepRollback) rollback.deleteRecursively()
        }
    }

    /** Accept legacy Android absolute paths, but only their explicit avatars/<filename> suffix. */
    private fun avatarReference(path: String): String {
        val normalized = path.replace('\\', '/')
        val relative = if (normalized.startsWith("avatars/")) normalized
            else {
                require(normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized)) { "头像引用无效" }
                require("/avatars/" in normalized) { "头像不在 avatars 目录" }
                "avatars/" + normalized.substringAfterLast("/avatars/")
            }
        val filename = relative.removePrefix("avatars/")
        require(filename.isNotBlank() && '/' !in filename && ':' !in filename && filename != "." && filename != "..") { "头像引用越界" }
        return relative
    }
}
