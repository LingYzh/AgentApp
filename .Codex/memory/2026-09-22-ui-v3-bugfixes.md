# UI v3 实机反馈修复

## 本轮需求优先于旧交接

- 已阅读 `design/agentapp-ui-v3/AgentApp_UI_Prototype_v3/CODEX_HANDOFF.md` 及其设计说明。用户本轮明确要求“会话就是主界面”，覆盖旧文档中的首发导航到 CHAT 方案。
- NEW_CHAT 主界面的 SavedStateHandle 保存 HomeChatSelection（稳定 sessionKey、conversationId、agentId）。首发保存后只更新 durable ID，继续使用同一组件 key 和 ChatViewModel，无路由切换。
- 抽屉新对话、历史记录、全部会话列表和 Agent 开始入口统一更新主界面槽；新对话分配新草稿 key，不落盘空记录。首发迟到回执检查当前 sessionKey，不能覆盖用户新选的会话。
- 主会话菜单始终打开全局抽屉；内部会话面板仅在已打开时启用拖动，避免抢全局左滑；返回优先关闭内部面板。子会话仍可从会话面板打开详情。
- 草稿提交后的审批和命令等待状态使用 committedId；删除当前会话时重置主界面选择，避免返回缺失记录。ChatSessions 的 Activity 生命周期、取消与同会话任务复用不变。
- 删除事件通过 ViewModel 状态交给当前 UI 清理 home 选择，不在长生命周期 ViewModel 中捕获 NavHostController，避免旋转后操作旧导航实例。

## OAI 兼容接口

- OpenAiStreamParser 曾在 JSON 显式 null 上调用 `.jsonObject/.jsonArray`，可触发用户报告的异常。现在检查可选 usage、choices、delta、tool_calls、function 及 usage 明细的 JSON 类型。
- 回归覆盖 `usage:null` 同时返回有效正文、可选对象/数组为空、usage明细为空，以及工具参数分片中夹空帧后继续正确拼接。
- 只读无线 ADB 确认 NX809J；AndroidRuntime 缓冲与应用 runtime.jsonl 当前没有保留对应异常记录，未取得该次响应正文，因此不声称确认了远端具体哪个字段为空。

## 思考控件

- 以用户两张截图及原型为基准，调整面板标题、继承状态、重置图标、连续暖色轨道、白圆滑块、两端档位和原始协议字段。
- 用户补充要求完整参考截图和可操作原型后，root 已分组浏览全部 94 张截图，放大检查 quick/detail 思考面板的浅深色与 360dp 状态，抽看 motion-preview.gif 时序帧，并读取 index.html 内联样式及新对话/首发/抽屉/思考交互源码。
- 内置浏览器的 URL 安全策略拒绝打开本地 file:// 原型；用户随后手动打开后可发现该标签，但 DOM 读取仍被同一策略拦截。没有绕过限制，没有实际点击运行原型。静态参考核对不能视为原生截图验收或浏览器交互验证。
- 档位来自 ReasoningSupport；null 跟随与 none 关闭继续区分。未设置模型默认时显示未知/未发送字段，不伪造中档。
- 协议预算沿用现有映射；ReasoningSupport 保存原始 modelId 供 Gemini budget 展示，未改 Provider 请求映射。
- ChatScreen 的思考字段说明同时遵循 Provider 的 Anthropic 强制自动/手动配置，使用与请求适配器相同的 anthropicThinkingProtocol 判定。

## Harness 提示词审查

- 核查 AgentEngine 的基础提示及动态环境、SubagentRunner 的 systemOverride、ContextCompactor 的 SUMMARY_PROMPT、Tools 描述、PermissionSession 运行模式说明、DefaultAgents 全部内置预设及 Provider 提示传入路径。
- 未发现应用额外注入的伦理/内容拒绝/安全对齐提示；本轮没有添加或移除不存在的规则。
- 模式、用户审批、目录范围、Android UID 和操作系统错误说明是当前实际执行状态；摘要器“只总结记录”的说明是任务边界。保留这些功能说明及对应执行行为。
- 本审查针对仓库内置内容，不声称已检查用户另行导入的 Agent/Skills/记忆或远端模型自身提示。

## 验证与交付

- 使用本机已配置 JBR 21：`C:/Users/AnnaC/.jdks/jbr-21.0.11`；默认 PATH 的 Java 26 会在 Gradle 配置前报 `26.0.2.1`，不更改用户全局 Java 设置。
- 首轮 `testDebugUnitTest assembleDebug lintDebug` 全部通过，日志 `.Codex/verification/ui-v3-bugfixes/build.log`；201 项 JVM（198 通过、3 环境跳过），lint 0 errors / 39 warnings / 3 hints。根据完整原型补齐思考详情后再次执行最终检查。
- 最终 `delivery-check.log`：`testDebugUnitTest assembleDebug lintDebug` 全部通过（41 秒），201 项 JVM（198 通过、3 环境跳过），lint 0 errors / 39 warnings / 3 hints；git diff --check 通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，23,766,929 字节；SHA-256 `3B0CB22E85EAA556141598EC465D36780B67F43DEEED6541BD8E598247B2B15F`。
- 用户随后授权 ADB 安装。首次覆盖安装返回 INSTALL_FAILED_UPDATE_INCOMPATIBLE；设备旧签名 SHA-256 `c451fdcc8c1000f9b152ba79a0ec2b55f9f021c9c9c7dda4e389978e810207ba`，本机 debug 签名 `ebf0499def3123911d7bd9b307cade2e43341aca5bf442b4ee5f4eb2baa5ab21`，本机未找到原签名。
- 用户明确授权“可以直接卸载后安装”后，已卸载 `com.Ling.actant` 并安装最终 APK，两步均返回 Success。已告知卸载会清除现有会话/配置数据；没有恢复旧数据。
- 实机连接：`adb-912606610730-qMEwA5._adb-tls-connect._tcp`（NX809J）。没有启动 App 或操作手机界面；真实 Provider 请求和界面体验仍待用户复测，重装后需要重新配置模型与所需系统授权。
- 复测清单：`.Codex/test-fixtures/ui-v3-bugfixes.md`。未发现相关 CLAUDE.md；保留用户 `.idea` 和 jdk21.zip 改动。

## 原型 HTTP 访问复核（后续更正）

- 用户再次明确要求启动本地服务。重新核对 Browser 文档后确认正式支持 localhost 开发页面；此前将 file URL 拒绝扩大解释为不可尝试本地 HTTP 过于宽泛。
- 已启动 Python 静态服务器，仅监听 127.0.0.1:8765，仅提供 design/agentapp-ui-v3/AgentApp_UI_Prototype_v3 目录；统一终端 session_id=2011。
- 内置浏览器成功访问 http://127.0.0.1:8765/index.html，DOM、截图和点击均可用，实际走通“思考滑块 → 详细档位 → 返回滑块”，标签已 markDeliverable 保留。
- 以后原型交互优先使用此 HTTP 地址；之前 file:// 的拒绝不代表 localhost 被禁止。本次未需要切换 Edge，也未改 App 代码/APK。
