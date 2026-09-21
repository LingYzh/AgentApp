> 后续实现已由 `2026-09-21-chat-v2.md` 更新：取消附件字节上限、增加 Markdown/diff、改为子代理抽屉与独立只读页面及主动中止。下文保留首版记录。

# 2026-09-21：附件、多模态能力与子代理会话

## 用户确认

- 发送文件包括：具备能力的模型原生接收图片/PDF，以及任意文件复制到工作区供工具使用。
- 子代理跟随主会话管理，访问入口在主会话；工具调用采用紧凑、可展开的调用/结果合并视图。
- 本次允许经济型执行子代理，关键路径可用均衡配置；实机测试仍由用户执行。

## 数据与行为

- ProviderConfig 将 discoveredCapabilities 与 capabilityOverrides 分开，按模型 ID 保存，手动覆盖优先；不根据模型名猜测能力。
- ChatMessage.attachments 保存文件名、MIME、大小和工作区相对路径，不把 base64 放进会话 JSON。SAF 文件即刻复制，不依赖重启后的 URI 授权。
- 附件逐个显示原生/工作区发送方式；支持移除、切换、文件独立发送、图片缩略图。发送预检失败保留草稿。
- AttachmentStore 保留 FileStore 的边界校验，限制复制和请求大小；原生附件缺失或已改变时报错，历史模型切换也校验能力。
- OpenAI Chat 使用 image_url / file；Anthropic 使用 image / document source；Gemini 使用 inlineData。自定义模板缺少可靠附件与工具协议，明确拒绝附件读取。
- 子会话记录 parentConversationId、parentToolCallId、实际模型和 executionStatus；主会话中详情 Sheet 不创建新的运行任务。
- 子代理流式内容按约 750 ms 节流落盘，页面在 STARTED 阶段约 1.2 秒读取；FileStore 会话读写共用同步锁，避免轮询读取半份 JSON。启动时将上次进程遗留的运行中子代理标为已停止。
- 总列表只展示根会话；删除主会话级联删除子代理记录，保留工作区文件。孤儿或循环旧数据仍可见，导入包内的循环关联拒绝导入。
- 独立会话 JSON、Agent ZIP 随所选根会话包含子代理及所引用附件；COPY 重映射父会话 ID 和附件路径。完整备份原已覆盖 workspace。
- 更改 Provider 协议或接口 URL 后不沿用旧接口自动发现的能力；手工覆盖不清空。
- 旧子代理无父关联，继续保留，不用标题/时间戳猜归属。
- 测试说明及小文件在 `.Codex/test-fixtures/attachments/`。

## 来源与实现取舍

- [Anthropic Models](https://platform.claude.com/docs/en/api/models)：capabilities.image_input.supported / pdf_input.supported。
- [OpenRouter Models](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties)：architecture.input_modalities。
- [OpenRouter 多模态协议](https://openrouter.ai/blog/insights/every-modality-one-api/)：PDF 使用 file 内容类型；仅 architecture.input_modalities 的 file 作为此目录格式的 PDF 能力别名，通用顶层 file 不作此推断。
- [OpenAI File inputs](https://developers.openai.com/api/docs/guides/file-inputs)：Chat Completions 的 file_data 仅支持 PDF；图片使用 image_url。
- [Gemini Models](https://ai.google.dev/api/models)：supportedGenerationMethods 描述操作类型，不能推导输入模态。
- [Models.dev](https://github.com/anomalyco/models.dev)：参考其输入/输出模态及供应商覆盖的组织方式；没有引入在线目录依赖。
- [Cline ModelInfo](https://github.com/cline/cline/blob/main/apps/vscode/src/shared/api.ts)：参考显式能力元数据；本项目未知能力保守降级为工作区文件。

本地上限为移动端内存保护值，不代表供应商官方最大值：工作区文件 20 MB、原生单文件 4 MB、原生请求历史合计 12 MB、每消息 8 个附件。文件工具仍主要面向文本，工作区保存不等于任意二进制可解析。

## 验证

- 使用本机 JBR 21：`assembleDebug`、`testDebugUnitTest`、`lintDebug` 均通过。
- 88 项 JVM 测试，0 failures / errors；新增覆盖附件路径/大小/缺失、各协议请求内容、能力目录、根会话筛选、子代理取消、父子与附件 COPY 导入、Agent ZIP 包含子会话、启动恢复。
- Lint：0 errors、29 warnings、1 hint；warning 数与既有基线一致。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，22,030,892 bytes。
- SHA-256：`EFCA72E2B2E8525A3C743F52A4288D8E18840441358C5E620D0E6B7EA9A1201A`。
- `git diff --check` 通过；相关目录未发现额外 CLAUDE.md。
- 未调用用户真实 Provider、未覆盖安装或操作手机。UI 体验与网关实际兼容性待用户按测试说明确认。
