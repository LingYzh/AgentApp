# AgentApp 交接 · 2026-09-23

## 当前停点

用户要求先留交接并提交项目。下一轮重点是流式输出平滑性；用户确认同时存在文字成批出现和跟随滚动顿挫。当前代码包含优化初版，但不能宣称问题已解决。最新提出的缓冲播放方案仅完成可行性研判，尚未实现。

## 本轮项目变更

- 会话面板使用独立路由并复用 ChatSessions，展示计划、子代理及保存快照产物；头像支持图片失败回退。
- 历史、Agents、工作区、Skills、记忆、供应商列表统一操作菜单、管理态及原型列表行；补齐导入导出入口。
- Markdown 修复行内代码几何与标题/引用间距，增加脚注、安全 HTML 子集、上下标、高亮及离线 KaTeX/Mermaid。保留原生普通正文和 CommonMark；不承诺所有平台私有语法。
- 思考滑块修正填充，详情点选立即应用并返回滑块，恢复过渡动画，移除返回按钮。
- 模型供应商编辑页有可折叠模型目录与模型子页；子页完成只保存编辑草稿，父页统一持久化。
- 模型能力解析支持多个服务的输入模态/能力字段，显式 false 优先；资料见 `test-fixtures/model-capability-sources.md`。
- 供应商 model 字段仅用于测试连接；列表显示名称、协议、模型数量，无默认标识或协议字母头像。会话、Agent、子代理不再使用测试模型兜底；新会话快照不可用时保持未选择。
- 新增针对性单测、完整 Markdown 样例、模拟器辅助脚本、验收记录及离线资源许可证。IDE 配置为既有暂存文件，一并保留提交。

## 流式初版已改内容

- `StreamingTextPacer.kt`：只对展示文本追赶，完成/取消/内容替换立即同步完整原文，大于 4096 字符积压直接同步；避免拆开 UTF-16 代理对及组合附加字符，未覆盖所有 ZWJ 字素簇。
- `MarkdownContent.kt`：后台解析、自适应 32–120ms 节奏、复用相等块对象，保留旧内容直到新解析完成。仍是全文解析，不能等同增量解析。
- `ChatScreen.kt`：底部跟随改为 100ms 线性滚动；用户手势打断滚动时保留监听，生命周期取消继续传播。
- 新增四项单测覆盖追赶、结束/替换/大块同步、Unicode 和引用定义回溯更新。

## 待实现与待验证

1. 用户提议：首段到达先缓冲一小会儿，再稳定释放字符；上游暂停时排空已收文本；耗尽后恢复接收则重新缓冲。研判可行。150–250ms 是建议起点，并非已确认的固定参数。
2. 显示速度根据输入速率及积压平缓调整，限制额外延迟；正常结束快速平滑收尾，取消应立即停止动画并显示已收到文本。当前初版没有该状态机。
3. 不要每帧重解析整篇 Markdown；先定位全文解析、代码高亮、行内代码几何、公式/图表 WebView 重载热点。引用定义可能回溯影响前文，不能直接按字符块独立解析。
4. 验证手势上翻停止跟随、返回底部恢复、最终内容/复制一致、取消及生命周期恢复；测试长文、代码、公式、脚注、突发块和上游停顿。
5. 当前初版提高解析频率，可能增加 CPU；没有可靠对照证据前不得宣称性能改善。模拟器单次 gfxinfo 也不能代表真机体验。

## 已有验证和环境

- 本次 `testDebugUnitTest assembleDebug lintDebug` 成功：257 项测试，254 通过、3 跳过、0 失败/错误；lint 0 errors、39 warnings、3 hints，与原基线一致。`git diff --check` 通过。
- 最新 APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA256 `B434D7DA4E53B2818F751BC2A2B68251E2497866B7CFC3A0725B969959F713EC`。已覆盖安装 NX809J（912606610730），安装返回 Success；未操作手机测试。
- 模拟器 emulator-5554，API 37，360dp；手机 API 36。手机 UI 验收由用户执行，自动安装授权仍有效。
- `.Codex/tools/streaming-fixture-server.py` 在 loopback 5582 提供无密钥固定 SSE：每 120ms 输出 48 字符，20 段 Markdown。用 `adb -s emulator-5554 reverse tcp:5582 tcp:5582` 接入。
- 模拟器经 UI 创建独立 Stream-QA 供应商，地址 `http://127.0.0.1:5582/v1`，模型 `stream-fixture`。当前新会话配置仍可能选中该模型。后续恢复 Local-UI-QA 的 gpt-5.6-luna，再移除临时供应商；不要误把临时供应商导出为日常备份。
- 基线 gfxinfo：584 帧，66 janky（11.30%），p50/p90/p95/p99 为 22/40/46/65ms；初版后 335 帧、137 janky（40.90%），34/101/150/400ms。初版存在明显退化信号，下一轮优先复核并修正，不得视为性能改善。两次包含发送与布局开销，后一次采集较晚且期间有构建，未完成可比性核验及截图验收。原始数据在 verification/ui-v3-refinement/stream-*-gfx.txt。
- 本地真实模型服务 5580；含 Key 的导入备份仅在被忽略的 `.Codex/local/`。`Backup-EmulatorProviders.ps1` 可重新导出，禁止把密钥写入交接或提交。
- 原型服务 `http://127.0.0.1:8765/index.html`。下一轮先检查服务是否仍运行；8766 仓库根服务已停止，不要暴露本地凭据目录。
- 详细已验收/待实机项见 `test-fixtures/ui-v3-completion-acceptance.md` 与 `test-fixtures/ui-v3-refinement-acceptance.md`；其中旧 APK/测试计数是各批历史记录。

## 参考资料

- Vercel smoothStream：<https://ai-sdk.dev/docs/reference/ai-sdk-core/smooth-stream>，提供分块缓冲/定时释放思路，不是本项目实现或主流商业 App 内部机制的证据。
- Compose 阶段和状态读取：<https://developer.android.com/develop/ui/compose/phases>。

交接时不继续扩展功能，不推送或发布。
