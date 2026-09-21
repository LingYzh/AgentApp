# Conversation context and manual compaction

- `Conversation.reasoningEffortOverride` is read at each model request, including subsequent tool
  rounds. Null follows provider configuration; `NONE` is an explicit setting. Changing it does
  not rewrite system text, historical messages, or tool schemas, or cancel the current request.
- `ContextWindows.replay()` is the single history projection for engine calls and attachment
  preflight. A successful manual compaction prepends one stable summary and filters covered IDs.
  Excluded failed messages remain excluded. Original conversation history stays viewable.
- Context categories are local heuristics, not an upstream tokenizer or billing statement.
  Media counts are intentionally approximate; image resolution, PDF pages, provider processing,
  and protocol overhead can change actual consumption substantially. Last provider usage is
  separately timestamped and labeled with its model. Cache counts are subsets of input totals.
- `ContextCompactor` works on a snapshot, calls the selected provider with text-only transcripts
  and no tools, summarizes bounded chunks sequentially, and carries the prior summary forward.
  Referenced attachments are not read again. Failure/cancellation/empty or truncated output
  does not replace context. A non-shortening summary is rejected.
- Compression boundaries retain recent messages and never split parallel tool calls, results,
  or tool-origin attachment messages. The latest real environment message survives in place;
  future mode changes append normally rather than changing the compacted cached prefix.
- Manual compression intentionally changes the request prefix once. Ordinary subsequent
  permission changes remain append-only. Editing/deleting history discards compaction and
  previous usage to avoid summaries retaining user-deleted information.
- Scope: manual compression only, no automatic threshold trigger. User-defined model context
  capacity drives the UI; unknown capacity stays unknown. Summary quality depends on the model.
- Regression coverage: `ContextCompactionTest` and `AgentEngineTest` cover transactional failure,
  cancellation, repeated compaction, tool boundaries, excluded attachments, stable continuation,
  live effort changes, and usage persistence.

## Delivery validation

- `assembleDebug testDebugUnitTest lintDebug` succeeded. 168 tests: 167 passed, one Windows
  symlink-creation test skipped. Lint: zero errors, 37 warnings, 3 hints.
- ADB overwrite installation succeeded on the connected NX809J. No phone UI tests or paid
  provider requests were run.
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `AB2DCF48BF71F5EACFC7D60A9A03B1C37A30EB586017A255A992797ECE8DC927`
