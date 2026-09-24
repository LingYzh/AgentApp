# 工具记录展开闪退与设置样式统一

- 用户报告部分 device_observe/device_action 记录点击闪退，明确要求不读取对话正文。仅从实机 runtime.jsonl 选取时间、事件名、工具名、异常类及 frames；未打开 conversations 文件、正文或工具返回。日志记录三次 IllegalArgumentException，堆栈均指向 Compose Constraints/SizeNode。
- 根因：InlineCodePanel.kt 的 ToolRecordPanel 根据原始单行长度直接指定宽度。设备观察 JSON 常为超长单行，超过 Compose Constraints 位宽限制。使用纯合成 JSON 的模拟器用例在旧代码准确复现：Can't represent a width of 898020 and height of 0 in Constraints，调用栈与手机一致。
- 修复同时覆盖 ToolRecordPanel 与 InlineCodePanel：显示层每段最多512 UTF-16单位，避免拆开 surrogate pair；布局宽度封顶8190px，达到上限允许折行。超长行显示分段提示，原始存档、复制和导出不变，未删减任何字符。
- 新增 CodeDisplayLinesTest 覆盖长 JSON、Unicode 和原有空白；debug 非导出 ToolRecordTestActivity + ToolRecordInstrumentedTest 使用纯合成数据，在 density3/fontScale1.5 下通过真实工具记录行点击展开、换行/取消换行、高度切换与完整复制断言。回归先红后绿，最终构建再次通过；不依赖真实用户数据。
- Herdr agy 保持 Gemini 3.8 Flash High，负责明确 UI 范围。新增 SettingsSectionCard 复用 ExpressiveTokens.CardShape/MaterialTheme 容器、边框、字号；DeviceControlScreen 分总开关、无障碍、Shizuku、任务栏目，连接/截图/授权状态及错误分别归位，不再底部聚集。WebSearchScreen 分服务启用、连接参数、测试搜索、网页读取，保存反馈在参数区，测试结果在测试区。
- ProvidersScreen 仅移动原 testResult 卡片到“获取模型列表 / 测试连接”按钮及局部信息下方、模型列表前，未改变测试逻辑与配置语义。
- 验证：assembleDebug / assembleDebugAndroidTest、5项针对性 JVM 测试、lintDebug 通过（0 errors/60 warnings/5 hints）；模拟器回归1项同时覆盖两类工具。设备控制与网络搜索模拟器截图已视觉检查，位于忽略目录 build/device-control-qa/device-styled.png、search-styled.png。本轮不重跑全套测试，既有符号链接测试限制仍见前文。
- 实机只读取诊断元数据并覆盖安装，不点击界面、不读取正文。UI与历史记录真机复测由用户执行。无相关 CLAUDE.md；未提交，保留既有 IDE 修改和 jdk21.zip。
- 最终 NX809J 覆盖安装 Success，保留数据；APK SHA256 `8475A2BBB17048463EB9E74331BB90B4525041F29068BE5A4718FA4E13929A4E`。
