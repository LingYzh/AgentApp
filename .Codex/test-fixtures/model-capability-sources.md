# 模型目录能力声明核对（2026-09-23）

| 服务 | 官方声明位置 | 本轮处理 |
| --- | --- | --- |
| OpenAI | `/v1/models` 文档只列 id/object/created/owned_by | 保持能力未知，不能根据模型名称猜测 |
| DeepSeek | `/models` 只列基础模型对象 | 同上 |
| Anthropic | `/v1/models` 的 `capabilities.image_input.supported`、`pdf_input.supported`；另含 thinking/effort 等 | 保留输入能力解析；思考协议仍使用现有逻辑 |
| OpenRouter | `architecture.input_modalities`，另有输出模态及 context_length | 保留 image/file/audio/video 输入解析，输出不用于输入能力 |
| Mistral | 模型对象的 `capabilities.vision`，另有 function_calling/max_context_length | 新增 vision→图片，不从 vision 推断 PDF |
| Gemini | `inputTokenLimit`、`outputTokenLimit`、`supportedGenerationMethods`；标准模型资源没有输入模态清单 | generateContent 不等于支持所有附件；兼容网关额外返回的显式模态字段 |
| Ollama | 原生 `POST /api/show` 返回 capabilities（如 vision），与 OpenAI 兼容目录不同 | 本轮不向任意兼容服务自动探测或逐模型请求 Ollama 私有端点；可手动设置 |
| 本机 5580 网关 | 实测有 `modalities.input`、`capabilities.input.{image,pdf,audio,video}`、`inputTypes` | 新增映射；不读取 output 作为输入支持 |

## 官方资料

- [OpenAI Models](https://platform.openai.com/docs/api-reference/models)
- [DeepSeek List Models](https://api-docs.deepseek.com/api/list-models/)
- [Anthropic List Models](https://platform.claude.com/docs/en/api/models/list)
- [OpenRouter Models](https://openrouter.ai/docs/api/api-reference/models/get-models)
- [Mistral Models](https://docs.mistral.ai/api/endpoint/models)
- [Gemini Model resource](https://ai.google.dev/api/models)
- [Ollama Show model details](https://docs.ollama.com/api-reference/show-model-details)

## 验收

1. 编辑本地供应商 → 获取模型列表 → 打开具体模型；显示接口声明来源，图片开关与接口一致。
2. 改成手动覆盖后保存，再获取列表，手动值保持优先；恢复接口能力后回到发现值。
3. 只有基础字段的目录应显示“未提供可识别的输入能力声明”，不是声称不支持。
4. 滑块 → 详细档位列表应有过渡；点任一档位反向过渡到滑块；快速切换不能触发退出控件。

新增 4 项 JVM 回归覆盖本地字段、显式 false 优先、畸形字段隔离、Mistral 裸数组、输入输出区分与 Gemini 保守行为。手机交付后需重新获取列表并保存，旧的空发现缓存不会自行变成新结果。
