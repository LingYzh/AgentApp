# Provider 与 Agent 配置迁移

## 用户确定的行为

- Provider 可独立导出、导入；导出时选择是否包含 API Key。
- Agent 包只包含 Agent、自定义头像和可选的所属会话，不附带 Provider 配置。
- 导入检测同 ID 冲突，不静默覆盖；用户在摘要中选择更新已有或作为副本导入。
- 更新不含 Key 的 Provider 配置时，保留本机已有 Key。

## 数据格式与边界

- Provider：`actant.providers`、version 1 的 JSON。
- Agent：ZIP 内含 `agents.json`（`actant.agents`、version 1），以及被引用的 `avatars/<id>.png`。
- 不包含 Key 时同时排除附加请求头；附加请求头可能采用自定义字段保存凭据。
- Provider 更新只合并模型配置，保留主题、工具轮次上限等全局设置。
- Agent 所属会话按 `Conversation.agentId` 筛选；不包含其它 Agent 或默认无 Agent 会话。
- 会话导出包含消息和工具记录，不包含工作区产物、技能或记忆文件。
- 副本导入为冲突项重新生成 ID，并同步调整随包会话的 Agent 引用和头像路径；不改动原有项目。
- Agent 的 Provider 引用原样保留；缺失时提示单独导入或重新绑定 Provider。
- 导入在确认之前只读；全部解析、ID、ZIP 条目与关联校验通过后才写入。普通写入失败时尝试恢复本次涉及的原文件。
- 配置包解压后上限 64 MiB，单个头像上限 8 MiB；拒绝路径越界、重复 ID/条目、缺失头像和不属于随包 Agent 的会话。

## 入口与验证

- Provider 菜单支持导入、选择 Provider 导出、导出全部；列表项支持单项导出。多选弹窗显示已选数量，零项时禁用继续。
- Agent 菜单支持导入、导出全部；列表项支持单项导出。导出可选包含所属会话，默认不勾选。
- 使用系统文件选择器保存和读取；导入先显示数量、缺失 Provider 引用和 ID 冲突摘要，再确认写入。
- 同 ID 且名称不同的冲突摘要同时显示文件名称和本机名称；Provider 副本导入不会改动现有 Agent 的 Provider 绑定。
- `testDebugUnitTest assembleDebug lintDebug` 通过，59 项 JVM 测试零失败，其中迁移测试 11 项。
- Nubia NX809J / Android 16 实机完成 Provider、Agent 临时配置导入、单项导出、Agent 重复更新不产生副本，以及 Provider 多选导出验证。
- 多选验证使用两个临时 Provider，只勾选一个，拉取实际 JSON 确认仅有该项且不含凭据；Agent 导出 ZIP 确认仅含 Agent manifest，不附带 Provider。
- 实机验证期间未创建真实会话；会话归属筛选、消息往返和副本映射由 JVM 测试覆盖。
- 已覆盖安装最终 APK，临时配置、Agent 和手机 Download 中明确命名的测试文件已清理。
- `connectedDebugAndroidTest` 在指定单一无线连接上通过，1 项设备测试零失败。
- 多选状态以只读 List 类型暴露，每次替换集合，避免可变集合原地修改绕过 Compose 重组。
