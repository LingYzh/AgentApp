# UI v3 第二轮修正

- 六级 Markdown 标题使用不同字号，增加引用底部留白；扩展脚注、兼容文字脚注、上下标、高亮、安全行内 HTML、公式和 Mermaid。保留 CommonMark、原生正文、120ms 采样。修复转义标记及跨空白的上标误匹配。
- KaTeX 与 Mermaid 随 APK 离线打包；公式/图表可切换源码并复制。WebView 不开放网络、文件访问或 JS bridge；图片仅支持 HTTP/HTTPS，失败显示 alt。并非所有平台私有 Markdown 方言均支持。
- 思考滑块填充裁剪到滑块圆心；使用受窗口边界约束的底部面板。按用户最后修订，详情点选立即应用并返回滑块，删除返回按钮。弹层系统栏跟随 App 主题。
- Agents 使用头像标题、模型标签、描述及底部操作卡片；会话、文件、Skills、记忆使用紧凑分隔行；Provider 使用默认 radio 与供应商卡片。文件描述过滤 null。
- 导航改称“模型供应商设置”；模型列表可折叠，子页配置能力与上下文。“完成”回到供应商草稿，父页统一保存。
- 模拟器已通过真实 UI 保存本地服务配置并收到模型响应；可导入的含 Key 配置备份位于被 Git 忽略的 `.Codex/local/emulator-providers-with-keys.json`，用 `.Codex/tools/Backup-EmulatorProviders.ps1` 更新。
- 已实际查看模拟器截图，核对 360dp 暗色下列表、模型子页、思考弹层和原生文件预览中的公式、Mermaid。选择“低”后回到滑块并显示 low。模拟器为 API 37，不代表 API 36 手机验收。
- 最终测试 246 项：243 通过、3 跳过、0 失败；assembleDebug/lintDebug 成功，39 warnings/3 hints 与基线一致；git diff --check 通过。
- 最终 APK 已覆盖安装到 NX809J（保留数据）及模拟器。手机 Download/AgentApp-Markdown-Samples.md 已推送并核对 SHA256；未操作手机界面。尚未提交或推送。
