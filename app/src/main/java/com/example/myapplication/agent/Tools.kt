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
    const val EDIT_FILE = "edit_file"
    const val DELETE_FILE = "delete_file"
    const val RUN_COMMAND = "run_command"
    const val ENTER_PLAN_MODE = "enter_plan_mode"
    const val EXIT_PLAN_MODE = "exit_plan_mode"

    val ALL_NAMES = listOf(
        WRITE_FILE, READ_FILE, LIST_FILES,
        SAVE_MEMORY, SEARCH_MEMORY, DELETE_MEMORY,
        USE_SKILL, SAVE_SKILL, RUN_SUBAGENT,
        EDIT_FILE, DELETE_FILE, RUN_COMMAND, ENTER_PLAN_MODE, EXIT_PLAN_MODE
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
            "在当前工作目录写入（创建或覆盖）一个文本文件。用于生成代码、文档、数据文件等。实际权限由运行时模式、目录范围和计划文件边界强制检查。",
            props("path" to "相对当前工作目录的文件路径，如 notes/todo.md", "content" to "完整文件内容", required = listOf("path", "content"))
        ),
        ToolSpec(
            READ_FILE,
            "读取当前工作目录中的文件。文本直接返回；图片和 PDF 在当前模型支持对应能力时作为原生多模态内容提供。",
            props("path" to "相对当前工作目录的文件路径", required = listOf("path"))
        ),
        ToolSpec(
            LIST_FILES,
            "列出当前工作目录中的文件（相对路径列表）。",
            props("path" to "可选，子目录路径；留空列出当前工作目录", required = emptyList())
        ),
        ToolSpec(
            SAVE_MEMORY,
            "保存或更新长期记忆（用户偏好、关键事实、项目状态等）。使用独立托管目录，不受任务文件目录范围影响；Readonly/Plan 禁止修改。",
            props("title" to "记忆标题", "content" to "完整记忆正文", "id" to "可选，search_memory 返回的现有记忆 ID；提供则更新，不提供则新建", required = listOf("title", "content"))
        ),
        ToolSpec(
            SEARCH_MEMORY,
            "在长期记忆中按关键词检索。",
            props("query" to "检索关键词", required = listOf("query"))
        ),
        ToolSpec(
            DELETE_MEMORY,
            "按 id 删除一条长期记忆。独立托管目录不受任务文件目录范围影响；Readonly/Plan 禁止修改。",
            props("id" to "记忆条目 id", required = listOf("id"))
        ),
        ToolSpec(
            USE_SKILL,
            "加载指定 Skill 的完整指令并遵循执行。先用它获取技能说明，再按说明行动。",
            props("name" to "Skill 名称", required = listOf("name"))
        ),
        ToolSpec(
            SAVE_SKILL,
            "创建或更新一个 Skill（可复用的指令集），供以后通过 use_skill 调用。独立托管目录不受任务文件目录范围影响；Readonly/Plan 禁止修改。",
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
        ),
        ToolSpec(
            EDIT_FILE,
            "在一个文本文件中精确替换唯一出现的 old_text。适合小范围编辑；路径可为当前工作目录相对路径或已授权的绝对路径。实际权限由运行时模式、目录范围和计划文件边界强制检查。",
            props(
                "path" to "文件路径",
                "old_text" to "当前文件中唯一存在的原文",
                "new_text" to "替换后的文本（可为空以删除）",
                required = listOf("path", "old_text", "new_text")
            )
        ),
        ToolSpec(
            DELETE_FILE,
            "删除一个普通文件，不支持目录、递归删除或符号链接。Readonly/Plan 禁止；Accept Edit 每次需用户确认；Auto 仍受目录范围和 Android 系统权限限制。",
            props("path" to "要永久删除的单个文件路径（当前工作目录相对路径或绝对路径）", required = listOf("path"))
        ),
        ToolSpec(
            RUN_COMMAND,
            "在本应用 UID 权限内通过 /system/bin/sh 执行一条命令。每次执行受当前权限模式和用户审批约束；文件工具目录范围不约束 shell，shell 仍受 Android 权限且不提供目录沙箱。",
            props("command" to "要执行的 shell 命令", "cwd" to "可选工作目录（默认当前工作目录）", required = listOf("command"))
        ),
        ToolSpec(
            ENTER_PLAN_MODE,
            "主代理进入计划模式。任何当前模式均可请求进入；子代理被硬性禁止。进入后只能写入本对话指定的计划文件。",
            props(required = emptyList())
        ),
        ToolSpec(
            EXIT_PLAN_MODE,
            "主代理提交当前计划文件给用户审批。仅计划模式可用；子代理被硬性禁止。获批后会切换到用户选择的执行模式。",
            props(required = emptyList())
        )
    )

    fun specs(planPath: String, includePlanControls: Boolean): List<ToolSpec> = ALL.filter { spec ->
        includePlanControls || spec.name !in setOf(ENTER_PLAN_MODE, EXIT_PLAN_MODE)
    }.map { spec ->
        when (spec.name) {
            WRITE_FILE -> spec.copy(description = "写入文本文件。相对路径位于当前工作目录；也可使用已授权的绝对路径。计划模式仅可写入 $planPath。实际权限由运行时模式、目录范围和计划文件边界强制检查。")
            READ_FILE -> spec.copy(description = "读取文本、图片或 PDF。路径可为当前工作目录相对路径或已授权的绝对路径。")
            LIST_FILES -> spec.copy(description = "递归列出文件，结果有数量上限。路径可为当前工作目录相对路径或已授权的绝对路径。")
            ENTER_PLAN_MODE -> spec.copy(description = "主代理进入计划模式；计划文件固定为 $planPath，子代理被硬性禁止。")
            EXIT_PLAN_MODE -> spec.copy(description = "主代理提交 $planPath 的当前内容给用户审批；子代理被硬性禁止。")
            else -> spec
        }
    }

    fun byName(name: String): ToolSpec? = ALL.firstOrNull { it.name == name }
}
