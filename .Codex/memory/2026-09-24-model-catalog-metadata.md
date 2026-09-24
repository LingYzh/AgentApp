# 完整模型目录声明 · 2026-09-24

## 现场结论

- 本机 `http://localhost:5580/models` 匿名访问返回 401；使用现有对应供应商凭据后返回 200、24 个模型。手机的 Anthropic 配置实际使用 `/v1/models`，同样返回 200 和 24 个模型，不是路径拼接失败。
- 手机已有 24 份 `discoveredCapabilities`、没有手动输入能力覆盖；旧客户端已识别四类输入能力，但未保存上下文/输出上限、思考、工具、温度、缓存等完整声明。
- 此本地网关提供 `limit.context/input/output`、`modalities.input/output`、`capabilities`、`thinkingEfforts`、`supportsPromptCaching` 等字段。字段缺失保持未知，不靠模型名推测；明确 false 和明确空列表不得退回正向猜测。

## 实现

- 新 `ModelMetadata` 保存容量、输入/输出上限、思考/温度/工具/缓存声明、思考档位、输入/输出模态及完整 `raw` 模型对象。`ModelCatalog` / `ProviderConfig.discoveredModelMetadata` 保存传递；原四类输入能力和手动覆盖兼容。
- `ModelFetcher` 同时支持现有 OpenAI/Anthropic 风格与 Gemini 目录，容量采用合法正整数；嵌套明确布尔值优先；字符串列表不接收数字/布尔，空列表与未声明不同。通用 `tools` / `attachment` 不能冒充具体能力。
- 供应商页获取后仍须“保存”；模型子页“完成”只返回。新模型详情显示规范化声明，可展开复制完整 JSON（包含价格、interleaved、厂商私有字段等），未识别字段不会被丢弃。改变协议/目录地址时旧自动发现数据不沿用；手动覆盖优先。
- 上下文容量优先手动覆盖，其次接口发现，均无则未知。思考明确 false 时不自动发送思考参数；明确档位限制可选值，缺失时使用原协议规则；未知档位保留原始声明但不构造任意请求字段。
- `thinkingSchemaPath` 仅保存展示，不解释为动态协议。未知 Gemini 别名仍不猜测 thinkingConfig。
- Anthropic 必填 `max_tokens` 的默认值不超过已声明最大输出值；显式用户设置继续优先。OpenAI/Gemini 不因目录元数据而强加可选输出上限。工具、温度、缓存和输出模态声明不代表客户端新增了对应发送能力。

## 验证

- 合并回复/分支修复后的最终 160 项针对性 JVM 测试、构建和 lint 通过（0 errors、39 warnings、5 hints）。APK SHA256 `3F4C68512B2D1F77BDE3132F879301AC6CD0C5AEF4018E89373DF1B6D2A527A5`；手机和模拟器均覆盖安装 Success，保留数据。没有运行或宣称全套测试通过。

- 模拟器 `emulator-5554` 通过 `adb reverse tcp:5580 tcp:5580` 直接访问真实本地服务，仅拉取目录，未向真实模型发送生成请求。
- `Catalog QA`：实际获取、保存、重新打开后 24 份完整元数据保留；`auto` 为 1000000 上下文、64000 输出、不支持思考；`claude-opus-5` 五档思考完整保留；raw 包含 cost 等额外字段。
- 聊天选择 `auto` 后显示“思考 · 不支持”，上下文详情显示 `1,765 / 1,000,000`（估算值），证明运行时读取接口容量。
- 截图与只读响应在忽略目录 `build/reply-fixes-20260924/`；测试凭据不输出日志、不写测试源码。模拟器临时 Provider 只供验收，勿当日常备份。
- 手机更新 APK 后需在实际供应商页重新“获取模型列表”并“保存”，旧配置不会在安装时自动联网迁移。没有修改手机供应商配置。
