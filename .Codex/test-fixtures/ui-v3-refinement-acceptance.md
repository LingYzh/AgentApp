# 第二轮验收与交付

## 构建结果

- `testDebugUnitTest assembleDebug lintDebug`：成功；243 passed / 3 skipped / 0 failed。
- lint：0 errors / 39 warnings / 3 hints；数量未增加。
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- SHA256：`CD854FE7167C5EFF6633F1FC50F876BA68B68764CE01CCD8B177D4776F243524`
- NX809J 已覆盖安装；versionName=1.0，lastUpdateTime=2026-09-23 16:10:09。
- 手机样例：`/sdcard/Download/AgentApp-Markdown-Samples.md`；SHA256 `A4197135B800A63C059C8EF0B7FF5398FB13F5F44391475B0B1E22E991649C5F`。

## 已观察的模拟器结果

- 360dp 暗色：Agents 卡片、供应商模型子页、Markdown 六级标题与引用、离线 KaTeX 公式和 Mermaid 流程图已截图并查看。
- 思考详情六个档位可见；点击“低”立即回到滑块，显示 `low`，没有返回按钮。弹层状态栏图标正确。
- 本地 LLM 配置经真实 App UI 保存，聊天收到模型回复；含 Key 的可导入备份保存在 `.Codex/local/`，不提交。
- 原型参考服务：`http://127.0.0.1:8765/index.html`；26 页原型对照见前轮 `ui-v3-completion-acceptance.md`。
- 截图与日志：`.Codex/verification/ui-v3-refinement/`。早期 before/after 截图仅记录排错过程，以 final 命名及 math-native/mermaid-native 截图为准。

## 手机最短步骤

1. 打开任一会话的思考滑块 → 全部档位 → 点不同档位；应立即返回滑块，底部操作不被截断。
2. 工作区文件 → 上传文件 → Download/AgentApp-Markdown-Samples.md；阅读和源码之间切换，检查六级标题、引用、脚注点击、公式、Mermaid、复制及折叠。
3. 模型供应商设置 → 编辑 → 模型列表 → 模型设置；修改后“完成”返回，父页保存后重新打开确认。
4. 对照原型查看 Agents、历史、文件、Skills、记忆和供应商列表及管理菜单。

## 验收边界

- 模拟器 API 37，手机 API 36。手机视觉、IME、TalkBack、减少动效、快速反向操作、生命周期恢复和浅深色/大字体全组合仍待用户验收。
- Markdown 支持 CommonMark/GFM 及已列扩展；原始 HTML 仅安全子集，脚注正文中的跨脚注引用暂不解析，不承诺所有平台私有语法。
- 没有提交、推送或发布。
