# 权限运行时与计划模式

- `PermissionSession` 将会话的 Accept Edit、Plan、Auto 与 Readonly 模式作为工具后端闸门，而非只依赖系统提示。
- `PermissionCoordinator` 以 StateFlow 暴露命令和计划审批；取消等待会自动移除请求，避免迟到审批。
- 相对路径仍通过 `FileStore.workspaceFile` 保持工作区边界；绝对路径可以受会话授权目录的 canonical 路径约束。Plan 仅能写入每个会话固定的 `.plans/<conversation-id>.md`，且拒绝重定向的计划文件。
- Accept Edit 下每条未被记住的命令都需审批；设置页可显式记住任意精确命令，快速“长期允许”仅限 CommandPolicy 的低风险精确命令。限定目录时不运行 shell。
- Accept Edit 不允许 Agent 修改 config.json 或 conversations 元数据，防止通过文件工具自行扩大权限；Auto 保留应用 UID 范围内的文件能力。Auto 只取消应用内确认：会话的显式目录范围仍限制普通文件和 shell 命令，但不覆盖应用托管的 memory/skills 根；这些专用工具仍受 Agent 工具白名单与 Readonly/Plan 写入门禁，并由 FileStore 单独校验名称、规范路径和符号链接。
- 工具新增 `edit_file`、`run_command`、`enter_plan_mode` 和 `exit_plan_mode`。每轮模型请求重建工具描述和系统提示以反映最新模式、目录范围与计划路径。
