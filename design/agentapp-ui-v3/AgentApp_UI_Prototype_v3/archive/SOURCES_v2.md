> 历史研究记录。v3当前核查范围见根SOURCES.md。

# 参考来源与可信边界

以下 S1–S5 为首版设计参考记录；v2本轮重新核验的交互与实现来源见文末。未声称再次登录竞品实测所有灰度界面。

研究日期：2026-09-22。以下是本方案采用的来源，不是未来版本承诺。当前产品可能分地区、账号与版本滚动更新；本交付没有在用户手机上逐个安装并截图“最新 APK”，因此不是三个官方客户端的像素级复刻。

## S1 · OpenAI：ChatGPT Release Notes

官方发布记录，读取到 2026-09-21；与 Android 布局直接相关的 2026-08-21 条目说明侧栏显示最多8个近期会话、生成图片全宽呈现，并写明版本1.2026.216或更高。

采用：近期会话放进侧栏、对话内容优先。没有在 AgentApp 中添加其插件、财务、语音或图片生成能力。

```text
https://help.openai.com/en/articles/6825453-chatgpt-release-notes
```

## S2 · Anthropic：Claude Cowork and chat are now one Claude

官方产品公告，2026-09-16。统一聊天与更大任务的入口，将工作产物放在会话中；明确 Pro/Max 在随后几周逐步推出到 web、desktop 和 mobile。

采用：普通对话和工具任务共用入口，产物清楚可达。没有复制后台云任务、Docs/Slides编辑、远程电脑或自动调度；仓库不具备这些能力。

```text
https://claude.com/blog/cowork-is-now-claude/
```

## S3 · Google：The next evolution of the Gemini app

Google I/O 2026 官方说明。Neural Expressive 包含新排版、动画、色彩和触觉反馈，面向 web/Android/iOS 推出，强调富内容回答而非大段文字堆积。

采用：状态变化有轻反馈，结果以有层次的内容呈现。没有沿用 Google 配色，也没有加入 Live、Omni、Spark 或24小时后台执行。

```text
https://blog.google/innovation-and-ai/products/gemini-app/next-evolution-gemini-app/
```

## S4 · Anthropic 官方 Skills 仓库的品牌指南

明确列出 Dark #141413、Light #faf9f5、Gray #b0aea5/#e8e6dc，以及 Orange #d97757 等品牌色。

采用：暖白与陶土种子。本交付其余颜色为自主设计/可读性适配。这个文件是品牌规范，不是 Claude Android 私有主题源码。没有复制/交付字体文件或官方商标图形。

```text
https://github.com/anthropics/skills/blob/main/skills/brand-guidelines/SKILL.md
```

## S5 · Android 官方 Compose 文档

原生落地应按实际依赖版本读取，重点是触控与辅助功能默认行为、系统 Insets 和 IME。

```text
https://developer.android.com/develop/ui/compose/accessibility/api-defaults
https://developer.android.com/develop/ui/compose/system/insets-ui
```

## R · 当前项目源码，优先级高于竞品印象

仓库：LingYzh/AgentApp；默认分支 master；基线提交：

```text
2c947234ab0d29ede6e999193717288363195b7a
https://github.com/LingYzh/AgentApp
```

通过 GitHub 连接器读取/核对的关键入口：

- `AGENTS.md`：平台、构建、架构、权限与测试边界。
- `MainActivity.kt`：真实路由、全局导航、主题入口。
- `data/model/Models.kt`：Provider、Conversation、Agent、TokenUsage、权限、能力与推理语义。
- `agent/Tools.kt`：14个工具及单层子代理限制。
- `ui/chat/ChatScreen.kt`：ChatViewModel关键状态/回调及当前输入区、工具展示、会话侧栏。
- `ui/settings/SettingsScreen.kt`：主题、循环次数、子代理覆盖、精确命令、存储和备份。
- `ui/skills/SkillsScreen.kt`：Skill ZIP导入、单项/全部/所选导出、分享入口。
- `ui/providers/ProvidersScreen.kt`：全局默认select、保存、删除、连通性测试和模型目录拉取。
- `.Codex/memory/2026-09-21-configuration-transfer.md`：迁移格式、Key/headers边界、冲突预览。
- `.Codex/memory/2026-09-22-chat-context-ui.md`：上下文、推理、压缩与用量。
- `.Codex/memory/2026-09-22-context-and-message-actions.md`：消息管理、附件、Diff与不可撤销副作用。
- `.Codex/memory/2026-09-22-permission-reference.md`：项目已实现的权限取舍；其中引用的第三方仓库不是本设计的官方事实依据。

未宣称对整个仓库进行完整源码审计；功能和约束核对集中在上述 UI/模型/工具及项目记录。原生重构前仍应重新读取当前 HEAD。


## v2 · 本轮重新核验（2026-09-22）

- Android Animation quick guide: https://developer.android.com/develop/ui/compose/animation/quick-guide （AnimatedVisibility、animateContentSize、进入/退出机制；本原型时长为设计取值。）
- Android Slider: https://developer.android.com/develop/ui/compose/components/slider （离散档位、steps、onValueChangeFinished。）
- W3C Slider Pattern: https://www.w3.org/WAI/ARIA/apg/patterns/slider/ （键盘、aria-valuetext、可达性。）
- W3C prefers-reduced-motion: https://www.w3.org/WAI/WCAG22/Techniques/css/C39 （减少非必要动效。）
- Repository master was re-read and is still commit 2c947234ab0d29ede6e999193717288363195b7a.
- ContextUsageUi.kt：直接核对八类色、denominator、未知容量、模型思考菜单。
- MarkdownContent.kt：直接核对原生块渲染、120ms流式采样、代码复制与换行、横向表格、链接scheme。
- AnthropicProvider.kt：直接核对 adaptive 的 output_config.effort 与关闭 thinking.type=disabled。

仓库路径前缀：https://github.com/LingYzh/AgentApp/blob/2c947234ab0d29ede6e999193717288363195b7a/app/src/main/java/com/example/myapplication/

界面中的支持模型、能力勾选、callId、时间、结果和token数均为展示fixture，不代表真实请求/设备的状态。
