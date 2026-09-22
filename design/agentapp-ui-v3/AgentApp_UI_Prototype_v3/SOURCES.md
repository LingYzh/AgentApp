# AgentApp UI v3 · 来源与核验范围

核查日期2026-09-22。v3以用户五张截图和本轮明确反馈为直接视觉需求，不对截图所在产品版本作识别或保证。暖白/陶土方案沿用上轮已约定的主题，未重新声称测量了三个竞品最新客户端。

## 本轮官方实现参考

- Android Animation Quick Guide：https://developer.android.com/develop/ui/compose/animation/quick-guide （读取页面显示2026-09-16更新）。参考AnimatedVisibility、尺寸变化、NavHost转场、可打断与性能取舍。本文毫秒/间距值是自己的设计参数，并非官方强制值。
- Android Navigation：https://developer.android.com/guide/navigation 。参考路由/返回栈架构；新草稿与首发幂等是本项目设计，不是官方替项目保证。
- Android Lazy Lists：https://developer.android.com/develop/ui/compose/lists 。参考稳定key和列表布局；实际API以本仓库依赖版本为准。
- Android Layout Basics：https://developer.android.com/develop/ui/compose/layouts/basics 。参考Row和子元素对齐。
- W3C C39：https://www.w3.org/WAI/WCAG22/Techniques/css/C39 。参考prefers-reduced-motion与减少非必要运动。

## 通过GitHub连接器核对的项目代码

仓库 `LingYzh/AgentApp`，master仍为 `2c947234ab0d29ede6e999193717288363195b7a`。

路径前缀：
https://github.com/LingYzh/AgentApp/blob/2c947234ab0d29ede6e999193717288363195b7a/app/src/main/java/com/example/myapplication/

| 路径与读取范围 | 已确认的相关事实 |
| --- | --- |
| MainActivity.kt 98–169、255–315 | 当前CONVERSATIONS是起点，CHAT需要conversationId；已有安全导航和保存恢复设置 |
| ui/chat/ConversationsScreen.kt 70–176 | create(agentId)立即创建并保存Conversation；因此新草稿不能在打开时调用它 |
| ui/chat/ChatActivityCards.kt 1–145 | ToolActivityRow已有参数/结果/状态、FileChanges.diff与FileDiffDialog，外层Surface/多层padding需调整 |
| data/store/FileChanges.kt 1–155 | DiffLine有新旧行号、FileDiffResult有added/removed、previewOmitted与fallback；现有快照和计算预算应保留 |

前两轮已核对的继续有效入口：AGENTS.md、Models.kt、ChatScreen.kt、ContextUsageUi.kt、MarkdownContent.kt/MarkdownDocument.kt、PermissionUi.kt、ProvidersScreen.kt、SkillsScreen.kt、SettingsScreen.kt、ConfigurationTransfer。没有声称本轮再次审计整个仓库或运行其Android测试。

## 设计证据与限制

原型Shell/Diff是基于需求构造的fixture，不是本轮运行仓库命令的截图。HTML和Kotlin参考不改变真实Provider行为。上轮品牌/竞品研究记录仅作为历史说明保留在archive/SOURCES_v2.md；不得把其中的滚动更新描述当成已对本机当前客户端逐项实测。

浏览器行为结果见TEST_REPORT.md及三个results JSON。没有Android安装、设备控制、真实API调用、仓库推送或release操作。
