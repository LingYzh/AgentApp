# AgentApp UI v3 · Codex原生实施交接

## 任务

将本目录完整原型的视觉和交互移植到现有Kotlin/Jetpack Compose App。不要换WebView、JS运行时或重写harness。先读取项目AGENTS/相关CLAUDE/当前HEAD与.Codex/memory，再读本目录README、V3_REFINEMENTS、DESIGN_SPEC、MOTION_MATRIX、SCREEN_MAP、design-tokens。

研究基线为 `2c947234ab0d29ede6e999193717288363195b7a`。若本地HEAD不同，先核对新增能力，不回退用户实现。界面名不等于新的业务实体；所有fixture和评审代码从正式构建剔除。

## 优先级与分批实施

### A. 公共样式、布局与局部状态

接入语义配色与排版，主要操作在暗色下保持白字/白图标；不让动态壁纸色覆盖主题。按钮Icon/Text居中Row，chevron置右。实现活动topbar下的TopFeedbackHost，与现有错误/消息来源对接。

以稳定key保存状态；列表/表单同页变更不能通过重建整页来“获得动画”。所有控件位置、选中、出现消失参考MOTION_MATRIX。避免modifier组合重复驱动同一高度，避免NavHost与Screen双重进场。

### B. 工具行与内联Shell/Diff（本轮核心）

重构ChatActivityCards.ToolActivityRow及其父级呈现：无外框、无外层步骤卡、无额外水平padding。命令完成收起仅“运行了命令”；编辑收起“已编辑 文件名 +N −M”。上下文顺序保持原有消息/调用顺序。

展开Shell使用真实command和对应原始结果；原地Diff直接使用FileChanges.diff，不再必须进入FileDiffDialog。保留只读、长结果/长行滚动、原文复制/换行、完整/裁剪说明。不要显示空结果成功、未知耗时或伪造退出码。

新增UI稳定状态键conversationId/messageId/toolCallId；主题/流式刷新/父布局重组不丢展开和横向滚动。展开/收起驱动真实布局高度，可中断/反向操作，最终意图胜出。

### C. NEW_CHAT草稿起始页

当前NavHost以CONVERSATIONS起始，CHAT需要真实ID，create()立即落盘。新增独立NEW_CHAT路由与草稿状态，设置为冷启动默认。统一全局“新对话”和“用Agent开始”的入口，但不先生成空会话。

首次发送原子/幂等地创建会话、保存首条消息、消费草稿并移交执行。不能先在新页ViewModel启动任务再因导航销毁其scope导致取消。保存或导航中断后恢复应避免重复首发；重复点击只有一个会话和请求。

保留历史入口。后台返回、配置重建、权限页/文件选择器返回、已恢复导航栈和明确会话Intent不得重置到新对话。草稿附件清理由其所有权决定，不能删除已引用文件。无Provider引导配置，不产生空记录。

### D. 保留v2控制与Markdown

模型项有效原生能力图标、思考独立滑块+详细原始字段值、8类上下文、Provider radio/checkbox分工、配置字段/测试/迁移、正文Markdown全部按DESIGN_SPEC验收。不要只实现26页样本而保留真实聊天旧工具卡片。

Markdown保留现有原生分块/SelectionContainer/120ms解析采样和旧内容可见策略。不对每个token淡入或整条消息反复尺寸动画。长表/代码局部滚动，复制原文，链接安全scheme照旧。

### E. 原生回归与交付

每批完成静态/单元检查，更新.Codex/memory，必要时同步项目说明。按项目已有命令运行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

输出APK路径、修改文件、测试结果、已知未解决项以及最短实机检查步骤。设备测试依AGENTS由用户执行；未另行授权不要连接或点击手机。不要自动push、发布release或修改仓库设置。

## 不得破坏的不变量

1. applicationId=`com.Ling.actant`与namespace=`com.example.myapplication`不同，不顺手统一；项目Kotlin/Gradle依赖先以仓库现状为准。
2. 工具白名单空列表表示全部，子代理不可再次委派；强制子代理模型优先级/ModelResolver顺序不改。
3. 权限模式、目录边界、Android UID限制、计划审批、命令自动放行的限制优先规则保留。UI不能授予权限。
4. CancellationException贯穿网络/工具/子代理；删除会话/消息不等于撤销文件。离开页面对任务归属的影响必须明确。
5. Provider可选参数null、reasoning none/null、能力手动覆盖与发现、custom模板、providerBlocks重放、媒体和上下文语义保留。
6. 导入preview/冲突更新与副本、缺Key保留本地Key、无Key排除附加头、ZIP/路径校验、自动备份规则不改。
7. 不新增root/Shizuku/常驻后台/自动截图/远程商店等未实现能力。没有真实结果不展示成功，无快照不展示精确Diff。

## 原生验收清单

- [ ] 360/412逻辑宽度、浅深色及系统大字体；图标文字居中，不裁切控制栏。
- [ ] 启动新草稿、不发送反复退出不增历史；首发连点、保存失败、配置重建和任务交接正确。
- [ ] 现有会话后台返回、选文件返回、权限设置返回不丢会话/草稿/执行状态。
- [ ] 命令完成/进行中/审批/失败/中止/空输出；展开不执行；复制与换行源文本准确。
- [ ] Diff行号、+/-、缺快照、previewOmitted、fallback、长行和大文件预算；无外层卡片。
- [ ] 快速展开收起/返回/换弹层，无残留点击层，滚动和选择不重置；减少动效终态一致。
- [ ] UI局部状态变化有过渡，不只是页面进入；输入和取消不等待动画，流式正文不闪烁。
- [ ] Provider单选与多选互不干扰；切换、测试、拉取、迁移保留实际功能。
- [ ] 顶部消息占位，不遮住输入与审批按钮；模态内提示位于活动模态。
- [ ] TalkBack焦点、展开状态、图标描述、触控48dp；IME/预测返回/字体放大实机检查。

浏览器原型的54项自动检查与94张截图只是呈现层参考，不代表本原生验收已通过。
