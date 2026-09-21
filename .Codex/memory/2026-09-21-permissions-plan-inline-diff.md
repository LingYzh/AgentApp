# 会话权限、计划审批和内联 diff

## 用户决策

- 本轮共享存储全文件访问 + App UID 命令；不接 Shizuku/root。Android 系统目录和其他 App 私有目录仍受系统权限限制。
- 继续经济/均衡子代理；允许构建成功后覆盖安装现有无线设备，不操作手机进行自动化体验测试。
- 默认 Accept Edit 自动文件修改，命令审批；可记住精确低风险命令。Auto 取消 App 内审批，Readonly 禁止工具修改和命令，Plan 只允许主代理编辑本会话指定计划文件。
- 权限从主会话继承到子代理，禁止子代理切换模式；会话可额外限定目录。
- enter_plan_mode/exit_plan_mode 使用稳定会话计划路径，拒绝/反馈保留 Plan，接受时选择 Auto/Accept Edit 并继续同一执行循环。

## 实现要点

- 工具默认友好摘要，参数/原始结果收进详情；文件 diff 对话流内直接展示红删绿增、行数，新增文件与覆盖编辑区分。
- 进入旧会话首次定位最新消息，流式更新只在底部附近自动跟随。
- 计划正文和审批使用 Markdown；命令审批显示命令及 cwd，取消任务会撤回待审批项。
- 相对工作区路径仍保留 FileStore 路径边界；工具额外支持 Android UID 能访问的绝对路径。
- 会话目录约束使用规范化边界检查；存在目录限制时禁用任意 shell，避免绕过范围。

## 审阅与边界

- root 复审界面，工具详情改为标题行展开入口；内联 diff 增加文件名、细边框、红绿行数及深色主题对比度，优先展示变更段及附近上下文。命令审批使用独立等宽代码区域，避免短命令占满屏幕。
- Accept Edit 保护权限配置及会话元数据，避免通过文件修改自行放宽授权。手动配置可放行任意精确命令，审批弹窗快速记住仅限低风险命令。
- Plan 写入替换 inode，避免已有硬链接修改其他文件；拒绝计划文件和祖先目录的符号链接重定向。
- Shell 使用 nonce 门控、已验证独立进程组及 `/proc/<pid>/stat` 启动时间确认身份，在取消/超时时清理进程组。最长 30 秒，输出最多 64 KiB。主动自行 setsid 的命令仍可能脱离进程组，这不是 OS 沙箱。
- 审批确认同步撤回已处理请求，避免界面重复读取旧计划审批。

## 交付验证

- `assembleDebug testDebugUnitTest lintDebug` 成功；117 项 JVM 测试全通过，无跳过。Lint 0 errors、36 warnings、2 hints。
- 覆盖安装至已连接 Nubia NX809J：`adb install -r` 返回 Success；未操作手机界面或创建实机测试数据。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- SHA-256：`0D061B75DC3871A0770F2DCA3D2D5DAA8C062BEBFF6D12FF00CD28A040F08617`。
- 操作验收清单：`.Codex/test-fixtures/permissions-plan.md`。UI 为代码与布局审阅，实机显示/触控和 App UID shell 兼容性仍由用户验收。
