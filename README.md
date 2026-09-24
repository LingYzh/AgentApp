# Used AI Harness（UAH）

UAH 是一个在 Android 手机上运行的 AI Agent 实验项目。它把模型对话、工作区文件、记忆、技能、网络搜索和经用户授权的设备操作放在同一个应用中。项目主要用于个人试验，目前不以应用商店发布为目标。

## 下载与安装

从 [GitHub Releases](https://github.com/LingYzh/AgentApp/releases) 下载最新 APK 并安装。当前的 [v1.0.0 Preview 1](https://github.com/LingYzh/AgentApp/releases/tag/v1.0.0-preview.1) 是预览版，要求 Android 7.0（API 24）及以上。

预览版是不可调试的 release 构建，但使用测试证书签名，以便覆盖此前同证书安装的测试包。它没有使用正式发布证书；以后更换证书时可能需要卸载旧版，届时请先在应用内备份数据。

## 开始使用

1. 在应用中添加模型供应商和具体模型。支持 OpenAI 兼容接口、Anthropic、Google Gemini 和自定义请求模板；所需地址、密钥及模型由用户自行配置。
2. 在新对话的输入区选择模型和权限模式，然后发送消息。模型未选择时不能发送。
3. 如需联网搜索，进入 **设置 → 搜索与设备 → 网络搜索服务**，选择并配置一个服务，测试后保存。可用服务包括 SearXNG、SerpApi、Brave、Tavily、Exa，以及面向存量账号的 Google Custom Search。`search` 依赖已启用的服务；`fetch` 可以独立读取网页。
4. 如需操作手机，进入 **设置 → 搜索与设备 → 设备控制**，打开总开关，并在系统设置中启用 UAH 的无障碍服务。保持手机亮屏、解锁后再发起任务。
5. Shizuku 是可选增强能力。设备控制页提供官方下载入口；安装并启动 Shizuku 后，在该页请求授权、连接服务。没有 Shizuku 时仍可使用无障碍操作。

设备操作还受 Agent 工具授权和当前会话权限模式限制。`Readonly` / `Plan` 只允许观察；`Accept Edit` 对写操作逐次请求批准；`Auto` 可连续执行。运行中的任务可通过应用的悬浮控件或通知停止。无障碍读取优先使用界面控件；截图需要 Android 11 及以上，受系统安全窗口限制。Shizuku 不提供任意特权 shell，也不等于 root。

## 主要能力

- 流式模型对话、工具调用、单层子代理，以及可选择的思考档位。
- 工作区文件读写、Shell 命令、记忆、技能和会话管理；具体写入操作由权限模式控制。
- 网页搜索与读取、附件输入，以及 Markdown、公式和 Mermaid 展示。
- 完整备份与配置迁移。备份可能包含模型密钥及对话内容，请妥善保存。
- 设备状态查询、界面观察、控件操作、截图和有限的 Shizuku 系统操作。设备控制默认关闭，须由用户主动启用。

这是仍在迭代的预览项目。不同厂商系统上的无障碍、后台运行和 Shizuku 行为可能不同；模型对复杂界面的操作效果也取决于其工具调用能力。涉及重要数据或不可逆操作时，请使用需要审批的权限模式并核对目标。

## 本地构建

项目为单模块 Kotlin / Jetpack Compose Android 应用，使用 Gradle Wrapper。准备 Android Studio、Android SDK 36 和可供 Android Gradle Plugin 8.11.1 使用的 JDK；首次打开时让 Android Studio 完成 Gradle 同步，并在本机生成 `local.properties`。不要提交 SDK 路径或签名密钥。

在 Windows PowerShell 中运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。模拟器或设备已连接时，可运行 `.\gradlew.bat connectedDebugAndroidTest`。`assembleRelease` 默认生成未签名 APK；可安装的 release 包还需自行配置签名，不能把 `app-release-unsigned.apk` 直接用于安装。

应用的 `applicationId` 是 `com.Ling.actant`，源码 `namespace` 是 `com.example.myapplication`；它们目前不同。项目使用 `compileSdk` / `targetSdk` 36、`minSdk` 24、Gradle 8.13、Kotlin 2.0.21。

代码入口包括 `app/src/main/java/com/example/myapplication/agent/`（Agent 与工具）、`provider/`（模型适配）、`device/`（设备控制）、`data/`（配置与存储）和 `ui/`（Compose 界面）。
