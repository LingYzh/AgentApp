# 共享存储、删除工具和诊断日志

## 已核实的设备状态

- 用户报告 `Download/notes.txt` EACCES，而 App 新建的文件可以读写。
- 无线 ADB 只读检查：`MANAGE_EXTERNAL_STORAGE: default; rejectTime=...`。这表明 App 没有启用所有文件访问；不能据此声称 Android 按 basename 锁住 notes.txt。
- 按用户此前授权的共享存储读写范围，通过 `appops set --uid com.Ling.actant MANAGE_EXTERNAL_STORAGE allow` 启用授权，随后查询得到 `Uid mode: MANAGE_EXTERNAL_STORAGE: allow`。包级旧 reject/default 历史仍可显示，UID override 已是 allow。
- 没有读写 notes.txt 内容，没有创建/删除手机测试数据，也没有启动设备 UI 自动化；具体读写效果由用户复测。
- 官方存储边界：[Android 所有文件访问](https://developer.android.com/training/data-storage/manage-all-files)。会话 Auto 不能代替 Android 系统授权。

## 实现

- 动态环境在后续请求追加当前 Android 共享存储授权状态；EACCES/EPERM 工具错误说明系统拒绝，并阻止“按文件名保护”无证据推断和反复改模式试探。会话权限弹窗也展示独立的系统授权状态。
- `delete_file` 只接受单个普通文件。Readonly/Plan 硬拒绝，包括计划文件；Accept Edit 逐次弹窗确认、不能永久放行；Auto 免 App 内审批。所有模式仍遵守范围与系统权限，删除前重新检查模式、目录、目标及元数据。
- 不递归删除目录，不跟随文件符号链接执行删除。显式 Agent 工具白名单仍生效：已有 Agent 如果没勾选新工具，需在 Agent 配置中勾选“删除文件”；空列表表示默认工具全集。
- `files/logs/runtime.jsonl` 本地 JSONL 元数据日志，最多轮转为 `runtime.1.jsonl`、`runtime.2.jsonl`，每个约 1 MiB。
- 记录启动、请求/工具/审批开始结束、网络状态/耗时、错误类别、失败路径的可读写状态及系统授权；不记录请求头、URL、API key、消息正文、文件正文、shell 命令内容或思考签名。未捕获异常只记录异常类型与栈帧，不记录 message。
- 默认自动启用，设置页说明路径、用途与保留范围；日志读取失败不会阻止 Agent。日志不参与正常备份清单。

## ADB 读取

调试构建首次启动后产生日志。选定一个无线连接，避免同一设备重复发现：

```powershell
& 'E:/Android/Sdk/platform-tools/adb.exe' -s 'adb-912606610730-qMEwA5._adb-tls-connect._tcp' shell run-as com.Ling.actant ls -l files/logs
& 'E:/Android/Sdk/platform-tools/adb.exe' -s 'adb-912606610730-qMEwA5._adb-tls-connect._tcp' exec-out run-as com.Ling.actant cat files/logs/runtime.jsonl
```

发布版如果关闭 debuggable，run-as 不可用；这次交付的是 debug 构建。最终验证结果见本轮验收文档。

## 交付验证

- `assembleDebug testDebugUnitTest lintDebug` 成功；155 项 JVM 测试，154 通过、0 失败、1 跳过（Windows 符号链接创建失败，未验证该用例）。Lint 0 错误、37 警告、3 提示。
- `git diff --check` 通过；未找到需更新的 CLAUDE.md。
- 已 `adb install -r` 覆盖安装 NX809J，返回 Success；安装后再次确认 UID 的 MANAGE_EXTERNAL_STORAGE 为 allow。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256：`4EED1B64480FCD1742D3A80910EB970CFA4EBAAB3AC8B57DB36F1D4D85643275`。
- 实机验收清单：`.Codex/test-fixtures/storage-streaming-reasoning.md`；文件访问复测与实际流式观感仍由用户执行。
