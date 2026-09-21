# Native Markdown and truncation diagnostics

- Device metadata showed `deepseek-flash` using ANTHROPIC, not OpenAI Chat Completions.
  MAX returned 30,701 thinking characters, no body, in ~35 seconds. The old adapter sent
  `max_tokens=8192` for adaptive thinking; the old log did not record stop reason, so the
  exact termination reason of that historical call cannot be proven from that log alone.
- Anthropic now defaults to 65,536, with an editable positive `maxOutputTokens`. OpenAI and
  Gemini omit optional caps by default. The engine classifies token-limit stops as visible
  incomplete responses, retains received text/thinking, and does not execute tool calls from
  truncated responses. Logs record stop reason, configured/effective cap, and current-request
  reported output usage (never request content).
- Replaced Markwon AndroidView/TextView rendering with CommonMark -> immutable semantic
  blocks -> native Compose Text. The existing CommonMark 0.13 grammar remains; removed
  Markwon renderer dependencies. No WebView, HTML execution, or remote image fetching.
- Streaming samples every 120 ms, parses off-main, retains old content until ready, and keeps
  block identity by position/type. Initial static text renders at its actual height immediately
  to avoid blank/reflow when old messages enter the list. Completed blocks remain structurally
  equal while the tail changes. This removes whole-message `setMarkdown`/TextView resets.
- Typography: 16sp/26sp paragraphs, restrained headings, paragraph spacing, nested lists and
  quote rules. Code has a language header, copy, and wrap toggle; tables use fixed columns and
  horizontal scrolling. Native link annotations preserve selection and only open normal web,
  mail, or phone links on user action. Raw HTML is literal text, except inline br line breaks.
- JVM parser regressions cover stable prefixes, open fences, escaped table pipes, alignments,
  nested lists/tasks, and reference links. Device visual testing remains user-run.

## Delivery

- `assembleDebug testDebugUnitTest lintDebug` succeeded; 185 tests, 182 passed, 3 Windows
  symbolic-link environment skips; lint has 0 errors, 39 warnings, 3 hints.
- ADB overwrite installation succeeded on NX809J. Only existing diagnostic metadata and relevant
  permission state were read from the phone; no phone UI automation or test data creation.
- APK SHA-256: `C9258DFE8CAB6659F63B8AA6FB5FEB1C04F4FB31D50DF2F895A6976E550CDC9D`.
