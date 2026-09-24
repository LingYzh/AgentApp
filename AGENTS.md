# AgentApp

## 项目目标

- 完善 Android 移动端 harness，并尽可能支持用户授权的设备操作。
- 用户自用，不计划上架应用商店。
- 目标设备为 Nubia NX809J，Android 16 / API 36；通过无线调试连接本机。尚无 root 或 Shizuku，用户有后续增强控制权限的需求。
- 测试流程由用户执行；代理负责准备待测 App 构建版本、测试文件、必要环境和简短操作说明，当前实机体验问题优先询问用户。
- APK 交付时只要 ADB 中发现已授权的手机，默认直接安装：先覆盖安装保留数据；如签名冲突，用户已授权卸载旧版后重装（会清除旧版数据）。同一手机多个 ADB 连接只安装一次；此授权不包含自动操作界面或执行实机测试。
- 默认不再自行操作手机进行测试、自动化点击或创建/删除测试数据；用户另行明确要求代测时再执行。构建与静态检查可作为交付准备。
- 当前第一阶段优先完善聊天与 harness 稳定性；设备控制、Shizuku 和 root 接入后续推进。

## 构建与验证

- Windows 构建：`.\gradlew.bat assembleDebug`
- JVM 单元测试：`.\gradlew.bat testDebugUnitTest`
- 静态检查：`.\gradlew.bat lintDebug`
- 设备测试：`.\gradlew.bat connectedDebugAndroidTest`，需要已连接的设备或模拟器。
- 同一手机可能被无线 ADB 发现为多个连接；设备测试前检查 `adb devices -l`，必要时以进程级 `ANDROID_SERIAL` 指定一个连接，避免重复启动 instrumentation。
- Android SDK 路径由本机 `local.properties` 配置；不要提交本机路径。
- 当前配置：Gradle 8.13、AGP 8.11.1、Kotlin 2.0.21；compileSdk/targetSdk 36、minSdk 24；字节码目标 Java 11。

## 架构

- 单一 `app` 模块，Kotlin + Jetpack Compose + Material 3。
- `AgentApp` 手工组装 FileStore、ProviderFactory、BackupManager 和 AgentEngine。
- `agent/`：模型调用、工具执行循环和单层子代理。
- `provider/`：OpenAI 兼容、Anthropic、Gemini、自定义模板适配；OkHttp 网络请求。
- `data/model/`：可序列化配置、会话、消息、Agent、记忆模型。
- `data/store/FileStore.kt`：应用私有目录内 JSON、Markdown、工作区文件与技能包。
- `ui/`：会话、聊天、模型配置、Agent、技能、记忆、文件与设置页面。
- `data/backup/`：ZIP 备份与恢复。完整备份包含 avatars，归档使用相对头像引用，恢复前暂存校验并自动备份原数据；缺图保留 emoji，不借用本机旧图。
- `data/backup/ConfigurationTransfer.kt`：Provider、会话、记忆 JSON 与 Agent ZIP 独立迁移；先 prepareImport 校验/预览，再按用户选择处理冲突。
- `ui/components/ListSelection.kt`：六类列表共用的管理模式、checkbox 多选、全选与批量删除确认。

## 已核实的约定与边界

