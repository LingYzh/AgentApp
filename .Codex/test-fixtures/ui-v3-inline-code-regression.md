# 行内代码底板回归样本

覆盖 UI v3 截图中的短符号代码、混排、换行邻接和列表场景。渲染时分别使用 360 dp 与 412 dp 屏幕宽度，检查浅色/深色、默认/较大系统字体，以及流式更新/完成态；查看底板是否同高、是否出现空框或覆盖邻行。选择文字并复制时，内容仍应包含原始文本，链接仍应可点击。

- 混排：中文前缀 English middle `value_42` 中文后缀，继续普通正文。
- 行尾和新行开头：这一段以 `end-token` 结束，
  下一行以 `~~` 开始，然后接中文和 English。
- 短符号：`~` `~~` `*` `**` `***` `>` `>>` `#` `######`，以及双反引号包住的单个反引号 `` ` ``。
- 任务项：`[x]` checked 与 `[ ]` unchecked；同时检查任务列表正文里的 `done_1` 和中文。
  - 嵌套列表中的 `nested()` 靠近行尾，中文换行后再以 `->` 开头。
- 链接：代码中的 `[标签](https://example.com/path?q=1)`；真实链接 [打开示例](https://example.com)；链接两侧分别放置 `before-link` 和 `after-link`。
- 连续换行：短代码 `a` 后接足够长的中文 English 文本，让后面的 `tail_token` 在窄宽度下自动换行。

```text
fenced code keeps its natural height and source text
second line: 中文 English `symbols` ~~ **
```
