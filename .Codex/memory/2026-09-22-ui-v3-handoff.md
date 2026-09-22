# AgentApp UI v3 交接记录（2026-09-22）

## 当前实现

本次在研究基线 `2c947234ab0d29ede6e999193717288363195b7a` 上，保留本地已有功能，使用 Kotlin、Jetpack Compose 和原生 Markdown 实现 UI v3。设计参考位于 `design/agentapp-ui-v3/AgentApp_UI_Prototype_v3/`，没有把网页状态管理、演示记录或评审页加入 App。

### 会话、草稿与输入区

- 冷入口为独立 NEW_CHAT 草稿；首次有效发送通过 FileStore.commitDraft 回执幂等保存真实会话与首条消息。未发送草稿不进入历史。
- ChatSessions 在 Activity ViewModel 内持有会话 ViewModelStore，草稿转 CHAT 复用任务；SavedStateHandle 保留草稿、输入与附件引用。无后台常驻服务。
- 首页采用64dp顶栏、左对齐品牌标志、中文衬线标题、仅填入输入框的建议按钮及真实 Agent 选择。
- 输入区为圆角卡片，包含附件、原生文本输入、模型选择和发送/停止；下方权限、思考、上下文入口并列。
- 用户消息气泡包含附件；助手正文保留原生 Markdown、选择复制、安全链接和流式120ms采样。
- 工具调用使用透明行与原地展开；命令显示对应call的原始参数和输出，区分待返回/空输出/失败/中止。编辑直接展示保存快照生成的内联Diff，保留统计降级、行号、复制、换行和局部滚动。

### 抽屉、面板与共用样式

- 全局抽屉使用86%宽度，固定标题/关闭入口、独立滚动的最近三个真实根会话和工作环境入口、固定底部设置。
- 模型选择面板包含搜索、Provider分组、当前radio及image/pdf/audio/video原生能力与来源。
- 权限和目录使用底部面板；思考滑块独立，拖动预览、松手提交，null继承与none关闭区分，保留详细中文及wireValue。
- 上下文保留八类估算、余量、未知容量和服务端报告；压缩入口固定底部。
- 补充局部折叠、复制、列表管理、附件、反馈、导航和主题过渡。流式token不触发整段进入动画。
- 共用UiScaffold在顶栏下方为反馈留位；列表管理保留checkbox；Provider普通模式保留radio。文件/记忆预览继续使用共享Markdown。
- 首页中文标题使用Noto Serif SC 500字符子集，字体源说明及OFL许可证随包保存。
- debug源集提供32个Preview组合（360/412dp × 浅深色 × 8个状态）。

### 用户Preview反馈修正

- 抽屉内容与旧Preview显式使用background，消除外层Surface默认白/灰表面色差异。
- 模型按钮改为填满剩余宽度的Box内左对齐，去掉weight(fill=false)与加权Spacer导致的右侧空白。
- 发送触控区48dp、圆形底形40dp；首页标题fillMaxWidth；旧Preview明确为360dp。
- 修正后尚未取得用户新截图，不能标记视觉验收完成。

### Agent图片完整备份

- FullBackupArchive共用于手动完整导出和恢复前自动备份，包含avatars。
- 导出仅在归档副本中写相对图片引用；恢复支持旧绝对路径并重建目标设备本机路径。
- 先临时解包及校验，再自动备份和替换；普通替换失败回滚。回滚失败保留原目录及备份并报错。
- 旧包缺图/空图使用emoji回退并提示数量，不借用本机旧图。
- BackupManager.importFrom返回ImportResult(fileCount, missingAvatars)，SettingsScreen调用方已同步。
- 独立Agent ZIP覆盖/副本、ID与附件重映射协议保留。

## 已验证

- Windows JBR：`E:\Program Files\Android\Android Studio\jbr`。
- 每批执行testDebugUnitTest、assembleDebug、lintDebug；最终结果见 `.Codex/verification/ui-v3-refinement/delivery-check.log`。
- 最终198项JVM测试：195通过、3项Windows符号链接环境跳过，0失败/错误。
- 新增6项完整图片备份测试全部通过，覆盖跨根目录往返、源索引不变、自动备份、旧引用、缺图/空图、非法路径及JSON保留原数据。
- 最终lint：0 errors、39 warnings、4 hints。第三批曾因API26的File.toPath失败，已替换为兼容minSdk24的canonicalPath边界检查并复验通过。
- `git diff --check`通过。本轮后续仅添加handoff/记忆文档，没有再次修改已验证代码。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，24,568,282字节。
- APK SHA256：`E87A15D687BE9F41188F7E3DA4BC04252094E206D2F7F0C16373010A206177D0`。

## 验收边界

- 本轮没有代理侧原生截图渲染、安装或手机操作；实机测试由用户执行。
- 尚待确认：修正后的Preview效果、浅深色视觉、IME/大字体、返回手势、旋转恢复、首发连点及导航交接、真实头像导出/恢复。
- 详细操作和预期见 `.Codex/test-fixtures/ui-v3-refinement-acceptance.md`；此前功能回归见 `ui-v3-acceptance.md`。
- 历史列表、会话树、子会话及计划页面本轮保留功能，未按原型全面改版。
- 无root、Shizuku或后台常驻能力；Activity最终销毁仍取消任务。
- applicationId仍为com.Ling.actant，namespace仍为com.example.myapplication。
- 旧备份未携带的图片无法恢复；完整导入仍是覆盖当前数据操作。
- 本次提交不包括本地IDE个人配置和构建APK；未push或release。
