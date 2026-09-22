# AgentApp 原生 UI V3 交付与实机检查

研究与实现起点：`2c947234ab0d29ede6e999193717288363195b7a`。保留 Kotlin / Jetpack Compose，未引入 WebView、JS 渲染器、原型样本或第 24/25/26 页。

## 改动清单

| 文件 / 区域 | 实际改动 |
| --- | --- |
| `ui/theme/Theme.kt` | V3 暖色语义配色、白色主按钮前景、正文行高、主题颜色局部过渡。 |
| `ui/components/UiScaffold.kt`、`UiTextButton.kt`、`UiMotion.kt` | 顶栏下反馈占位、48dp 文字按钮、按压反馈、退出内容禁止交互、代码宽度估算。 |
| `MainActivity.kt` | NEW_CHAT 默认入口、历史抽屉入口、真实 ID 的 CHAT 路由、轻位移返回动画，保留导航恢复。 |
| `ui/chat/ChatSessions.kt`、`NewChatScreen.kt`、`ChatScreen.kt` | Activity 内共享任务所有权、草稿与输入/附件状态恢复、首次成功保存后移交、不在 onResume 重置。发送/停止、附件、模型选择及历史工具呈现同步调整。 |
| `data/store/FileStore.kt` | 首发独立回执与幂等保存；会话 JSON 使用临时文件替换。打开草稿不落空历史。 |
| `agent/PermissionSession.kt` | 新增草稿回执纳入既有 Accept Edit 权限元数据保护；不放宽任何目录或执行权限。 |
| `ui/chat/ToolRecord.kt`、`ChatActivityCards.kt`、`ui/components/InlineCodePanel.kt` | 透明工具行，精确命令状态、真实 command / 对应结果、空输出/未返回区分、原地复制/换行/局部滚动/高度切换。 |
| `ui/files/InlineFileDiff.kt`、`FileDiffView.kt`、`data/store/FileChanges.kt` | 已保存快照的后台 Diff 计算、增删统计与行号、符号/侧线、长行滚动、复制 Diff 及前后原文；缺失/截断/预算降级明确说明。聊天和文件页共用。 |
| `ui/chat/ModelPicker.kt`、`ContextUsageUi.kt` | 分组搜索模型及四类原生能力/来源；独立思考滑块与详细原始值，null 继承与 none 关闭区分，松手提交；八类上下文比例保留。 |
| `ui/chat/ConversationsScreen.kt`、`ui/agents/AgentsScreen.kt` | 历史创建入口与 Agent 对话入口进入草稿；删除历史前取消并等待运行任务的最终保存，避免写回已删历史。 |
| `ui/providers/ProvidersScreen.kt` | 默认 radio / 管理 checkbox 的独立语义与局部过渡；模型候选显示原生能力来源，保留配置字段、获取模型、连接测试与迁移。 |
| `ui/components/MarkdownContent.kt`、`ui/files/FilesScreen.kt`、`ui/memory/MemoryScreen.kt` | 原生 Markdown 共用阅读、源码/预览切换；代码默认横滚、原文复制，用户换行才启动尺寸过渡，流式采样不重播动画。 |
| `ui/components/ListSelection.kt`、`ui/skills/SkillsScreen.kt`、`ui/settings/SettingsScreen.kt` | 接入公共按钮/反馈及列表相关局部过渡，保留真实管理和存储行为。 |
| `ui/chat/AttachmentPreview.kt`、`MessageActions.kt`、`PermissionUi.kt` | 接入公共文字按钮；删除确认实心按钮在暗色也为白色前景。保留预览、消息操作和授权链。 |
| `ui/transfer/ConfigurationTransferUi.kt` | 迁移反馈接入当前页面顶栏下占位，不再浮盖底部操作。迁移数据格式、冲突预览与 Key/headers 规则未改。 |
| `DraftCommitTest.kt`、`ui/chat/ToolRecordTest.kt` | 并发/重试/恢复/空草稿/参数恢复，以及退出码与真实终止状态回归。 |
| `AGENTS.md`、`.Codex/memory/2026-09-22-ui-v3.md` | 同步草稿、任务所有权、工具记录与验证约定；相关目录没有 CLAUDE.md。 |

