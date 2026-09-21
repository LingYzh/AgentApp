package com.example.myapplication.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * 运行中子代理的进程内索引。
 *
 * 子会话已经落盘后，详情页通过会话 ID 精确取消对应的 [Job]；这里不持有或取消父任务。
 */
class SubagentRegistry {
    private class Entry(val job: Job) {
        val stoppedByUser = AtomicBoolean(false)
        val stopReason = AtomicReference<String?>(null)
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 在子会话对用户可见前登记其惰性 Job，消除“刚创建便停止”的窗口。 */
    fun register(conversationId: String, job: Job) {
        check(entries.putIfAbsent(conversationId, Entry(job)) == null) {
            "子代理会话已注册：$conversationId"
        }
    }

    /**
     * 请求停止一个仍在运行的子代理。首次请求保留理由，重复请求不会覆盖它。
     *
     * @return 仅当本次请求实际取消了已注册的运行任务时为 true。
     */
    fun stop(conversationId: String, reason: String): Boolean {
        val entry = entries[conversationId] ?: return false
        synchronized(entry) {
            if (!isPendingOrRunning(entry.job) || !entry.stoppedByUser.compareAndSet(false, true)) {
                // Only the runner unregisters. A repeated stop can race the runner's
                // cancellation handler, which still needs the first reason to report back.
                return false
            }
            entry.stopReason.set(reason)
            entry.job.cancel(CancellationException("用户已中止子代理"))
            return true
        }
    }

    fun isRunning(conversationId: String): Boolean =
        entries[conversationId]?.job?.let(::isPendingOrRunning) == true

    /** 仅供运行器在捕获到子 Job 取消时读取首次用户理由。 */
    internal fun userStopReason(conversationId: String): String? {
        val entry = entries[conversationId] ?: return null
        return if (entry.stoppedByUser.get()) entry.stopReason.get() else null
    }

    /** 区分“未被用户停止”与“用户没有填写理由”。 */
    internal fun wasStoppedByUser(conversationId: String): Boolean =
        entries[conversationId]?.stoppedByUser?.get() == true

    internal fun unregister(conversationId: String, job: Job) {
        val entry = entries[conversationId] ?: return
        if (entry.job === job) entries.remove(conversationId, entry)
    }

    private fun isPendingOrRunning(job: Job): Boolean = !job.isCompleted && !job.isCancelled
}
