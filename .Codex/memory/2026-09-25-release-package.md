# Release 试装包

- 用户确认当前功能可用，要求提交项目并试出 release APK。功能提交 `292f767`（设备控制、设置整合/样式、工具记录闪退修复、测试与交接）；已有 `.idea/gradle.xml`、暂存 `.idea/inspectionProfiles/Project_Default.xml` 与 `jdk21.zip` 保留，不纳入功能提交。未推送远端或创建公开 Release。
- 构建命令：JDK21 下 `gradlew.bat assembleRelease lintRelease --console=plain`；成功，release lint 0 errors、56 warnings、5 hints。遵循原配置 `isMinifyEnabled=false`，本次不引入 R8 混淆。
- Gradle 产出 `app/build/outputs/apk/release/app-release-unsigned.apk`。项目无独立发布签名；为遵循保持签名且保留数据覆盖安装，用当前本机 Android debug.keystore 经 build-tools36 apksigner 对 release 产物签名，没有修改 Gradle 签名配置或提交密钥。这是不可调试的 release 构建，但签名仍为本机测试证书，不应称为正式发布证书签名。
- 可安装包：`build/releases/UAH-1.0-292f767-release.apk`，versionName1.0/versionCode1、minSdk24/target36、applicationId保持不变；APK与密钥均未入 Git。
- SHA256：`999A685DB3591E32475FE2CAD4254D3445933CD308D0B4502B10F9836D6419D8`。
- 签名证书 SHA256：`ebf0499def3123911d7bd9b307cade2e43341aca5bf442b4ee5f4eb2baa5ab21`，与前版 debug APK 一致；apksigner v2/v3 verify通过。aapt确认无 application-debuggable 标记、无 DeviceTestActivity/ToolRecordTestActivity。
- 模拟器覆盖安装与冷启动验证；手机只覆盖安装，由用户测试。Release 不支持 debug 的 adb run-as，后续如需读取私有运行日志，须先同签名覆盖安装 debug 或提供应用内日志导出。
