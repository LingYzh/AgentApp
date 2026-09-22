# 新会话默认设置与工作目录迭代

## 用户决定

- 本轮覆盖旧原型的 Provider 默认思考强度/“跟随配置”语义：首次 medium；没有 medium 时采用当前支持列表中位档；自定义/不支持协议不注入字段。
- 新会话页面的 Agent、模型、思考强度、权限模式、工作目录、允许目录范围保存为后续新建默认。输入正文、附件和消息不继承；历史会话中的设置调整不改变默认。
- 用户明确要求图形目录浏览，同时真正改变相对文件路径与 Shell 起点；工作目录和允许访问范围分别选择。
- 普通新对话继承记忆；从 Agent 管理入口明确启动某个 Agent 使用该 Agent 的模型，不受之前的会话模型 override 抢占。

## 实现约定

- NewChatDefaults 保存于独立 new-chat-defaults.json，避免 Provider/设置页旧 AppConfig 快照覆盖新会话偏好。FileStore 先同步发布内存值，Application 的 IO scope 刷最新值；立即新建可读取最新配置，旧写任务不会回写旧快照。
- 完整备份包含并预校验新偏好文件；恢复旧归档时重置偏好，恢复后清理缓存。导出/恢复与偏好刷盘使用同一 store 锁。
- ProviderConfig.reasoningEffort 继续作为适配器请求载体与旧 JSON 兼容字段；Provider 编辑不再配置它，保存为 null。会话实际选择在 ModelResolver 和子代理解析阶段覆盖旧 Provider 默认。
- 旧根会话的未指定档位按 medium/可用默认补齐；支持的明确档位保留。详情/快捷控件不再提供跟随选项，reset 选择默认具体档位，支持时始终有白色滑块。详情 footer 纳入 navigationBars inset，列表独立滚动。
- Conversation.workingDirectory 为 nullable 绝对路径，null 使用 App workspace；相对文件操作、空 list_files 路径和空命令 cwd 使用所选目录。失效目录明确报错；绝对路径仍单独执行范围检查。子代理继承目录。
- 允许目录范围与模式/系统权限仍生效；选 cwd 不自动扩大允许范围。Plan 使用原有专用计划文件；已保存附件引用仍映射 App workspace。
- 原型可视规范继续参考 index.html 源码及 screenshots；内置浏览器拒绝 file URL 并明示不得绕过，未用 localhost 代理绕过该工具限制。没有真实浏览器交互或手机 UI 自动化验证。

## 验证

- 新增默认值、立即继承/重启保存、Provider 默认隔离、Agent 显式启动、备份往返/旧归档/损坏归档、实际工作目录工具路径和子代理继承等 JVM 回归。
- 构建和安装结果见本轮最终交付记录；手机 UI 与真实 Provider 对话由用户复测。

## 本轮最终交付

- `testDebugUnitTest assembleDebug lintDebug` 通过，64 秒；日志 `.Codex/verification/ui-v3-bugfixes/iteration-final.log`。216 项 JVM：213 通过、3 环境跳过、0 失败/错误；lint 0 errors、39 warnings、4 hints。`git diff --check` 通过。
- 测试曾发现默认 workspace 相对路径兼容回归，已恢复 FileStore containment 与最终文件 scope 校验；旧会话限定子目录时的合法相对路径继续可用。自选目录才启用新的 cwd 解析。
- 自选目录产生的文件更改快照保存绝对路径；“查看当前文件”按会话目录解析，并优先使用已保存更改路径，避免打开 App workspace 中同名文件。
- 最终 APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256 `9D6874F0235EE640457982478E02F6A1F1A396C2F8C74F103A4D04EACECD4BF6`。
- 已使用之前授权对 NX809J 执行 `adb install -r`，返回 Success。此次覆盖安装保留应用数据，未卸载、未启动 App、未操作手机 UI。
- 简短复测清单：`.Codex/test-fixtures/new-chat-defaults-and-directories.md`。没有相关 CLAUDE.md；用户 `.idea` 与 `jdk21.zip` 保持原样。

## 原型 HTTP 访问复核（后续更正）

- 用户再次明确要求启动本地服务。重新核对 Browser 文档后确认正式支持 localhost 开发页面；此前将 file URL 拒绝扩大解释为不可尝试本地 HTTP 过于宽泛。
- 已启动 Python 静态服务器，仅监听 127.0.0.1:8765，仅提供 design/agentapp-ui-v3/AgentApp_UI_Prototype_v3 目录；统一终端 session_id=2011。
- 内置浏览器成功访问 http://127.0.0.1:8765/index.html，DOM、截图和点击均可用，实际走通“思考滑块 → 详细档位 → 返回滑块”，标签已 markDeliverable 保留。
- 以后原型交互优先使用此 HTTP 地址；之前 file:// 的拒绝不代表 localhost 被禁止。本次未需要切换 Edge，也未改 App 代码/APK。
