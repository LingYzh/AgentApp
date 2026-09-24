package com.example.myapplication.agent

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.sessionEffort
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

/**
 * 子代理执行器（Claude Code Task 风格）：主代理通过 run_subagent 工具提供完整任务描述，
 * 子代理以干净的上下文跑独立循环，禁止再嵌套 run_subagent。
 *
 * 模型解析优先级：设置页用户指定 > 主代理工具参数指定 > 继承主代理当前配置。
 */
class SubagentRunner(
    private val store: FileStore,
    private val providerFactory: ProviderFactory,
    private val onStatus: (String) -> Unit = {},
    private val registry: SubagentRegistry = SubagentRegistry(),
    private val deviceTools: AndroidDeviceTools? = null
) {
    suspend fun run(
        task: String,
        providerName: String?,
        model: String?,
        inherited: ProviderConfig,
        parentConversationId: String? = null,
        parentToolCallId: String? = null,
        permissionSession: PermissionSession? = null
    ): String {
        val appConfig = store.loadConfig()
        val resolvedBase: ProviderConfig = when {
            // 1. 用户在设置中强制指定
            appConfig.subagentProviderId != null -> {
                val p = appConfig.providers.firstOrNull { it.id == appConfig.subagentProviderId }
                requireNotNull(p) { "子代理供应商已不可用，请重新选择" }
                p.copy(model = requireNotNull(appConfig.subagentModel?.takeIf { it.isNotBlank() }) {
                    "请先为子代理选择模型"
                })
            }
            // 2. 主代理在工具参数中指定
            providerName != null || model != null -> {
                val base = providerName?.let { name ->
                    appConfig.providers.firstOrNull {
                        it.name.equals(name, ignoreCase = true) || it.id == name
                    }
                } ?: inherited
                base.copy(model = model?.takeIf { it.isNotBlank() }
                    ?: inherited.model.takeIf { base.id == inherited.id }
                    ?: error("指定其他供应商时必须同时指定子代理模型"))
            }
            // 3. 兜底：继承主代理
            else -> inherited
        }
        val resolved = resolvedBase.copy(reasoningEffort = resolvedBase.sessionEffort(inherited.reasoningEffort))

        onStatus("子代理运行中（${resolved.model}）…")
        // Persist the running record before the first provider call. The root conversation
        // list can therefore discover a child even while the parent tool is still waiting.
        val conversation = Conversation(
            title = task.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(32) ?: "子代理任务",
            providerIdOverride = resolved.id,
            modelOverride = resolved.model,
            parentConversationId = parentConversationId,
            parentToolCallId = parentToolCallId,
            executionStatus = "running",
            reasoningEffortOverride = resolved.reasoningEffort,
            permissionMode = permissionSession?.conversation?.permissionMode ?: com.example.myapplication.data.model.PermissionMode.ACCEPT_EDIT,
            allowedDirectories = permissionSession?.conversation?.allowedDirectories ?: emptyList(),
            workingDirectory = permissionSession?.conversation?.workingDirectory
        )
        conversation.messages += ChatMessage(role = "user", content = task)

        return supervisorScope {
            val engine = AgentEngine(store, providerFactory, subagentRunner = null, deviceTools = deviceTools)
            val child = async(start = CoroutineStart.LAZY) {
                engine.run(
                    conversation = conversation,
                    config = resolved,
                    maxLoops = appConfig.maxAgentLoops,
                    depth = 1,
                    systemOverride = "你是主 Agent 委派的子代理。独立完成交给你的任务，充分利用可用的本地工具，" +
                        "最终回复要包含完整结论（主 Agent 只能看到你的最终回复）。",
                    allowedTools = Tools.SUBAGENT_DEFAULT.toSet(),
                    permissionSession = permissionSession?.childSession()
                )
                resultFrom(conversation)
            }
            // Register before persisting the visible conversation. A detail view can therefore
            // always stop a saved running child, including one cancelled before it starts.
            registry.register(conversation.id, child)
            try {
                store.saveConversation(conversation)
                child.start()
                val result = child.await()
                val lastAssistant = conversation.messages.lastOrNull { it.role == "assistant" }
                conversation.executionStatus = if (lastAssistant != null && !lastAssistant.isError && lastAssistant.content.isNotBlank()) {
                    "completed"
                } else {
                    "failed"
                }
                store.saveConversation(conversation)
                onStatus(if (conversation.executionStatus == "completed") "子代理已完成" else "子代理执行失败")
                result
            } catch (e: CancellationException) {
                // A cancelled child is recoverable only when this parent coroutine is still
                // active and the registry records a user stop. Parent cancellation must escape.
                try {
                    currentCoroutineContext().ensureActive()
                } catch (parentCancellation: CancellationException) {
                    conversation.executionStatus = "cancelled"
                    store.saveConversation(conversation)
                    onStatus("子代理已取消")
                    throw parentCancellation
                }
                if (registry.wasStoppedByUser(conversation.id)) {
                    val reason = registry.userStopReason(conversation.id).orEmpty()
                    conversation.executionStatus = "cancelled"
                    conversation.stopReason = reason
                    store.saveConversation(conversation)
                    onStatus("子代理已由用户中止")
                    userStoppedResult(reason)
                } else {
                    conversation.executionStatus = "cancelled"
                    store.saveConversation(conversation)
                    onStatus("子代理已取消")
                    throw e
                }
            } catch (e: Exception) {
                conversation.executionStatus = "failed"
                store.saveConversation(conversation)
                onStatus("子代理执行失败")
                "错误: 子代理执行失败: ${e.message}"
            } finally {
                registry.unregister(conversation.id, child)
                // Covers an unexpected callback/provider failure after the normal status path;
                // the latest streamed messages and lifecycle state remain available on disk.
                store.saveConversation(conversation)
            }
        }
    }

    private fun resultFrom(conversation: Conversation): String {
        val lastAssistant = conversation.messages.lastOrNull { it.role == "assistant" }
        return when {
            lastAssistant == null -> "(子代理未产生回复)"
            lastAssistant.isError -> {
                val detail = lastAssistant.content.ifBlank { "子代理未产生有效回复" }
                "错误: $detail"
            }
            lastAssistant.content.isBlank() -> "(子代理未产生回复)"
            else -> lastAssistant.content
        }
    }

    private fun userStoppedResult(reason: String): String {
        val visibleReason = reason.ifBlank { "（未提供）" }
        return "用户已中止子代理。理由：$visibleReason。请根据理由调整后续策略。"
    }
}
