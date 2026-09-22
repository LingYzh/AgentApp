# UI v3 本轮交付与实机验收

## 本轮改动

- 首页：64dp 顶栏、左对齐品牌与中文衬线标题、三个仅填草稿的建议按钮、真实 Agent 选择。
- 聊天：原生输入卡片、内部附件与模型入口、右侧发送/停止、底部权限/思考/上下文入口；用户气泡附件分隔、助手标志和原有内联工具/Diff。
- 抽屉：86% 宽、固定标题与底部设置、独立滚动区域、真实最近三个根会话及全部功能入口。
- 面板：模型搜索/Provider 分组/选中 radio/原生能力与来源；权限/目录底部面板；思考滑块与同层详情；上下文八类估算与报告、固定压缩操作。
- 图片备份：手动与自动完整备份包含 avatars；相对引用、跨设备重建路径、临时校验与失败回滚；旧包缺图提示并使用 emoji。
- IDE 反馈修正：抽屉 Preview 与运行时使用同一个 background；模型槽填满剩余宽度，发送按钮不再被 weight(fill=false) 留白挤偏；标题填满正文宽度。

## 验证结果

- 第一批 batch-a.log、第二批 batch-b.log：testDebugUnitTest / assembleDebug / lintDebug 均通过。
- 第三批初次 lint 找到 API 26 toPath 调用，已替换；final-check.log、delivery-check.log 全部通过。
- 最终 198 项 JVM 测试：195 通过、3 项 Windows 符号链接环境跳过；0 失败/错误。新增头像归档测试 6 项全部通过。
- 最终 lint：0 error、39 warning、4 hint；git diff --check 通过。
- APK：app/build/outputs/apk/debug/app-debug.apk，24,568,282 字节。
- 全部代码使用本地当前实现；没有操作手机、安装 App、push 或 release。

## Preview 与尚未完成的验收

- Android Studio 打开 app/src/debug/java/com/example/myapplication/ui/chat/ChatVisualPreviews.kt，提供 360/412dp × 浅/深色 × home/chat/running/drawer/model/permissions/reasoning/context 共32个组合。
- 原 ChatScreenEmptyPreview、AppDrawerPreviewLight/Dark 继续保留，显式采用360dp及正确背景。
- 本轮没有代理侧原生截图渲染或手机验收；用户已提供的 Preview 截图用于定位问题，修正后仍需刷新确认。编译/单元测试通过不代表像素级视觉验收通过。
- 大字体、IME、返回手势、面板动效及首发导航连续性须由下方实机步骤确认。
- 历史列表、会话树、子会话及计划页面本轮不改版，入口和功能保留。
- 旧备份本来没有携带的图片无法凭空找回；导入时会明确提示缺失。

## 简短实机步骤（由用户执行）

1. 更新安装本轮 APK，查看浅/深色首页；标题正常两行，抽屉底色与页面一致，发送按钮靠输入卡片右侧。360dp/系统较大字体时检查不截断、不重叠；必要时建议按钮可换行。
2. 打开抽屉：中间内容可滚，设置保持底部；真实最近会话可打开；新对话为空草稿。选择 Agent、点建议、添加文件，再返回/旋转/打开文件选择器后确认输入与附件仍在。
3. 打开模型、权限、思考和上下文面板；确认模型能力与来源仍在、权限范围仍可编辑、思考跟随和关闭是不同值、滑块松手才提交、容量未知不会显示假百分比。
4. 发送并快速再点，确认只产生一条首发和一个历史会话；生成时可立即停止。展开已有命令/Diff，确认内容来自对应记录。切到后台再返回确认仍是当前会话。
5. 为一个测试 Agent 选择图片并完整导出，检查 ZIP 中有 avatars 文件且 agents.json 使用 avatars/... 相对路径。选择安全的测试数据环境导入该包，确认图片显示；恢复前自动备份包同样应含原头像。旧无图片包应显示缺失提示、emoji 回退。

不要为验证破坏现有正式数据；完整导入沿用覆盖当前数据语义，导入前自动保存当前完整备份。
