请依据 `design/agentapp-ui-v3/` 对现有 AgentApp 做原生 Android UI 迭代。保留 Kotlin + Jetpack Compose，不换 WebView，不照抄原型中的演示数据、JS Markdown/Diff 或浏览器状态管理。

先读当前仓库 AGENTS.md、相关 CLAUDE.md、.Codex/memory/ 和当前 HEAD，再读原型 README.md、V3_REFINEMENTS.md、DESIGN_SPEC.md、MOTION_MATRIX.md、SCREEN_MAP.md、CODEX_HANDOFF.md、design-tokens.json。研究基线为 2c947234ab0d29ede6e999193717288363195b7a；以本地当前实现为准，不删新功能。

本轮必须落地：
1. 对话流工具调用不包在外层线框/卡片里，不套“完成N步骤”父卡；工具行和展开内容对齐正文左右边界，移除多层padding。
2. 完成的命令收起仅显示“运行了命令”；原地展开真实command和该call对应输出。进行中/待批准/失败/中止用准确状态，空输出和未返回区分，退出码未知则不显示。可复制原文、换行、局部滚动，不另开页/弹窗，也不自动重跑。
3. 编辑收起显示“已编辑 文件名 +N −M”，展开直接显示内联Diff。使用现有FileChanges.diff/ChatMessage.fileChange，保留行号、+/-、原文复制、长行滚动和降级提示。不伪造缺失快照/统计，不重新读取文件当历史快照；不通过FileDiffDialog才能查看内容。
4. 有意义的UI变化尽量都有局部过渡：导航/返回、抽屉/弹层及弹层替换、工具折叠高度、列表管理/选中/过滤、tab、附件、反馈、代码换行/复制/高度、按钮状态、主题。遵循MOTION_MATRIX，保持stable key/焦点/滚动，可打断与反向操作。流式token/120ms采样不重播整段动画；输入、拖动、取消即时响应，遵循系统减少动效。
5. 修正所有图标+文字按钮为同一居中Row，尤其向下箭头应在文字右侧且垂直居中；触控区48dp，视觉图标独立定尺寸，不靠负margin补偿。
6. 冷启动默认独立NEW_CHAT草稿页，不再历史。当前CHAT需要真实ID、create立即落盘，不能直接替换字符串或每次onResume跳转。首次有效发送才幂等创建/保存首条消息/消费草稿/移交任务，不造空历史、不连点重复、不因导航销毁ViewModel取消首发。已有导航恢复、旋转、后台返回、权限/文件选择返回保留当前会话和输入。历史入口继续保留。

同时完整保留v2：暗色实心主按钮白字白图标；上下文8类比例色/余量/未知容量/服务端报告；模型每项原生image/pdf/audio/video能力与来源；独立思考滑块、详细中文+wireValue、null继承≠none关闭、松手提交；顶栏下占位消息；Provider普通radio设默认/管理checkbox多选；共享Markdown标题/正文/引用/列表/代码/表格、原生分块和SelectionContainer及安全链接。

按公共样式/局部动效 → 工具Shell/Diff → 新对话草稿与导航 → v2全功能回归分批实施。保持applicationId/namespace差异、权限/目录、取消传播、单层子代理、Provider协议/重放/可选参数、附件所有权和迁移语义。不要虚构root/Shizuku/后台常驻能力。

每批维护.Codex/memory并运行testDebugUnitTest、assembleDebug、lintDebug，交付APK、改动清单、测试结果、未解决项与简短实机步骤。按AGENTS由用户实机测试；未授权不得操作手机，不自动push或release。第24/25/26页、评审导航、演示状态和计时仅为原型验收，不加入正式App。
