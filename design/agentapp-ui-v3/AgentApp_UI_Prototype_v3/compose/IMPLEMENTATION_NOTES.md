# 原生参考的使用范围

本目录的 AgentUiPalette.kt 是颜色参考，未在本容器编译为 Android 产物。以应用现有 Theme.kt / ExpressiveTokens 为接入点，不要求新建第二套 Theme，也不要直接覆盖文件名相同的现有定义。

## 无外框的工具行

```kotlin
// 示意结构，不是可直接覆盖的业务实现。
Column(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(toolIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        // 动作 / 可缩略文件名 / 真实 +/- 统计 / 可旋转箭头。
        // 不在此输出 command、原始 JSON 或大段结果摘要。
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        // Shell / Diff 全宽，与上方正文边界对齐；内容区域内可滚动。
        // 只有这里允许代码底色；外层不套 Card/Surface/padding。
    }
}
```

生产代码补全：稳定语义key、rememberSaveable状态、展开状态无障碍语义、系统动画设置、真实模型状态和copy/wrap回调。不要用示意snippet替换权限、文件边界或取消逻辑。

不要同时给 AnimatedVisibility 和外层 animateContentSize 配置相互竞争的高度动画。记录删除/移位时与列表 item 动画协调；大结果只在展开时按需计算/渲染。

## 图标与文字

文本+箭头放进同一 Row，以 CenterVertically 对齐。Text不含Unicode箭头代替图标；Icon固定dp大小，不对齐文本基线。按钮原始48dp触控范围保留，文件名采用weight(fill=false)或明确最大宽度保证统计/箭头可见。

## Diff来源

调用既有 FileChanges.diff(change)，映射每个DiffLine的type、oldLineNumber、newLineNumber、text。ADDED使用newLineNumber，REMOVED使用oldLineNumber，CONTEXT保留合适的上下文行号。可见行号不参与原文复制。previewOmitted和fallback保留并醒目标注，不能将网页fixture的简化LCS搬入项目。

## 新草稿的任务归属

不要把首发任务放在即将被导航移除的NEW_CHAT页scope中。草稿提交与真实会话的运行ViewModel应有明确的一次性交接协议：保存得到真实ID后由对应会话拥有执行，消费首发payload有幂等标记。既不能因跳转取消，也不能在源/目标ViewModel各发一次。具体方案依当前ChatViewModel及store实现选择，不能为了UI增加后台常驻服务。
