# 列表管理与模型列表网关

## 本次用户确认

- Provider、Agent、会话、文件、记忆、Skills 均使用列表内管理模式和 checkbox 多选。
- 管理模式显式显示导入、导出等按钮，导出选项在保存前的确认弹窗选择。
- 会话、记忆新增独立迁移。

## 实现约定

- `ui/components/ListSelection.kt` 统一管理状态、全选、返回退出、操作栏和批量删除确认。
- 多选与 Provider 全局默认模型相互独立；管理模式不执行默认模型切换或打开编辑页。
- 选择集合保存为基础类型 List，每次替换；操作只使用当前列表中仍存在的 ID。
- 删除先快照已选 ID 并确认，后台逐项或单次合并写入，刷新列表后解除忙碌锁。
- Provider、Agent、会话、记忆复用 `ConfigurationTransferHost`；会话 JSON 格式为 `actant.conversations`，记忆为 `actant.memories`，version 1。
- 会话保留完整消息、工具记录及 Agent / Provider 引用，不携带关联配置和工作区文件。导入预览提示缺失引用。
- 记忆保留索引元数据和 Markdown 正文；更新保留其它条目，副本使用新 ID。
- 会话、记忆导入沿用全部校验后写入、64 MiB 限制、同 ID 更新或副本、普通写入失败恢复机制。
- DeepSeek 精确匹配 `api.deepseek.com`，OpenAI / Anthropic 网关的模型列表请求均使用根 `/models` 与 Bearer 鉴权。
- 其它供应商保留路径前缀，Anthropic / Gemini 仍按协议补版本路径；不对未知域名盲目移除 `/anthropic`。
- Provider 新增可选完整 `modelsUrl` 字段和编辑入口，留空自动识别；配置导入导出保留该字段。
- 模型列表请求复用可取消 HTTP 响应读取，保留自定义请求头覆盖优先级。
- 文件与 Skills 管理模式使用保存的选择快照；选定导出先确认数量和名称，再通过 SAF 保存 ZIP。
- 工作区 ZIP 只导出经过 `workspaceFile` 边界校验的选中文件，并保留相对路径；文件导入按原始字节复制，避免二进制损坏。
- Skill 子集 ZIP 使用 `{name}/SKILL.md` 及同目录附属资源的现有导入格式，并校验 Skill 目录及其文件的 canonical 边界。
- 文件与 Skills 批量删除在单次后台循环后刷新一次；失败时也刷新已完成的部分并传播取消信号。

## 官方依据

- https://api-docs.deepseek.com/api/list-models/
- https://api-docs.deepseek.com/guides/anthropic_api/

## 验证

- `testDebugUnitTest assembleDebug lintDebug` 通过；70 项 JVM 测试零失败，lint 零错误、29 条已有警告。
- 网关请求测试覆盖 DeepSeek OpenAI / Anthropic 路径及 Bearer 头、未知网关路径保留、显式 URL、自定义请求头和 Gemini 参数。未使用用户凭据进行真实网络模型列表调用。
- Nubia NX809J / Android 16 实机核对六类列表的管理入口及操作栏，Provider checkbox、全选与导出选项，Agent 会话选项。
- 实际通过 SAF 导入/导出临时会话和记忆，各导出 JSON 精确包含所选两条记录；文件 ZIP 精确包含所选文件并保持内容字节。
- Skills 临时合集通过实机导入、选中导出和重新导入；工作区子集、越界路径、Skill 子集恢复另由 SelectedFilesExportTest 覆盖。
- 批量删除确认显示所选数量和名称，长名称列表限高滚动；实机核对只显示两条临时会话及单个临时文件。
- 测试 Skills 全选时曾误包含一个原有 Skill 并删除，已从删除前的完整导出 ZIP 恢复，逐文件字节比较一致；最终清理后再次核对一致。后续实机测试必须按临时项目名称逐项选择，不能假设列表只有测试数据。
- 临时会话、记忆索引及正文、Skill 目录、工作区文件均已确认清理；外部 Download/Actant-Smoke 内明确命名的测试文件已删除。
- 最终 APK 已覆盖安装，保留本机配置与数据。
