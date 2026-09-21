package com.example.myapplication.agent

import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ToolSpec
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.myapplication.provider.ProviderJson
import kotlinx.coroutines.CancellationException

/**
 * 工具执行器。所有文件操作限定在应用私有目录内（FileStore 做路径越界校验）。
 *
 * @param allowedTools 允许的工具名集合，null = 全部（受 onRunSubagent 是否为 null 限制）
 * @param onRunSubagent 子代理执行回调（task, providerName?, model?）；为 null 时 run_subagent 工具不可用（保证单层子代理）
 */
class ToolExecutor(
    private val store: FileStore,
    private val allowedTools: Set<String>? = null,
    private val onRunSubagent: (suspend (task: String, providerName: String?, model: String?) -> String)? = null
) {
    fun specs(): List<ToolSpec> = Tools.ALL.filter { spec ->
        (allowedTools == null || spec.name in allowedTools) &&
            (spec.name != Tools.RUN_SUBAGENT || onRunSubagent != null)
    }

    /** 执行工具，返回给模型的文本结果。错误以 "错误: " 前缀返回而不是抛出。 */
    suspend fun execute(name: String, argumentsJson: String): String {
        if (specs().none { it.name == name }) return "错误: 工具 '$name' 不可用"
        val args = try {
            ProviderJson.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "错误: 工具参数不是合法 JSON: ${e.message}"
        }
        fun arg(key: String): String {
            return args[key]?.jsonPrimitive?.content ?: ""
        }

        return try {
            when (name) {
                Tools.WRITE_FILE -> {
                    val path = arg("path")
                    val f = store.writeWorkspace(path, arg("content"))
                    "已写入 ${f.name}（${f.length()} 字节）"
                }
                Tools.READ_FILE -> store.readWorkspace(arg("path"))
                Tools.LIST_FILES -> {
                    val files = store.listWorkspace(arg("path"))
                    if (files.isEmpty()) "(工作区为空)"
                    else files.take(200).joinToString("\n") +
                        if (files.size > 200) "\n... 共 ${files.size} 个文件" else ""
                }
                Tools.SAVE_MEMORY -> {
                    val entry = store.saveMemory(arg("title").ifEmpty { "未命名记忆" }, arg("content"))
                    "已保存记忆 [id=${entry.id}] ${entry.title}"
                }
                Tools.SEARCH_MEMORY -> {
                    val hits = store.searchMemory(arg("query"))
                    if (hits.isEmpty()) "(未找到相关记忆)"
                    else hits.take(5).joinToString("\n\n") { (e, body) ->
                        "[id=${e.id}] ${e.title}\n${body.take(1000)}"
                    }
                }
                Tools.DELETE_MEMORY -> {
                    val id = arg("id")
                    val existed = store.listMemories().any { it.id == id }
                    store.deleteMemory(id)
                    if (existed) "已删除记忆 $id" else "错误: 记忆 $id 不存在"
                }
                Tools.USE_SKILL -> {
                    val body = store.readSkillBody(arg("name"))
                        ?: return "错误: Skill '${arg("name")}' 不存在"
                    "以下是 Skill '${arg("name")}' 的指令，请遵循执行：\n\n$body"
                }
                Tools.SAVE_SKILL -> {
                    val rawName = arg("name")
                    val skillName = com.example.myapplication.ui.skills.formatKebabCase(rawName).ifBlank { "unnamed-skill" }
                    store.saveSkill(skillName, arg("description"), arg("content"))
                    "已成功保存 Skill '$skillName'（路径：skills/$skillName/SKILL.md，已自动规范为标准目录包，后续或后续对话可直接通过 use_skill 加载调用）"
                }
                Tools.RUN_SUBAGENT -> {
                    val runner = onRunSubagent ?: return "错误: 当前上下文不允许调用子代理"
                    runner(
                        arg("task"),
                        arg("provider_name").ifBlank { null },
                        arg("model").ifBlank { null }
                    )
                }
                else -> "错误: 未知工具 '$name'"
            }
        } catch (e: CancellationException) {
            // CancellationException 是控制流信号，不能伪装成工具返回值。
            throw e
        } catch (e: Exception) {
            "错误: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
