# Output-cap and truncated-response handling

- `ProviderConfig.maxOutputTokens` is an optional, provider-wide completion cap. Positive values are forwarded as `max_tokens` to OpenAI-compatible and Anthropic requests, and as `generationConfig.maxOutputTokens` to Gemini. Custom templates are intentionally untouched.
- OpenAI-compatible providers send no implicit output cap. This preserves each upstream's default and avoids model-ID guessing.
- Anthropic Messages requests require `max_tokens`. When the field is empty, the app sends `65536`, replacing the former `8192` cap. The Anthropic protocol counts thinking and visible output in that limit. Manual thinking therefore rejects a configured cap that is not larger than its fixed thinking budget; the provider editor presents the same constraint before save/test.
- Parsers retain provider stop reasons in `StreamEvent.Done`; callers must distinguish `length` from natural completion and record only metadata such as cap, stop reason, and usage. Do not log response content.
- DeepSeek's Chat Completions documentation states that an omitted `max_tokens` defaults to 64K in thinking mode and 128K at `reasoning_effort=max`; `finish_reason=length` can mean the output cap or the context limit. The official DeepSeek Anthropic-compatible endpoint also requires `max_tokens` in its examples.
