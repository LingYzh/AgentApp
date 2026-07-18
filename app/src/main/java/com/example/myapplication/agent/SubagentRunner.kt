package com.example.myapplication.agent

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ProviderFactory

/**
 * 子代理执行器（Claude Code Task 风格）：主代理通过 run_subagent 工具提供完整任务描述，
 * 子代理以干净的上下文跑独立循环，禁止再嵌套 run_subagent。
 *
 * 模型解析优先级：设置页用户指定 > 主代理工具参数指定 > 继承主代理当前配置。
 */
class SubagentRunner(
    private val store: FileStore,
    private val providerFactory: ProviderFactory,
    private val onStatus: (String) -> Unit = {}
) {
    suspend fun run(
        task: String,
        providerName: String?,
        model: String?,
        inherited: ProviderConfig
    ): String {
        val appConfig = store.loadConfig()
        val resolved: ProviderConfig = when {
            // 1. 用户在设置中强制指定
            appConfig.subagentProviderId != null -> {
                val p = appConfig.providers.firstOrNull { it.id == appConfig.subagentProviderId }
                p?.copy(model = appConfig.subagentModel ?: p.model) ?: inherited
            }
            // 2. 主代理在工具参数中指定
            providerName != null || model != null -> {
                val base = providerName?.let { name ->
                    appConfig.providers.firstOrNull {
                        it.name.equals(name, ignoreCase = true) ||
                            it.model.equals(name, ignoreCase = true)
                    }
                } ?: inherited
                base.copy(model = model ?: base.model)
            }
            // 3. 兜底：继承主代理
            else -> inherited
        }

        onStatus("子代理运行中（${resolved.model}）…")
        val conversation = Conversation(title = "子代理任务")
        conversation.messages += ChatMessage(role = "user", content = task)

        val engine = AgentEngine(store, providerFactory, subagentRunner = null)
        return try {
            engine.run(
                conversation = conversation,
                config = resolved,
                maxLoops = appConfig.maxAgentLoops,
                depth = 1,
                systemOverride = "你是主 Agent 委派的子代理。独立完成交给你的任务，充分利用可用的本地工具，" +
                    "最终回复要包含完整结论（主 Agent 只能看到你的最终回复）。",
                allowedTools = Tools.SUBAGENT_DEFAULT.toSet()
            )
            conversation.messages.lastOrNull { it.role == "assistant" && !it.isError }?.content
                ?: "(子代理未产生回复)"
        } catch (e: Exception) {
            "错误: 子代理执行失败: ${e.message}"
        }
    }
}
