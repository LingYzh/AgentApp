# AgentApp UI v3 · 页面与源码映射

基线 `2c947234ab0d29ede6e999193717288363195b7a`。以下路径相对于 `app/src/main/java/com/example/myapplication/`。这些是阅读入口，不要求一页一个Kotlin文件；编辑与列表常在同一文件中。Codex应先读取当前树，不根据原型新建重复的Screen类。

| 编号 | 原型ID | 页面/状态 | 现有入口 | 实施重点 |
| --- | --- | --- | --- | --- |
| 01 | home | 新对话 | MainActivity.kt + ui/chat/ChatScreen.kt + ConversationsScreen.kt | 新增NEW_CHAT草稿起始路由，首发才创建；不得直接跳到无ID的CHAT |
| 02 | chat | 完成会话 | ui/chat/ChatScreen.kt / ChatActivityCards.kt | 正文、轻量工具行、消息操作、生成文件 |
| 03 | running | 执行中 | ui/chat/ChatScreen.kt / PermissionUi.kt | 真实工具状态、停止/取消、审批入口，不伪造后台任务 |
| 04 | history | 历史会话 | ui/chat/ConversationsScreen.kt | 保留历史管理和加载；不再是冷启动页 |
| 05 | session | 会话面板 | ui/chat/ChatScreen.kt / ChatActivityCards.kt | 重排现有计划、子代理、产物展示，不新增任务调度 |
| 06 | child | 只读子代理 | ui/chat/ChatScreen.kt / ChatActivityCards.kt | 轻量工具行，保留只读与中止，不新增再次委派 |
| 07 | plan | 计划审批 | ui/chat/PermissionUi.kt / agent/ | 计划阅读、反馈、按现有权限状态审批 |
| 08 | agents | Agents | ui/agents/AgentsScreen.kt | 助手列表、创建新草稿、编辑/迁移 |
| 09 | agent-edit | Agent编辑 | ui/agents/AgentsScreen.kt | 系统提示、头像、默认模型与工具语义 |
| 10 | files | 工作区 | ui/files/FilesScreen.kt / data/store/FileStore.kt | 文件列表、目录、批量操作与边界 |
| 11 | file-view | 文件预览与Diff | ui/files/FilesScreen.kt / data/store/FileChanges.kt | 阅读/源码/变更，和对话内联Diff共用呈现规则 |
| 12 | skills | Skills | ui/skills/SkillsScreen.kt | ZIP导入、单项/所选/全部导出，不新增启停 |
| 13 | skill-edit | Skill编辑 | ui/skills/SkillsScreen.kt | 元信息和SKILL.md正文 |
| 14 | memory | 长期记忆 | ui/memory/MemoryScreen.kt / data/store/ | 列表与迁移，不擅自更改召回策略 |
| 15 | memory-edit | 记忆编辑 | ui/memory/MemoryScreen.kt | 编辑独立Markdown正文 |
| 16 | providers | 模型配置 | ui/providers/ProvidersScreen.kt | radio设默认；管理模式checkbox，保留行身份 |
| 17 | provider-edit | 模型配置编辑 | ui/providers/ProvidersScreen.kt / data/model/Models.kt | 所有Provider字段、能力、获取模型与测试不丢失 |
| 18 | settings | 设置 | ui/settings/SettingsScreen.kt | 主题、循环、子代理模型、授权与数据入口 |
| 19 | backup | 备份与迁移 | data/backup/ + ui/transfer/ConfigurationTransferUi.kt | 独立配置迁移与覆盖全部数据严格区分 |
| 20 | commands | 自动放行命令 | ui/settings/SettingsScreen.kt / agent/CommandPolicy.kt | 精确命令增删与原权限边界 |
| 21 | onboarding | 未配置模型 | ui/chat/ + data/model/Models.kt | 新对话的配置引导状态，无空会话落盘 |
| 22 | error | 请求失败 | ui/chat/ChatScreen.kt | 保留原错误、消息和已发生副作用 |
| 23 | empty | 空列表 | 各列表已有empty state | 轻量提示与真实创建入口 |
| 24 | styles | 组件与配色 | ui/theme/Theme.kt | 仅设计验收入口，不放进业务导航 |
| 25 | markdown | Markdown排版 | ui/components/MarkdownContent.kt / MarkdownDocument.kt | 仅样本页；生产共用原生阅读样式 |
| 26 | tool-lab | 工具记录样本 | ui/chat/ChatActivityCards.kt / ToolPresentation.kt / data/store/FileChanges.kt | 仅验收样本；主/子会话工具记录也必须改为此模式 |

## 公共交互

| 交互 | 数据与现有实现 | v3要求 |
| --- | --- | --- |
| 命令展开 | ToolCallInfo.argumentsJson + 按toolCallId匹配的ChatMessage.content/isError | 收起一句话，原地Shell；无结果≠空输出 |
| 文件编辑展开 | 成功结果fileChange、FileChanges.diff(change)、FileDiffResult | 直接内联Diff，统计/行号/降级标志准确，不弹FileDiffDialog |
| 其他工具 | ToolPresentation / 原始参数与结果 | 无外框展开行；不丢参数查看与复制 |
| 模型选择 | ProviderConfig.capabilitiesFor(modelId)、ModelResolver、switchModel | 每模型有效媒体能力图标；模型/Agent/全局优先级不改 |
| 思考滑块/详细选项 | ReasoningSupport.efforts、reasoningEffortOverride、updateReasoningEffort | null继承与none区分；原始字段值；松手提交 |
| 上下文/压缩 | ContextUsageUi.kt、ContextWindows、compactContext/cancelCompaction | 分段/空余/未知/服务端报告、明确确认 |
| 权限/命令审批 | PermissionUi/PermissionSession/PermissionCoordinator | 保留实际等待与批准链，界面展开不是审批 |
| 附件 | AttachmentPreview.kt、MessageAttachment.delivery、附件存储 | native/workspace区分，新草稿转会话时保留正确所有权 |
| 消息编辑/删除 | MessageActions.kt、ConversationContext | 修改历史不自动执行，删除不撤销副作用 |
| Provider连通性/模型目录 | ProvidersScreen.kt中既有ViewModel方法 | 保留真实加载/错误、避免动画导致重复请求 |
| 配置迁移 | ConfigurationTransfer.kt + ConfigurationTransferUi.kt | prepareImport预览、冲突、Key/headers与ZIP路径校验 |
| 批量管理 | ui/components/ListSelection.kt | 多选不改默认，保留stable key与滚动 |
| 顶部反馈 | 既有Snackbar调用点 + 新展示host | 当前活动顶栏下占位，高度进出动画 |

## NEW_CHAT导航改造需要共同调整的位置

`MainActivity.Routes / TopLevelRoutes / NavHost.startDestination / AppDrawerSheetContent`、新对话草稿ViewModel/组件、当前 `ConversationsViewModel.create` 的复用边界、首条消息的保存和运行任务归属、`safeNavigate/safePopBackStack` 的返回栈及恢复逻辑。

不得在组合函数中直接调用create，不在每个onResume导航NEW_CHAT，不传空conversationId给当前ChatViewModel。草稿->真实会话的提交与任务交接必须在原生实现中验证；详见V3_REFINEMENTS.md第6节。

## 原型专有内容

styles/markdown/tool-lab、桌面评审导航、视口开关、演示计时与fixture只用于设计检查；正式App不新增这些菜单。session是既有信息重新组织，error/empty/onboarding是状态，不是新持久实体。所有浏览器示例Parser/Diff均不是生产实现替换品。
