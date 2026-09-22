# UI v3 第三轮：Markdown、目录语义与视觉收敛

## 用户已确认

- 工作目录与额外目录同级。文件工具只访问工作目录与额外目录的并集；额外目录为空仅工作目录。
- Shell 不受文件目录范围约束，从工作目录启动并按权限模式审批；移除“设置额外目录就禁用 Shell”的旧行为。
- 原型通过 http://127.0.0.1:8765/index.html 可在内置浏览器交互；不要继续报告“本地原型整体不可访问”。
- 用户负责手机交互验收。此前授权 ADB 安装继续有效，优先 install -r 保留数据。

## 实现

- 原生 Markdown 新增 details/summary、open、嵌套与流式未闭合恢复；仅标题折叠，嵌套状态保存。代码内标签保持原文；限制递归深度。
- 后台轻量代码 lexer 支持 Kotlin/Java/JS/TS/Python/Shell/JSON/YAML/HTML/CSS/SQL；未知语言纯文本。语法配色按原型，复制与横向滚动不改变原文。
- Markdown 引用、代码背景/头部与间距对照原型；统一代码/Diff 暖灰背景。
- 思考滑块端点内缩17dp，圆点4dp、白色thumb34dp；Canvas视觉与Material交互分离，避免轨道边缘裁切。
- 图形目录读取与保存移到 IO；读取进度、应用中状态、重复操作锁和面板内错误。配置保存成功后才发布会话状态。
- 管理列表统一说明、搜索、页内新增入口；历史分隔行与今天/昨天/更早日期分组；保留现有导入/导出、批量管理与Provider单选语义。
- 聊天标题打开模型选择；附件横向列表；顶部反馈自定义边框、关闭、错误图标；更新排版令牌与抽屉按钮主题色。
- 工具状态使用会话/消息/调用组合key，折叠内容保留局部Saveable状态。

## 验证

- `testDebugUnitTest assembleDebug lintDebug` 已通过，225 项测试：222 通过、3 跳过、0 失败。后续仅调整 Modifier 参数顺序与快捷目录的纯路径获取，再次 assemble/lint。
- 修正新增 Markdown 围栏/递归/长单行边界，并将两个旧元数据测试改为显式授权其目录后独立验证 Accept Edit 保护。
- 使用 JBR 21 (`C:\Users\AnnaC\.jdks\jbr-21.0.11`)；默认 Java 26 与项目构建不兼容。
- 首次覆盖安装成功，数据保留；最后微调版本安装信息在下方追加。
- 测试样本见 `.Codex/test-fixtures/ui-v3-markdown-and-directories.md`。
不以原型浏览器截图或 JVM 通过冒充真机像素与触控验收。

最终 `assembleDebug lintDebug` 通过；lint 0 errors、39 warnings、4 hints，与迭代前告警数一致。
无线 ADB `install -r` 成功，设备 `com.Ling.actant` 的 lastUpdateTime 为 `2026-09-22 21:59:11`，未清数据或操作手机界面。
APK：`app/build/outputs/apk/debug/app-debug.apk`（23,243,002 bytes）。
SHA256：`4918BBC2E69433FD603568A9AC9259A8EA9654A30A139F4E9B23393BA4987CA6`。
