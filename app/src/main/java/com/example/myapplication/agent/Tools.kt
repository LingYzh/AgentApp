package com.example.myapplication.agent

import com.example.myapplication.provider.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject

/** 全部工具的 JSON Schema 定义 */
object Tools {

    const val WRITE_FILE = "write_file"
    const val READ_FILE = "read_file"
    const val LIST_FILES = "list_files"
    const val SAVE_MEMORY = "save_memory"
    const val SEARCH_MEMORY = "search_memory"
    const val DELETE_MEMORY = "delete_memory"
    const val USE_SKILL = "use_skill"
    const val SAVE_SKILL = "save_skill"
    const val RUN_SUBAGENT = "run_subagent"

    val ALL_NAMES = listOf(
        WRITE_FILE, READ_FILE, LIST_FILES,
        SAVE_MEMORY, SEARCH_MEMORY, DELETE_MEMORY,
        USE_SKILL, SAVE_SKILL, RUN_SUBAGENT
    )

    /** 子代理可用的默认工具（不含 run_subagent，保证单层） */
    val SUBAGENT_DEFAULT = ALL_NAMES - RUN_SUBAGENT

    private fun props(vararg pairs: Pair<String, String>, required: List<String>): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                pairs.forEach { (name, desc) ->
                    putJsonObject(name) {
                        put("type", "string")
                        put("description", desc)
                    }
                }
            }
            putJsonArray("required") { required.forEach { add(it) } }
        }

    val ALL: List<ToolSpec> = listOf(
        ToolSpec(
            WRITE_FILE,
            "在工作区写入（创建或覆盖）一个文本文件。用于生成代码、文档、数据文件等。",
            props("path" to "相对工作区的文件路径，如 notes/todo.md", "content" to "完整文件内容", required = listOf("path", "content"))
        ),
        ToolSpec(
            READ_FILE,
            "读取工作区中一个文本文件的内容。",
            props("path" to "相对工作区的文件路径", required = listOf("path"))
        ),
        ToolSpec(
            LIST_FILES,
            "列出工作区中的文件（相对路径列表）。",
            props("path" to "可选，子目录路径；留空列出全部", required = emptyList())
        ),
        ToolSpec(
            SAVE_MEMORY,
            "把一条重要信息保存到长期记忆（用户偏好、关键事实、项目状态等）。",
            props("title" to "记忆标题", "content" to "记忆正文", required = listOf("title", "content"))
        ),
        ToolSpec(
            SEARCH_MEMORY,
            "在长期记忆中按关键词检索。",
            props("query" to "检索关键词", required = listOf("query"))
        ),
        ToolSpec(
            DELETE_MEMORY,
            "按 id 删除一条长期记忆。",
            props("id" to "记忆条目 id", required = listOf("id"))
        ),
        ToolSpec(
            USE_SKILL,
            "加载指定 Skill 的完整指令并遵循执行。先用它获取技能说明，再按说明行动。",
            props("name" to "Skill 名称", required = listOf("name"))
        ),
        ToolSpec(
            SAVE_SKILL,
            "创建或更新一个 Skill（可复用的指令集），供以后通过 use_skill 调用。",
            props(
                "name" to "Skill 名称（短横线命名，如 pdf-report）",
                "description" to "一句话说明何时使用",
                "content" to "Skill 的完整指令正文（Markdown）",
                required = listOf("name", "description", "content")
            )
        ),
        ToolSpec(
            RUN_SUBAGENT,
            "把独立、耗时或需要隔离上下文的子任务委派给子代理执行（例如批量文件处理、长篇调研、多步骤生成）。" +
                "子代理拥有干净的对话上下文和全部本地工具，完成后把最终结论返回给你。" +
                "简单问题、需要与用户交互的任务不要委派。",
            props(
                "task" to "交给子代理的完整任务描述与执行指令（子代理看不到本会话历史，需自包含）",
                "provider_name" to "可选，指定运行子代理的模型配置名；不传则继承当前模型",
                "model" to "可选，指定模型名；不传则继承当前模型",
                required = listOf("task")
            )
        )
    )

    fun byName(name: String): ToolSpec? = ALL.firstOrNull { it.name == name }
}
