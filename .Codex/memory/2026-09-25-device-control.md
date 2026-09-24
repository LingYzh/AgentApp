# 本机设备控制第一版

## 追加交付：设置入口与 Shizuku 安装

- 用户要求补装 Shizuku、增加下载入口，并将网络搜索服务与设备控制整合进设置页。实机 NX809J 已安装 Shizuku 13.6.0.r1086.2650830c，安装 Success；仅安装，启动及授权仍由用户执行。
- 两入口移至设置主页“搜索与设备”组，从抽屉和 TopLevelRoutes 移除。子页返回箭头使用 safePopBackStack，系统返回也回到设置。搜索的独立配置/保存行为不变；移除设置中未接入控制能力的过时文案。
- 设备控制页新增“下载 Shizuku”，通过系统 ACTION_VIEW 打开 https://shizuku.rikka.app/zh-hans/download/ ，异常使用现有错误提示处理。
- Herdr agy 保持默认模型；其 Edit 遭混合换行匹配失败，root 接管完成改动。未改后端或签名，没有相关 CLAUDE.md。
- assembleDebug、lintDebug 成功（0 errors、60 warnings、5 hints）。模拟器验证抽屉移除、设置两入口、两个子页返回、系统返回及下载 Intent；浏览器首次运行停在 Chrome 欢迎页，日志确认已收到官方网址，未替用户登录或改变浏览器设置。截图 build/device-control-qa/settings-integrated.png 已视觉检查。
- 新版 UAH 实机覆盖安装 Success，保留本次已有数据，未操作实机界面。APK SHA256 `B889A7E41373E4F4CE5504291C86428F5B61B8FD04051471B8FEBD54E6EAC9DA`。本追加仅导航/下载入口变更，未重复后端整套测试。
- 下方第一版的“抽屉进入设备控制”“未向手机安装 Shizuku”等为历史，当前入口为“设置 → 设备控制”，实机 Shizuku 已安装。

- 用户选择无障碍 + Shizuku、本机运行；先自动测试 Android Studio 模拟器，最终手机仅安装由用户验收。Herdr 复用 agy-cli，按用户指定保持 Gemini 3.8 Flash High 默认设置。root 完成集成、取消与边界修复及验收。
- 五个工具 device_status / device_observe / device_action / device_screenshot / device_system 接入 Tools、ToolExecutor、AgentEngine、子代理。默认关闭；三层检查全局开关、Agent 工具白名单、会话权限。Plan/Readonly 仅观察；Accept Edit 每次写操作审批；Auto 连续操作。
- 无障碍有界遍历：最多访问 1000 节点、深度 30、输出 150 节点、JSON 20k；点击/输入/滚动优先原生节点，手势后备；截图最长边 1600，走现有媒体附件管线。快照绑定服务会话、窗口、包、版本、方向，旧状态拒绝操作；返回 accepted 不冒称业务成功。
- Shizuku API 13.1.5，持久 UserService/Binder，仅应用列表、force_stop、固定设置页和固定系统按键；限制包名、保护自身与基础系统包。取消使用 requestId，进程输出有界；无法确认结束时隔离后续设备操作。API24 的 Process 兼容路径已修复。
- Application ChatSessionPool + DeviceTaskService 保活发送时启用设备控制的任务；通知/无障碍覆盖层可停止，覆盖层可审批。进程死亡不自动恢复执行。普通会话仍按旧 Activity 生命周期取消。跨会话屏幕互斥，父子会话共享租约。

## 验证

- assembleDebug、assembleDebugAndroidTest、65 项针对性 JVM 测试通过；lint 0 errors、59 warnings、5 hints。全套另运行 332 项，331 通过，FileDeletionTest 的 Windows 符号链接删除测试失败；属于既有环境问题，本轮未修复，不宣称全套通过。
- emulator-5554 / Pixel_10_Pro_XL / API37：四项 instrumentation 全通过，证据 build/device-control-qa/instrumentation-all.txt（忽略目录）。覆盖中文输入、节点/坐标点击、滚动、截图、过期快照、外部页面变化、禁用、真实 Shizuku UID2000/应用列表/保护包、悬浮审批与停止，以及真实 AgentEngine 工具循环中销毁 Activity owner 后继续运行并复用原 ChatViewModel。模型供应商在该生命周期用例中为可控测试替身，不是真实外部模型。
- Shizuku 官方13.6.0仅安装并启动于模拟器。截图 final-device-page.png 已视觉检查。QA 辅助脚本锁定 emulator-5554 并校验模拟器硬件，测试 Activity 仅 debug、非导出。
- NX809J 912606610730 安装时签名冲突，按既有授权卸载旧版后重装 Success，旧版数据已清除；未操作手机 UI。未向手机安装/启动 Shizuku。构建 APK 位于 app/build/outputs/apk/debug/app-debug.apk。

## 用户验收

1. 配置模型后，抽屉进入“设备控制”，打开允许开关及系统无障碍服务；如系统限制安装来源，在应用信息允许受限设置。
2. 可先只用无障碍。需要增强能力时安装启动 Shizuku，再在设备控制页请求授权、连接服务；允许任务通知。
3. 亮屏解锁，选支持工具调用的模型，用 Auto 试“打开系统设置，读取当前页面并返回”；Accept Edit 验证目标应用上的悬浮审批，再检查停止按钮。
4. 截图理解需要支持图片输入的模型。WebView/游戏可能缺少节点，需截图与坐标；安全窗口可能不能截图。

## 当前限制

- 首版以节点优先减少截图/视觉模型往返；没有量化真机速度承诺。动作后约300ms再观察，尚无批量工作流、完整事件驱动等待、root 和通知监听。
- 真机厂商后台限制、Android16 行为及实际模型自主操作能力待用户验收；模拟器是 API37。
- 最终构建未提交；保留用户已有 .idea 修改和 jdk21.zip。
