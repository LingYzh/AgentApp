# Markdown 扩展（2026-09-23）

- `MarkdownDocument` 保留 CommonMark 0.13 与现有 GFM 表格、删除线；新增文档级脚注定义收集，在 `<details>` 前统一提取，因此正文、详情摘要与详情内容可引用同一个 `[^id]`。兼容 `[文字^id]`；未定义、转义及代码中的标记保持原文。定义允许空行后缩进续段；未引用定义显示在文末附注区域。
- 在 CommonMark 解析前只标记已定义脚注、安全的 `==高亮==`、`~下标~`、`^上标^`、`$行内公式$`、`\\(行内公式\\)` 与简单 `<sup>/<sub>/<mark>/<kbd>`。引用定义、链接目的地、围栏与缩进代码保持原文。无定义的脚注不转换。
- 图片拥有独立 `imageUrl`，Compose 用已有 Coil 显示，失败显示 alt。图片仅允许带主机的 HTTP/HTTPS URL；相对路径、`file:` 和 `content:` 均显示 alt，避免消息图片读取本机路径。裸 `http(s)`、`www` 和邮箱以链接呈现；点击仍受 UI 已有安全 scheme 检查。
- 六级标题各自使用不同字体大小；引用块底部恢复 14dp 留白。脚注点击弹出原生对话框阅读 Markdown 注释。
- `$$...$$`、`\\[...\\]` 和 `math`、`latex`、`tex` 围栏转为 `MarkdownBlock.Math`；`mermaid` 围栏保留代码模型。公式与 Mermaid 委托离线 `MarkdownRichBlock`，混合行内公式委托 `MarkdownMathText`；公式和 Mermaid 块均可切换源码并复制。
- 回归样例在 `.Codex/test-fixtures/markdown-complete-samples.md`。JVM 测试覆盖脚注范围、代码/转义/引用地址边界、私用字符、货币、图片与公式语义。测试与构建由主任务统一执行。