## 构建与边界

- APK：`app/build/outputs/apk/debug/app-debug.apk`。由用户自行覆盖安装；本轮没有操作手机、自动 push 或 release。
- 使用本机 Android Studio JBR，运行 `testDebugUnitTest assembleDebug lintDebug`；最终结果与 SHA-256 在本文件末尾记录。
- 草稿回执位于 App 私有 `draft-receipts/`，不属于历史会话或迁移资源。草稿附件只保存引用；消费/离开草稿不会删除可能已被历史或工作区引用的文件。
- 任务仅在 Activity 的 ViewModel 生命周期内继续；没有后台常驻服务或进程死亡后自动重跑。首发落盘后进程死亡，恢复已保存消息，不擅自重复发送。
- applicationId / namespace 差异、目录与权限检查、取消传播、单层子代理、Provider 协议/重放/可选参数及附件迁移规则继续保留。

## 用户实机步骤

1. **启动与恢复**：冷启动应为新对话；输入文字、选择模型/Agent/思考/权限并添加一个自选附件，先不发送。旋转、切后台、打开并取消文件选择器、进入权限设置再返回，检查当前输入与选择。反复进入未发送草稿不增加历史。
2. **首发与任务交接**：发送一次并快速再次点击，历史只能出现一个新会话和一条首发消息。生成中返回再从历史打开，确认没有重复请求；停止按钮应即时生效。无 Provider 时应引导配置，不能创建空历史。
3. **命令记录**：在主会话要求执行你确认安全的命令，如 `printf 'line 1\nline 2\n'`。批准前、执行中、完成后的文字应不同；完成收起只有“运行了命令”。展开、复制、切换换行不会重跑。用 `true` 检查空输出，用 `sh -c 'exit 3'` 检查真实退出码；主动停止长命令后检查中止记录。
4. **文件修改**：选择一个全新测试文件名，在 App 工作区新增再修改一行。收起检查文件名与增删行数；展开直接出现 Diff，检查旧/新行号、+/-、长行横滚、换行、原文复制和高度切换。缺失快照的旧记录只能显示降级说明。
5. **模型与思考**：模型搜索列表检查每项图片/PDF/音频/视频能力与来源。思考滑块拖动时只预览，松手后应用；详细列表显示中文与 wireValue。重置跟随必须区别于 none 关闭。
6. **V2 功能**：检查上下文八类颜色、容量余量/未知容量、服务端未报告值；检查 Provider 正常模式 radio 设默认、管理 checkbox 只选择。用自选临时资源验证迁移，不对真实资源执行批量删除。
7. **动效与阅读**：浅深色、系统大字体下快速反向展开工具，切换页签、思考详细面板和管理模式；检查箭头在文字右侧、点击区足够大、无残留点击层。阅读长代码/表格，流式输出期间上滑应保持阅读位置，复制原文正确。
8. **系统协作**：关闭系统动画后重复展开/返回；开启 TalkBack 检查工具展开语义、模型能力描述和按钮焦点；检查真实 IME 弹起/收起及返回手势。

## 尚待实机确认

未执行设备 UI 测试或真实付费 Provider 请求。360/412 逻辑宽度、大字体、TalkBack、IME/预测返回、进程恢复时序、真实网关与流式观感尚待上述实机验收；本地构建和 JVM 测试不能替代这些验证。


## 最终验证结果

- `testDebugUnitTest`、`assembleDebug`、`lintDebug` 均通过；最终日志：`.Codex/verification/ui-v3/final-build.log`。
- JVM：192 项，189 通过，0 失败、0 错误、3 跳过。跳过为 Windows 环境无法创建符号链接的现有路径测试。
- Lint：0 错误、39 警告、4 提示。HTML：`app/build/reports/lint-results-debug.html`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，24,568,051 字节。
- SHA-256：`4864EF2B2EF5024B0F497388E22AFE997AD813A93794C3C18A6480420BDEE74B`。
- `git diff --check` 通过。设备 UI、真实 Provider、安装与实机检查仍由用户执行。
