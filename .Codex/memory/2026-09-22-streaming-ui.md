# 流式聊天界面稳定性

- 发送后由 `LocalFocusManager.clearFocus()` 收起软键盘，并显式恢复消息列表的“跟随最新”状态。
- 列表由实际最后一行的尺寸和 viewport 变化驱动跟随。拖动状态与 `NestedScrollSource.UserInput` 区分用户手势/滚轮和程序滚动；用户上滑会关闭跟随，手动回到底部会重新开启。Markdown 节流后的异步高度变化也会补滚到底部。
- `MarkdownContent` 保持同一个 `AndroidView`，流式期间按 80 ms 固定间隔取最新文本交给 Markwon。不能使用 debounce，因为持续 token 会一直推迟显示；流结束时直接渲染完整最终文本。
- Markwon 仍需在每次实际渲染时重新解析 Markdown，但频率从每个 token 降到最多约 12.5 次/秒，减少 span、文字布局和列表高度反复替换带来的闪烁。
