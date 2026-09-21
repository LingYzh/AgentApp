# 托管记忆与 Skill 根的权限边界（v7）

## 用户要求与实现取舍

- 会话的显式目录范围用于普通文件工具与 shell，不能让 `save_memory`、`delete_memory`、`save_skill` 等应用托管资源失效。
- 托管记忆与 Skill 仍遵守 Agent 工具白名单，以及 Readonly/Plan 的写入门禁；本次没有放宽这些模式。
- Auto 只跳过应用内确认，不能绕过普通文件范围或 shell 限制。

## 落地

- `FileStore` 将 `memory/`、`skills/` 固定为可信应用根的直接 canonical 子目录。可信应用根本身可以有系统路径别名（Android 常见的 `/data/user/0` 与 `/data/data`），但专用根或其直接目标不得被符号链接重定向。
- 记忆 ID 只接受单段受限标识符；Skill 显式拒绝 `.`、`..`，并对每个专用文件校验规范父目录。未知 `delete_memory` 不再进入存储删除操作。
- 删除前拒绝 Skill 树中的符号链接；记忆索引、记忆正文和 `SKILL.md` 通过同目录临时文件替换，避免写入已有硬链接的另一名称。
- 旧版单文件 Skill 迁移、ZIP 导入覆盖和导出也先经过托管根及链接检查；ZIP 解包路径比较使用根路径分隔符边界。
- `save_memory` 新增可选已有 `id`，可直接更新记忆；无 ID 时仍新建，有效 ID 来自检索结果，未知 ID 明确拒绝。

## 参考原文

- Codex 官方源码：[`codex-rs/memories/README.md`](https://github.com/openai/codex/blob/main/codex-rs/memories/README.md) 的 “Phase 2: Global Consolidation” 说明 memory root 是独立的受控工作区；其内部 consolidation agent 使用本地写入、无审批和无网络（当前 main，约 256–280 行）。
- Codex 官方源码：[`codex-rs/core-skills/src/loader.rs`](https://github.com/openai/codex/blob/main/codex-rs/core-skills/src/loader.rs) 把 Repo、User、System Skill 配置为不同的 `SkillRoot` 与文件系统范围。
- Codex 官方 SDK：[`sdk/python/docs/api-reference.md`](https://github.com/openai/codex/blob/main/sdk/python/docs/api-reference.md) 的 Sandbox 段落说明 `workspace_write` 同时包含工作区和配置的 writable roots（当前 main，约 370–385 行）。
- 用户指定的研究仓库：[`Austin1serb/Anthropic-Leaked-Source-Code`](https://github.com/Austin1serb/anthropic-leaked-source-code)，固定提交 `3fa80a4417103f205c97e7468337703094c0e1`；此前已核对 `utils/permissions/permissions.ts` 与 `utils/permissions/filesystem.ts`，其普通路径检查和内部特殊位置规则分层。该仓库不是官方规范，只作为实现参考。
