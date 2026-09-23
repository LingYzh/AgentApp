# UI v3 list row refinement

- The active v3 prototype still renders the list page functions through `page() -> pageV2() -> pageV1()`. Its history, files, skills, and memory rows use the `.list-row` CSS: 16px vertical and 7px horizontal padding, 76px minimum height, 13px internal gap, 1px bottom divider, 15px title, and 12px description. The 42px icon tile has a 12px radius.
- Agents remain the prototype's `.list-card` exception: 18px corners, 16px padding, avatar/title/model at the top, description below, then a divider and tools summary plus start-chat action. Keep secondary edit/delete actions in the trailing menu and keep management selection available.
- `PrototypeListRow.kt` is the shared Compose implementation for divider rows and their icon, metadata, and overflow affordances. Per-row delete, rename, and export actions remain available through each row's more menu.
