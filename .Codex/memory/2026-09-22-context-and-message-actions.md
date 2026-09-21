# 上下文追加、消息管理与附件预览

## 用户要求与设计

- 动态权限模式、目录范围等信息必须在新请求前追加，不能回写旧上下文；工具执行层继续强制校验。
- 工具声明保持模式间稳定，避免切换 Readonly/Plan 时重写工具 schema。运行时环境消息明确当前可用与受限操作，拒绝结果解释权限原因。
- 主代理可以从 Readonly 主动进入 Plan；进入后仅允许计划文件写入，执行计划仍需用户选择 Auto/Accept Edit。子代理不能切换模式。
- 请求尚未获得有效模型输出即失败时，未送达用户消息及错误记录保留在界面，但从后续模型请求中排除。兼容旧版失败附件记录，不能丢弃此前已成功使用的附件。
- 用户明确允许修改历史导致缓存变化。消息可复制、编辑正文、移除附件或删除；删除携带工具调用的 assistant 时一起移除关联响应与媒体，避免悬空 tool result；不撤销已执行操作。
- 子代理仍只读，可复制与预览附件，不提供消息编辑/删除。
- diff 默认收起，仅显示 +/- 行数；展开显示内联变更，代码自动折行，原始行号保留在独立栏。

## 参考阅读（自行实现，不复制整段代码）

- Codex 公开实现：https://github.com/openai/codex/blob/main/codex-rs/core/src/context_manager/updates.rs ，上下文片段转换为消息。
- OpenCode 权限设计：https://opencode.ai/docs/permissions ，工具级 allow/ask/deny 与路径规则。
- 用户指定研究仓库：https://github.com/Austin1serb/Anthropic-Leaked-Source-Code 。阅读 `utils/toolSchemaCache.ts`、`services/api/promptCacheBreakDetection.ts`、`tools/EnterPlanModeTool/EnterPlanModeTool.ts`、`utils/attachments.ts`，用于核对 schema 稳定性、计划模式切换及后续提醒思路；不是官方来源。

## 验证

- `assembleDebug testDebugUnitTest lintDebug` 全部通过，127 项 JVM 测试，0 失败/错误/跳过；Lint 0 错误、37 警告、2 提示（含 PDF bitmap 的 UseKtx 风格建议）。
- 回归覆盖稳定 system/schema、环境不变不重复注入、权限/目录变化仅追加且历史前缀一致、Readonly→Plan→反馈→接受执行、失败附件批次剔除且工具响应成对保留、旧版部分输出保护、消息编辑/删除关联工具记录。
- 已通过无线 ADB `install -r` 覆盖安装 NX809J，返回 `Success`。没有操作手机界面或运行设备自动化测试；实机交互由用户验收。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256：`04B0400A88D0F3D186F0135A95D3CAC7AEC7800DBDDF3DB1FA42675F1D2B80F9`。
- 手测说明：`.Codex/test-fixtures/context-message-actions.md`。
- 保持请求前缀并不保证上游实际缓存命中；仍受供应商协议、缓存时效和最小长度影响。用户主动编辑/删除历史与排除失败请求会改变对应位置之后的上下文。
