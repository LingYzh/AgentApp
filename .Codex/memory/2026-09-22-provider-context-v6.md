# Provider context v6

- `ProviderConfig.contextWindowOverrides` stores a positive token capacity per exact model ID.
  `contextWindowFor()` returns `null` when the user has not supplied a value, so callers do not
  mistake an unknown limit for zero.
- Reasoning defaults to `null`, which sends no reasoning field. `MINIMAL` remains serializable for
  old configuration and Gemini; the OpenAI-compatible and Anthropic editor exposes
  `none/low/medium/high/xhigh/max` without model-ID gating.
- OpenAI-compatible Chat Completions forwards explicitly configured `temperature` and
  `reasoning_effort` unchanged, and always sends:

  ```json
  {"stream":true,"stream_options":{"include_usage":true}}
  ```

  The upstream service decides whether a model supports a selected combination.
- Anthropic `NONE` sends `thinking: {"type":"disabled"}` without `output_config.effort`.
  Other strengths default to adaptive thinking plus `output_config.effort`. AUTO retains a manual
  `budget_tokens` request only for recognized Claude 3.7 and 4.5 model names; unknown aliases use
  adaptive. Users may explicitly select `ADAPTIVE` or `MANUAL` for gateways and newer aliases.
  Thinking with a non-none effort still omits temperature because the Messages API rejects that
  documented combination.
- Gemini remains model-family-gated because its official API uses distinct `thinkingLevel` and
  `thinkingBudget` request schemas.
  The [Gemini thinking guide](https://ai.google.dev/gemini-api/docs/generate-content/thinking)
  explicitly covers Gemini 3.8/3.7 Flash (no `minimal`), Gemini 3.6/3.5 Flash,
  Gemini 3.1 Pro, Gemini 3.5/3.1 Flash-Lite, and Gemini 3 Flash. The
  [image-generation guide](https://ai.google.dev/gemini-api/docs/image-generation) confirms that
  Gemini 3.1 Flash-Lite Image supports only `minimal` and `high`.
- Anthropic configuration follows its [extended-thinking guide](https://platform.claude.com/docs/en/docs/build-with-claude/extended-thinking)
  and [effort guide](https://platform.claude.com/docs/en/build-with-claude/effort): adaptive
  thinking is paired with `output_config.effort`, and thinking with a non-none effort cannot send
  `temperature`.
- `TokenUsage` preserves nullable unknown values separately from real zero counts.
  Output includes reasoning; Gemini sums `candidatesTokenCount` and `thoughtsTokenCount` to
  normalize the otherwise separate fields ([UsageMetadata](https://ai.google.dev/api/generate-content#UsageMetadata)).
  OpenAI parses final Chat Completions `usage`; Anthropic combines `message_start` input/cache
  usage with final `message_delta` output usage, including cache read/write tokens in input totals;
  Gemini maps `usageMetadata`; CUSTOM emits no manufactured usage event.
- Parser and request-shape coverage is in `ReasoningRequestTest` and `ProviderParsingTest`; config
  persistence coverage is in `ProviderConfigSerializationTest`. No paid provider call is used.
