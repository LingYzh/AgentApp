# AgentApp UI v3 · 浏览器验证报告

核查日期：2026-09-22。对象：本目录独立HTML原型，不是Android APK。

## 结论

| 验证 | 结果 |
| --- | --- |
| v2保留功能回归 | 30 / 30通过 |
| v3新增交互与状态 | 24 / 24通过 |
| 合计自动行为检查 | 54 / 54通过 |
| 行为测试JavaScript错误 | 0 |
| 自动渲染截图 | 94张 |
| 截图场景JavaScript错误 | 0 |
| 被检查的手机根容器/正文横向溢出 | 0（局部代码/表格横滚为预期） |
| 动效录像 | 124次实际浏览器截图采样，编码合并为43帧，无脚本错误 |

代表性的命令、Diff、收起工具行、首页和Provider页面另作人工图像检查。94张截图已完整渲染和执行容器宽度检查，不等于逐像素视觉认证。

## 环境与方法

浏览器：144.0.7559.96。Python Playwright，412×868和360×800/868 CSS像素视口。截图使用1.5倍deviceScale，动效录像使用1倍后缩到360×758；这些是评审尺寸，不是用户手机真实分辨率。

当前容器策略阻止file://，因此测试通过page.set_content读取相同独立HTML内容。没有运行本机浏览器手工双击文件测试，没有联网加载模型服务。copy测试使用模拟剪贴板验证原文；没有声称在用户系统剪贴板完成测试。

行为记录：regression-test-results.json、v3-test-results.json。
视觉记录：visual-test-results.json。
实际动画帧时间和采集说明：motion-capture.json。GIF约10.6秒，包含末帧停留，不是帧率基准。

本次HTML SHA-256：

```text
43863fe06f0ca0486803915f333d49d1491b41472ed52e22777aaa1c541875dc
```

## 自动行为检查逐项

### 既有能力回归

1. PASS — All 26 routes render and have a top bar
2. PASS — Dark filled actions and send icon use white foreground
3. PASS — Model picker shows per-model native capability icons and search
4. PASS — Context known capacity segments sum to 17.14%, not 100%
5. PASS — Context unknown capacity shows composition, no fake utilization
6. PASS — Compacted context adds summary segment and sums to 6.2%
7. PASS — Slider keyboard steps, explicit none, and reset follow remain distinct
8. PASS — Dragging previews without committing until change
9. PASS — Slider title opens detailed canonical + protocol values
10. PASS — Unsupported effort resets when switching protocol; options follow support
11. PASS — Unknown model default does not display none as selected
12. PASS — Radio global default vs independent multi-select checkboxes
13. PASS — One clear provider edit target preserves edit workflow
14. PASS — Feedback occupies top layout and never covers bottom composer
15. PASS — Feedback moves inside active panel, not underneath scrim
16. PASS — Tool arguments/results expand inline and survive rerender
17. PASS — Pending tool does not fabricate a completed result
18. PASS — Markdown sample covers headings, quote, nested/task lists, highlighted code and table
19. PASS — Code wrap toggles locally without rerendering the page
20. PASS — Code copy sends original text, never highlighted HTML (clipboard mocked)
21. PASS — Prototype Markdown escapes raw HTML and rejects unsafe link schemes
22. PASS — Markdown source view matches raw sample exactly
23. PASS — Stream demo progresses without replaying block/page animations
24. PASS — Navigation and popup enter/exit animate and clean inert snapshots
25. PASS — prefers-reduced-motion disables route/popup animations
26. PASS — Existing send → tool approval → completed flow remains intact
27. PASS — Auto still requires second confirmation
28. PASS — Cancelled compaction does not commit context changes
29. PASS — Read-only child and explicit stop still work
30. PASS — Pending attachment addition/removal still work

### 本轮新增

1. PASS — Cold entry is New conversation, no empty demo record is created
2. PASS — Repeated New conversation without sending creates no history entries
3. PASS — First send creates one record, duplicate send is locked, next new draft is independent
4. PASS — History remains accessible from the navigation drawer
5. PASS — Tool summaries are borderless rows; command has no command preview
6. PASS — Command opens inline Shell containing the actual fixture command and its output
7. PASS — File edit opens raw-line diff with correct additions/removals and line numbers
8. PASS — Diff uses full conversation width with only local horizontal overflow
9. PASS — Copy Diff copies the patch source, not HTML or line-number labels
10. PASS — Empty command output is explicitly distinct from pending execution
11. PASS — Pending/running/failed/cancelled records are not falsely marked completed
12. PASS — Missing snapshot shows no fabricated diff or change count
13. PASS — Theme rerender preserves tool node, expanded state and horizontal scroll
14. PASS — Down-chevron label/icon centers align within one pixel in light and dark
15. PASS — Provider default changes keep stable DOM nodes for smooth control transitions
16. PASS — Non-navigation rerenders preserve text-field focus and edited value
17. PASS — Inline expansion interpolates real layout height and collapses correctly
18. PASS — Rapid expand/collapse reversals finish in the last requested state
19. PASS — Popup-to-popup replacement animates the old surface out and cleans up
20. PASS — Top banner reserves animated height on entry and releases it on exit
21. PASS — Ordinary state changes have local transitions rather than page re-entry
22. PASS — Width, button, send-state CSS transitions are configured
23. PASS — Reduced-motion renders final states with no web animations
24. PASS — Continuous Markdown token updates never reanimate page or content blocks

## 94张截图组成

26页×浅深主题=52张；两主题下Shell、Diff、换行、待批准/失败/空输出/中止/无快照、6种关键面板、Provider多选、顶部提示=32张；360窄屏页面及Shell/Diff/模型/滑块/上下文=10张。

## 复核方式

需要Python Playwright与一个可用Chromium，设置CHROMIUM_PATH可指定浏览器可执行文件。源HTML无需依赖；测试依赖不随包安装。

```text
python tests/build_prototype.py
python tests/regression_v2.py
python tests/browser_v3.py
python tests/render_v3.py
python tests/capture_motion.py
```

动画采集脚本按真实浏览器采样，环境性能会影响帧数与时间；不要把124作为重跑的固定断言。

## 未验证项目

没有编译APK、运行仓库JVM/lint/仪器测试、连接Android设备、授权系统文件、调用Provider、执行Shell、实际保存/删除工作区文件、导入真实配置、推送GitHub或发布release。

Android原生NEW_CHAT导航、首发事务/任务scope交接、配置重建、后台恢复、IME/insets、预测返回、TalkBack、大字体、多指、系统动画缩放、真实剪贴板、长任务取消/权限链、性能与内存，均由Codex本地实施后交用户实机验收。模板中的输入、用量和执行记录为fixture，不能当成真实兼容性或业务测试结果。
