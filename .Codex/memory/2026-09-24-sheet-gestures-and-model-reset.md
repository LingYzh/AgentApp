# 接口恢复与弹层手势修复 · 2026-09-24

## 改动

- 模型设置“恢复接口能力与上下文长度”同时移除当前模型的 capabilityOverrides、contextWindowOverrides、contextWindowDrafts。仅修改上下文或输入非法草稿时也显示恢复入口；不把上下文覆盖误标为输入能力覆盖。供应商页保存后持久化。
- 所有现有 ModalBottomSheet 入口统一使用 AppModalBottomSheet，默认完全展开。内容区消费列表边缘剩余滚动与 fling，防止 nested scroll 带动弹层。
- 长按列表项后可能绕过列表滚动而触发父层 draggable，因此内容区在 Main pass（子节点先处理）超过 touchSlop 后消费剩余移动。不能消费阈值前移动，否则会取消列表起滑。down/up 保留，顶部把手位于内容边界之外，仍可拖动关闭。
- 覆盖模型、Agent、权限/目录、思考、上下文、设置子代理、记忆编辑、导入导出和列表操作入口。沿用原保存期间关闭限制、主题与显式关闭行为。未升级 Material3 1.3.1。

## 验证

- assembleDebug、lintDebug 通过。
- emulator-5554：模型长列表普通上下滚动、顶部反复下拉，标题位置一致；设置子代理列表顶部下拉不关闭。
- 最终版本模型列表按住 700ms / 1300ms 再下拉，弹层不移动；随后普通滚动及点击选模型通过。把手拖动与关闭按钮另行验证。
- Catalog QA / auto 仅上下文覆盖 12345，以及输入能力与上下文 54321 同时覆盖，两种恢复通过；保存后配置不存在 auto 的两种手动覆盖，接口 contextWindow 仍为 1000000。
- UI XML / 截图在忽略目录 build/reply-fixes-20260924，未操作手机界面。其余弹层共用修复，未逐一完成真机验收。
- APK SHA256：418EEFB202D19346E1D8A80F5FD226FE67D67B5222EABFCDD1FC89D72CD1190B。
