# Context / reasoning manual acceptance

1. Edit an OpenAI-compatible provider with an arbitrary model alias. Confirm none, low, medium,
   high, xhigh, max remain available. Set its context capacity, save, and revisit it. Switching
   the selected model should show that model's own capacity (blank when not configured).
2. In a conversation change effort before sending, and during a tool loop. The current HTTP
   request continues; the next request uses the new effort. Return to “follow model” to restore
   the provider's default. Unknown/upstream-rejected effort should produce a visible error.
3. Open context details. The category colors in the stacked bar match the legend. Verify it is
   scrollable on a narrow screen and the compression button is reachable. Missing capacity is
   shown explicitly, with no invented percentage. Last actual input/output/cache counts are
   separate from the category estimates.
4. In a long idle main conversation, start manual compression. Original message bubbles remain.
   The active history count/estimate changes and the next reply remembers important goals and
   paths. Recent tool calls/results and attachments still work. Compress again after more turns.
5. Cancel compression, or provoke a provider error: original active context remains usable.
   Change permission after successful compression: new mode must take effect at the next request.
6. Edit/delete a historical message after compression: the app must discard the now-stale
   summary. Child conversations remain read-only and cannot change effort or compress context.

These steps are for user execution; no device UI automation or paid provider calls are part of
the build checks.
