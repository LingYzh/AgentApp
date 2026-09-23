# UI v3 全范围补齐

- 会话面板改为 `session/{conversationId}` 页面，使用 Activity 的 ChatSessions 根据 committed ID 复用原会话。面板可见期间观察计划/子代理；全局抽屉与面板分离。
- 审批与会话错误由 RESUMED 页面展示，转场不重复呈现、不自动消费审批；错误关闭时按原错误值清除，避免清掉后来产生的错误。
- 产物由成功 write_file/edit_file 的保存快照派生，每次变更保留独立消息身份；历史 Diff 不读取当前文件，当前文件入口继续走现有路径校验。新增 SessionArtifactsTest。
- 上下文面板持有活动反馈宿主，底层聊天同时隐藏该宿主，修复压缩失败提示被面板遮挡；仍悬浮于标题下，不推挤正文。
- 抽屉最近会话按会话关联 Agent 读取图片/名称，已删除 Agent 明示；历史列表、六类菜单、头像失败回退见同日 lists 记录。
- 设置子页面增加局部切换、退出 inert 和 SaveableStateHolder，返回保持各页面滚动；工具入参回参补局部展开动效。
- 行内代码使用单独的代码字体测量高度并按实际行基线绘制；只以字符矩形确定水平范围，过滤断行及无效矩形。实际设备效果留待用户验收。
- 原型用本机 8765 端口静态服务在内置浏览器打开，遍历 26 页、操作六类列表菜单，对照记录见 `.Codex/test-fixtures/ui-v3-completion-acceptance.md`。
- 未操作手机、安装 APK、提交、推送或发布。原生视觉、IME、TalkBack 与生命周期验收由用户执行。
- 后续追加安装授权：交付 APK 时发现已授权 ADB 手机即安装，签名冲突可直接卸载重装；实机测试仍由用户执行。2026-09-23 14:02 在 NX809J 上覆盖安装因签名冲突失败，随后卸载 `com.Ling.actant` 并安装成功；已核对 versionName=1.0、versionCode=1 和安装更新时间。此安装发生于上述初次交付之后。

## 最终验证

- `testDebugUnitTest assembleDebug lintDebug` 全部通过；237 项 JVM 测试中 234 通过、3 环境跳过、0 失败。
- lint 0 errors / 39 warnings / 3 hints，与修改前数量相同；`git diff --check` 通过。
- 新增 12 项测试：行内代码几何 5、选择范围 4、保存产物提取 3。生命周期/图片加载/TalkBack 等界面检查已列入用户验收步骤，不冒充 JVM 覆盖。
- 构建日志、修改文件列表与校验值位于 `.Codex/verification/ui-v3-completion/`；APK SHA256：`83131BEB00056A472314CA062FDF403DB78597ABBD0B93DE866059FBC39CC8FA`。
