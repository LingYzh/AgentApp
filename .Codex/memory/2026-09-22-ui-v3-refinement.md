# UI v3 视觉还原与头像完整备份

## 第一批
- 首页采用独立 64dp 顶栏、左对齐品牌/标题/建议区；建议只写入草稿。
- 输入区改为 23dp 圆角卡片，内部附件、无描边输入、加号/模型/发送；下方并列权限/思考/上下文入口。
- 首页使用 OFL Noto Serif SC 500 标题子集，许可证随 APK；正文保留系统无衬线字体。
- 用户气泡包含附件分隔，助手使用原生品牌标志；保留已有工具与 Diff 真实记录。
- testDebugUnitTest、assembleDebug、lintDebug 成功（batch-a.log）；未操作手机。

## 第二批
- 全局抽屉为 86% 宽、23dp 右侧圆角；真实最近三个根会话，滚动内容与固定设置分离。
- 模型面板包含搜索/Provider 分组/当前 radio/协议与四类能力来源；权限改底部面板、目录与固定应用操作。
- 思考使用底部局部面板与同层详细选择；上下文使用大号真实估算与八类明细。
- debug 源集提供 360/412dp × 浅深色 × 8 个状态的 Compose Preview；尚未实际渲染或实机验收。
- testDebugUnitTest、assembleDebug、lintDebug 成功（batch-b.log）。

## 第三批与预览反馈
- FullBackupArchive 为手动完整导出及导入前自动备份共用的纯流式归档逻辑，增加 avatars。
- 归档中的 Agent 图片使用相对引用，导入时重建本机绝对路径；旧包缺失图片时清空失效引用、保留 emoji，并返回缺失数量。
- 导入先在临时目录解包和校验，再自动备份及替换；普通替换失败回滚，回滚失败时保留原目录和备份并报错。
- 新增六项 JVM 测试：跨根目录往返、原索引不变、自动备份恢复图片、旧绝对路径、缺图/空图降级、非法路径/JSON 保留原数据。
- 首轮第三批测试和构建成功；lint 捕获 File.toPath 的 API 26 限制，改为 canonicalPath + 分隔符前缀以兼容 minSdk 24。final-check.log 三项检查全部成功。
- 用户 IDE Preview 反馈：旧 Drawer Preview 的外层 Surface 默认取 surface（白/灰），实际抽屉用 background；现在两者显式统一背景，内容本身也绘制同一背景。
- 输入 Row 中 model weight(fill=false) 与 Spacer weight 同时存在，缩短标签留下未分配尾部宽度；改为 Box(weight=1) 承载左对齐标签，发送固定在行末。48dp 触控与40dp发送底形分开。
- 首页标题显式 fillMaxWidth，旧 Preview 固定 360dp，避免按内容收窄；仍需用户刷新 Preview 确认实际渲染。
- 独立 Agent ZIP 协议、附件迁移、applicationId/namespace、任务取消和首发幂等语义未修改。

## 最终验证
- delivery-check.log：testDebugUnitTest、assembleDebug、lintDebug 全部通过。
- 198 项测试（195 通过、3 环境跳过）；lint 0 errors / 39 warnings / 4 hints；diff --check 通过。
- 未实际渲染原生截图，未操作手机；预览反馈修正及键盘/导航/恢复实机行为待用户验收。

## 交接
- 汇总交接记录：2026-09-22-ui-v3-handoff.md，包含本轮与此前未提交的v3改动、APK校验值、验证证据和实机验收边界。
