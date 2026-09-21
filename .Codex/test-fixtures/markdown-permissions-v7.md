# Manual checks for this build

1. Provider settings now expose “最大输出 tokens”. The affected DeepSeek configuration uses
   Anthropic: blank now means 65,536 instead of 8,192. A larger explicit value can be supplied
   if supported by that endpoint. Ask for a long answer at max effort; if upstream still reaches
   a cap, the conversation should visibly say it was truncated, and logs should include why.
2. Ask for multiple paragraphs, a long code block, a wide Markdown table, a nested list, and a
   quotation in one streamed response. Previous paragraphs should stay stable as new ones arrive.
   Check code copy/wrap controls, table horizontal scroll, text selection, links, light/dark mode.
3. Keep Download/DCIM as the session scope and choose Auto or Accept Edit. Save/search/delete
   a memory and save/read a Skill using their dedicated tools: the external scope should no
   longer block them. Generic file tools remain scoped. Arbitrary commands still require clearing
   the explicit directory scope because the app has no shell directory sandbox.
4. In Plan/Readonly the managed resources remain readable but cannot be changed. Accepting a
   plan in Auto restores their write access. The input toolbar indicates the active scope count.

No device test data or automated UI actions were created by the development agent.
