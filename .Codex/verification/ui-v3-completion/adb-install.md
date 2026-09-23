# APK 安装记录

- 时间：2026-09-23 14:02（Asia/Shanghai）。
- 设备：NX809J，ADB serial `912606610730`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- SHA256：`83131BEB00056A472314CA062FDF403DB78597ABBD0B93DE866059FBC39CC8FA`。
- `install -r` 返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
- 按用户明确授权执行 `uninstall com.Ling.actant` 后重新 `install`，两者均返回 `Success`；卸载清除了旧版应用数据。
- `dumpsys package com.Ling.actant` 确认 versionCode=1、versionName=1.0、lastUpdateTime=2026-09-23 14:02:17。
- 未启动应用或操作界面，实机验收由用户执行。
