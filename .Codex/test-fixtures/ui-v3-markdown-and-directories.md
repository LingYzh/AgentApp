# 第三轮 UI 验收

此文件可在 App 的 Markdown 文件预览中查看；无需发送给模型即可检查排版。

## Markdown 与代码

普通段落、**重点**、*补充*、~~旧方案~~、行内 `reasoning_effort`。

> 引用应有暖灰底色、左侧陶土色细线，与正文保持清晰间距。

> 左侧上下角保持直角，只有右侧有圆角。

行内代码示例：`reasoning_effort`、紧挨中文的`hello()`以及跨行长代码`very_long_identifier_with_arguments_and_more_content_for_wrapping`。边框与圆角应清晰，复制不应多出装饰空格。

- [x] 已完成：绿色圆角方框与勾号
- [ ] 未完成：空心圆角方框

<details open>
<summary>默认展开，支持 **标题强调**</summary>

点标题收起；点正文不应收起。把内层打开，再收起与重开外层，内层应保持打开。

<details>
<summary>内层详情</summary>

```kotlin
// 注释、关键字、函数、类型、字符串和数字应有区别
fun greet(name: String): String {
    val count = 42
    return "Hello, $name ($count)"
}
```

</details>
</details>

```python
def greet(name):
    # 一个简单函数
    return "Hello " + name
```

```javascript
async function loadData() {
    const result = await fetch("/api/items");
    return result.json();
}
```

```json
{"model":"demo","reasoning_effort":"medium","enabled":true,"count":42}
```

```sql
SELECT name, count FROM items WHERE count > 10;
```

```html
<details><summary>代码中的标签不能变成折叠控件</summary>原文</details>
```

```unrecognized-language
unknown_code("保持纯文本与原文复制");
```

| 项目 | 验收 |
| --- | --- |
| 复制与换行 | 复制仍是原始代码，长行仅在代码区滚动 |
| 深浅主题 | 代码高亮、引用与表格均清晰可读 |

## 最短实机步骤（用户执行）

1. 思考滑块切到首尾档：端点圆点完整，白色滑块始终可见；详细面板底部按钮可点。
2. 将工作目录设为 A，不添加额外目录：相对文件读写只在 A 内；添加 B 后，B 中绝对路径可访问，不必把 A 再加一次。
3. Accept Edit 下有额外目录时发起 Shell：仍出现审批，默认 cwd 为 A。Readonly/Plan 仍禁止 Shell。
4. 目录多层进入、返回、选择与应用：读取/保存中有进度提示，不能重复应用，失败在面板内显示。
5. 逐页检查历史、Agents、模型配置、文件、Skills、记忆的搜索、空结果、管理多选和原有导入导出；搜索不清空已选项。
6. 标题处不再打开模型选择。上方会话标题、下方Agent，旁边编辑图标可在空闲时改名，重新进入历史仍显示新标题；输入框的模型入口保留。
工具记录对照原型26：命令与输出处于一个Shell底板，Diff为单行号；展开/收起、换行、复制与高度按钮保持可用。

实机 UI、字体缩放、TalkBack、IME 和手势返回仍由用户验证；JVM 测试不代表这些检查已通过。
