# 权限实现源码对照与 Android 适配

## 参考范围

用户指定研究仓库：<https://github.com/Austin1serb/Anthropic-Leaked-Source-Code>，核对提交 `3fa80a4417103f205c97e7468337703094c0e3c1`。这是用户提供的研究来源，不视为官方规范。仅阅读源码，没有运行仓库脚本。

另外参考 Codex `codex-rs/core/src/context_manager/updates.rs` 的追加上下文消息，以及 OpenCode 的工具/路径权限说明。

## 已直接核对的机制

1. `utils/permissions/permissions.ts`：工具声明与调用授权分离。内部授权链先检查整工具 deny/ask，再检查工具自己的参数级权限、内容规则和安全检查；重新读取最新权限状态后才判断 bypass/allow；未决结果进入 ask。外层还处理 dontAsk、Auto 等策略。因此“给了工具声明”并不代表“这次执行已获准”。
2. `utils/permissions/filesystem.ts`：既核对输入路径，也核对解析后的路径；特殊内部可写位置、计划文件与普通文件规则分开。其计划 slug 前缀规则比本 App 的单一会话计划文件要求更宽，不能原样替换本 App 的精确路径检查。
3. `tools/EnterPlanModeTool/EnterPlanModeTool.ts`：进入 Plan 被视为只读的控制动作，主会话可以使用；子代理不能自行进入。它不是普通文件写入操作，不能因为 Readonly 屏蔽所有写操作而连带禁止。
4. `tools/ExitPlanModeTool/ExitPlanModeV2Tool.ts`：退出 Plan 是需要主会话用户交互的工具；先验证模式，读出计划，经过用户确认再继续。该仓库团队协作审批另有 mailbox 等机制，本 App 单层子代理不照搬团队调度。
5. `utils/toolSchemaCache.ts`：按会话缓存工具 schema，源码明确指出工具前缀改变会影响后续缓存。`services/api/promptCacheBreakDetection.ts` 也分别追踪 system、tools 和工具散列变化。
6. `utils/attachments.ts`：模式变化和提醒通过新的上下文附件消息表达。借鉴其追加模式，不把动态状态反复塞回历史 system 或工具说明。

## 本 App 落地取舍

| 机制 | Android 实现 |
| --- | --- |
| 工具可见性与授权分离 | 模式间保持 schema 稳定；执行前由 ToolExecutor/PermissionSession 检查，再由文件与命令实现检查具体参数。Agent 配置的工具白名单仍有效。 |
| 动态状态告知 | system 首次冻结；当前权限、允许/禁止操作、工作区、共享存储、目录范围、计划路径在请求边界以隐藏环境消息追加，状态未变不重复添加。 |
| 限制优先 | Readonly/Plan、会话目录和子代理限制不能被命令自动放行列表绕过；审批等待结束后再次检查权限是否收紧。 |
| Plan 控制 | 主代理 Readonly 可进入 Plan；只能修改该会话的一个计划文件，检查符号链接重定向；子代理只读且不能切换或审批。 |
| 命令审批 | Accept Edit 默认 ask；低风险命令可以由用户在弹窗中加入精确命令列表。没有把复杂 shell 解析伪装成 Android 沙箱。 |
| 目录范围 | 默认应用 UID 可访问路径，共享存储依赖 Android 所有文件访问授权；配置受限目录时阻止任意 shell，因为 App 当前没有能可靠限制命令文件访问的进程沙箱。 |
| Auto 语义 | 遵循用户要求，取消 App 内审批，仍受 Android UID 权限和显式会话目录限制。参考仓库的 Auto 包含额外自动决策逻辑，不直接等同于这里的 Auto。 |
| 子代理 | 继承主代理的实时权限与范围，不允许通过委派扩大权限；不引入团队协作中的独立审批与恢复权限机制。 |

## 验证与边界

- 已测试模式切换、目录切换只追加环境，既有请求前缀与工具声明保持不变；Readonly→Plan→计划反馈→接受执行路径通过。
- 系统目录、其他 App 私有目录仍由 Android 限制；本次不接入 root/Shizuku。
- 本次没有引入该仓库的 classifier、团队 mailbox、命令 shell AST 或完整通配符规则系统。稳定声明不会阻止模型偶尔尝试被禁工具，真正的权限边界始终是执行层。
