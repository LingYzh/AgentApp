# AgentApp

## 项目目标

- 完善 Android 移动端 harness，并尽可能支持用户授权的设备操作。
- 用户自用，不计划上架应用商店。
- 目标设备为 Nubia NX809J，Android 16 / API 36；通过无线调试连接本机。尚无 root 或 Shizuku，用户有后续增强控制权限的需求。
- 测试流程由用户执行；代理负责准备待测 App 构建版本、测试文件、必要环境和简短操作说明，当前实机体验问题优先询问用户。
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
- 冷入口为 NEW_CHAT 草稿，历史入口保留；首次有效发送通过 FileStore.commitDraft 回执保存会话和首条消息，不创建空历史。
- ChatSessions 在 Activity 的 ViewModel 内持有 ChatViewModel；草稿到 CHAT 交接复用同一 viewModelScope。Activity 最终销毁会取消任务，尚无独立后台任务服务。
- 草稿/输入/附件引用通过 SavedStateHandle 恢复；旋转、外部选择器和后台返回不重置导航。工具详情只使用对应调用记录与已保存快照，不重读文件冒充历史。
- 取消必须贯穿网络读取、工具和子代理；不得将 CancellationException 转为普通工具错误。
- 导航列表在 STARTED 阶段刷新，避免等待入场动画结束后才加载；圆角卡片使用 Card 自身的 onClick 处理 ripple。
- 当前没有无障碍服务、截图、通知监听、Shizuku 或 root 执行实现。
- 代码使用四空格缩进，添加必要注释，避免提前抽象。
- 后续代码改动同步维护 `.Codex/memory/`，并检查相关目录是否存在需要更新的 `CLAUDE.md`。
