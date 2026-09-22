# UI v3 第五轮：工具详情、编辑页、设置与系统栏

## 用户要求与实现

- 所有普通工具展开后使用与 Shell 共用的 ToolRecordPanel，入参和回参位于同一底板，保留原始内容、缺失与空返回的区别、等待/执行/取消/失败状态，以及复制、换行、限制高度。
- 文件修改仍优先呈现已保存的 Diff；可展开“查看入参与回参”查看原始工具记录，不用当前文件伪造历史。
- Agent、Provider、Skill 编辑页使用外置字段名、12dp 圆角填充输入框、22dp 页面边距和顶部文字保存。Provider 的能力、生成和高级连接设置可折叠；不恢复默认思考强度配置。
- 文件详情增加阅读、源码和已保存的最近变更视图；编辑中使用独立的源文件编辑器。
- 记忆编辑、导入确认、导出配置使用底部面板，正文可滚动，操作区保留在安全区域。
- 设置分为外观、执行、设备与存储、数据等分组；命令管理和备份迁移有独立内容页，独立迁移入口前往相应管理列表。
- MainActivity 在 Compose 主题变化时同步 SystemBarStyle，手动浅色/深色覆盖系统状态栏及导航栏图标模式；跟随系统继续响应 Android 配置。

## 保留的约束

- 工作目录和额外目录并集只限制文件工具；Shell 从工作目录启动并沿用权限模式审批。
- 配置迁移仍先预检、冲突确认再写入；完整恢复仍创建恢复前备份。
- 没有接入原型中的演示数据、重置按钮或未实现的设备能力。
- 手机 UI 交互由用户验证；本轮只准备构建、静态/单元检查和授权覆盖安装。

## 验证

- `testDebugUnitTest assembleDebug lintDebug` 最终全部通过。JVM 225项：222通过、3跳过、0失败；lint 0错误、39警告、3提示。
- 本轮引入的 ModifierParameter 警告与数值状态提示已修正；代码 diff-check 通过（Git 提示现有 CRLF 归一化）。
- 无线 ADB 覆盖安装成功，设备 lastUpdateTime `2026-09-22 23:14:39`，保留应用数据。
- APK `app/build/outputs/apk/debug/app-debug.apk`，SHA256 `F6BCA270255D983A15521E3665C69DCEE00CC34BFCAD486ECF88714CA6FBAD9D`。
- 手动验收清单：`.Codex/test-fixtures/ui-v3-editors-settings-and-system-bars.md`。未执行手机 UI 点击或创建测试数据。