- applicationId 为 `com.Ling.actant`，namespace 和源码包为 `com.example.myapplication`；两者不同，不要顺手统一。
- 工具定义集中于 `Tools`，执行入口为 `ToolExecutor`；AgentProfile 的空工具列表表示全部工具可用。
- 子代理禁止再次委派；模型选择优先级为设置页强制配置、工具参数、继承主代理。
- 工作区文件操作须保留 FileStore 的路径边界校验。
- NEW_CHAT 是主界面，SavedStateHandle 保存当前草稿/历史会话选择；首发通过 FileStore.commitDraft 保存会话和首条消息后原地显示聊天，不导航到 CHAT，不创建空历史。抽屉“新对话”替换为新草稿，历史选择在主界面打开；CHAT 保留给子会话详情。
- 新会话配置用 new-chat-defaults.json 保存 Agent/模型/具体思考档位/权限模式/工作目录/目录范围；草稿调整立即记忆，历史调整不影响默认，正文附件不继承。首次思考 medium，无该档则取支持列表中位档；Provider 不再配置默认思考强度。
- Conversation.workingDirectory 决定相对文件路径和空命令 cwd，null 使用 App workspace；文件工具范围是工作目录与 allowedDirectories 额外目录的并集，额外目录留空仅访问工作目录。Shell 不受该目录范围约束，继续按权限模式审批。保留 canonical 路径边界、附件工作区引用和 Plan 专用文件规则。
- ChatSessions 在 Activity 的 ViewModel 内持有 ChatViewModel；首发和从历史重开复用同一执行任务。Activity 最终销毁会取消任务，尚无独立后台任务服务。
- 会话面板使用 `session/{conversationId}` 独立页面，复用 ChatSessions；前台路由独占审批和错误提示。产物来自成功写入/编辑的保存快照，查看历史 Diff 与打开当前文件分离。
- 草稿/输入/附件引用通过 SavedStateHandle 恢复；旋转、外部选择器和后台返回不重置导航。工具详情只使用对应调用记录与已保存快照，不重读文件冒充历史。
- 会话顶栏标题在上、Agent 名称在下；模型选择仅保留在输入框。反馈横幅悬浮于顶栏下方，不占正文高度（用户修订优先于原型旧占位要求）。
- 取消必须贯穿网络读取、工具和子代理；不得将 CancellationException 转为普通工具错误。
- 导航列表在 STARTED 阶段刷新，避免等待入场动画结束后才加载；圆角卡片使用 Card 自身的 onClick 处理 ripple。
- 当前没有无障碍服务、截图、通知监听、Shizuku 或 root 执行实现。
- 代码使用四空格缩进，添加必要注释，避免提前抽象。
- 手动深浅色覆盖须同步 Activity 的 SystemBarStyle；不能让状态栏/导航栏图标继续只跟随系统主题。
- 思考档位详情点选后立即应用并回到滑块，不设置返回滑块底部按钮；弹层系统栏也跟随 App 手动主题。
- 模型供应商编辑页的模型列表默认展开；模型子页的“完成”只返回并保留草稿，统一由供应商页“保存”持久化。
- 供应商持久化的 model 只用于测试连接，不作为会话、Agent、子代理或模型目录的兜底。供应商列表显示名称、协议与模型数量，无默认 radio/标签/模型名/协议字母头像；聊天默认来自新会话配置快照，首次或快照模型已不可用时保持未选择并禁用发送。模型目录可接口获取或独立手动添加。
- Markdown 保留 CommonMark；流式展示先缓冲 200ms，耗尽后新到文本重新缓冲，按输入速率以约 90–140ms 分批发布（解析较慢时最多 180ms），新增原生文字以 220ms 淡入。正常结束在 200ms 内排空，取消/错误立即同步；历史、列表重入与前台恢复不重播旧缓存。淡入仅在绘制阶段遮罩新增文字，不逐帧解析 Markdown。公式与 Mermaid 使用内置离线资源，WebView 禁止网络与本地文件访问，普通正文继续原生渲染。实机阅读体验及性能仍需用户验收，见 `.Codex/HANDOFF.md`。
- 后续代码改动同步维护 `.Codex/memory/`，并检查相关目录是否存在需要更新的 `CLAUDE.md`。
- 回复操作按真实用户输入归并，整个回复结束及末尾淡入完成后才显示一组复制/编辑/删除。重新生成仅限最新回复，复用原问题和附件、保持当前权限；所有用户消息和完整回复都可分支。用户消息分支将正文/附件放输入框，历史截止上一轮模型回复；回复分支保留整组工具记录，不自动调用模型。
- 历史修改使旧环境快照失效，下次请求根据实时会话状态追加；`get_session_state` 始终只读可用，不放宽权限。Plan 完成必须保存计划文件并调用 `exit_plan_mode` 等待审批。Markdown 单 `~` / `^` 只接受整数上下标简写，文本上下标使用显式 HTML，避免聊天语气误降基线。
- 模型目录完整声明保存在 `discoveredModelMetadata`，包含规范化容量/思考等字段及 raw 原文。手动上下文覆盖优先于接口容量；明确思考 false/档位列表参与会话选择，缺失时保留原协议规则。厂商任意 schemaPath 仅展示，不自动变更协议；输入与输出能力不可互相推导。目录刷新后由供应商页“保存”持久化。

- 底部弹层统一用 AppModalBottomSheet：列表边缘滚动与长按后的内容拖动不得传给弹层；保留顶部把手关闭。Main pass 消费移动前须越过 touchSlop，否则会取消子列表起滑。恢复接口模型声明时同时清除能力覆盖、上下文覆盖和上下文输入草稿。

- 网络搜索使用 AppConfig.webSearch 中的全局当前服务（SearXNG、SerpApi、Brave、Tavily、Exa、Google Custom Search 存量接口）；Agent 仅控制 search 工具授权。服务未配置/停用时 UI 禁用 search，ToolExecutor 同时过滤声明并拒绝执行；fetch 独立可用。网络正文限制大小和时间，取消必须贯穿响应体读取，不共享模型密钥或浏览器登录状态。

- 品牌显示为 Used AI Harness / UAH，使用 design/UAH_Android_Icon_Kit_v1 原始资源；保持 applicationId、包名、存档协议和签名不变。搜索配置按服务独立保留，仅调用当前服务，不自动回退；旧 baseUrl 配置按 SearXNG 读取。
