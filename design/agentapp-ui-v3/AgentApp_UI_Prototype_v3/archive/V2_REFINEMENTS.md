> 历史记录，非当前实施规范。第7项外层步骤卡片已被v3无外框工具行取代；以根目录DESIGN_SPEC.md和V3_REFINEMENTS.md为准。

# AgentApp UI v2 · 九项迭代与原生实施约束

基线：LingYzh/AgentApp `master@2c947234ab0d29ede6e999193717288363195b7a`；2026-09-22 再次读取确认。
本交付修改的是交互原型和设计文档，没有修改仓库，没有编译 APK。数据与执行结果均为演示。

## 1. 动效不是流式正文动画

| 交互 | 原型规格 | Compose 对应方向 |
| --- | --- | --- |
| 页面进入 / 返回 | 260ms，约 20dp 水平位移 + 透明度 | NavHost enter/popEnter；对应方向相反 |
| 页面退出 | 180ms，约 12dp 位移 + 淡出 | exit/popExit |
| 底部面板 | 280ms 进入、180ms 退出，轻位移 | 现有 ModalBottomSheet 转场；不要叠两层动画 |
| 快速思考面板 | 280ms，12dp 位移、0.97 → 1 缩放 | Popup/Surface 进入退出 |
| 工具详情 / 普通折叠区 | 240ms 高度变化 | AnimatedVisibility / animateContentSize |
| 顶部反馈 | 200ms 进入、180ms 退出，布局占位 | 独立反馈 host + AnimatedVisibility |
| 按下 / 松开 | 110ms 轻缩放，0.97 | InteractionSource + 轻量状态动画 |
| 主题切换 | 180ms 轻过渡 | animateColorAsState 或单次过渡 |

时间和距离是本设计取值，不是声称 Android 官方要求的固定常数。默认 easing：cubic-bezier(.2,.8,.2,1)。动画不可成为业务锁：不要等动画结束才允许取消网络。

原型评审栏有“重播交互动效”和关闭开关，遵循 prefers-reduced-motion。原生实现读取系统动画偏好；减少动态效果时直接完成最终状态。流式正文不套 AnimatedContent，不为每 token 淡入，不反复重播整条消息；保留现有 120ms 后台采样和已解析内容可见策略。保持 stable key 与上滑停止追随。

## 2. 深色主操作始终白字 / 白图标

分开“强调文本色”和“实心操作背景”，不要简单把亮陶土主色配白字。

| 用途 | 浅色 | 深色 |
| --- | --- | --- |
| 实心操作背景 / primary / actionContainer | #A34F36 | #A9563D |
| onPrimary / onAction | #FFFFFF | #FFFFFF |
| 链接、轻按钮、强调文字 / contentAccent | #A34F36 | #E6A086 |
| 装饰品牌种子 | #D97757 | #D97757 |

发送、停止、实心确认按钮，以及其中 SVG / Icon 使用 onAction。禁用时降低背景/整体强调，不自动改成黑字。危险实心按钮单独用更深的 errorActionContainer，白色前景；错误说明仍用适合深色背景的 error 文本色。

## 3. 上下文按现有八类分段

直接复用 ContextOverview.segments、estimatedTokens、maxTokens 及当前 ContextUsageUi.kt 的颜色，不能从 UI 文案重新分类。

| key | 名称 | 颜色 |
| --- | --- | --- |
| system | 系统提示 | #6B91CA |
| tools | 工具定义 | #9C82C6 |
| environment | 环境信息 | #8995A5 |
| user | 用户消息 | #50A78F |
| assistant | 助手消息 | #D3A05B |
| results | 工具结果 | #CF7F8B |
| attachments | 附件 | #5BA9BD |
| summary | 压缩摘要 | #A0AF70 |

令 T 为各分类之和，C 为明确配置的容量。已知容量用 D=max(C,T,1)，每段宽度 tokens/D，余量 max(D-T,0)/D；超过容量不能裁掉某些类型，数字仍明确显示超额。未知容量用 D=max(T,1)，整条只表示已用组成，不显示容量百分比。零值不制造最小宽度，不能为了“看得到”篡改比例。图例 percentage=tokens/T，区分它与已占总容量比例。

默认演示 T=34,280、C=200,000，分段之和占 17.14%；压缩演示 T=12,400，占 6.2%，此时出现 summary。小环也使用同一份分类数据。服务端用量单独展示，不能把本地八类比例冒充服务端实际 token 分类；未报告不等于 0，缓存不重复相加。

## 4. 模型项的原生文件能力

模型名称下显示图片、PDF、音频、视频的线性图标与小标签，来源标为接口发现或手动覆盖。用 capabilitiesFor(modelId) 的有效值：手动覆盖 > discovery；未声明不暗示支持。一个 Provider 内不同 modelId 不能共用同一组硬编码能力。

原生媒体输入与 workspace 工具读取是不同路径。图标不代表 Android 已授权、不代表附件能实际发送，也不能替代现有模型能力/协议校验。原型只有演示目录，禁止将其中的 Sonnet / Gemini 能力样本抄成真实兼容表。

## 5. 离散思考滑块 + 详细选择

入口：composer 控制栏 → 小型滑块面板 → 点击滑块上方“中 · medium”等文字 → 完整选项 popup。模型选择仍在原来独立入口，滑块不混入模型名。

选项由 ReasoningSupport.efforts 提供，保留返回顺序。仅作为所有可能值的字典：none=关闭、minimal=极低、low=低、medium=中、high=高、xhigh=很高、max=最大。当前不支持的值不能绘制为可选刻度。无选项隐藏滑块；单选项不进行除零归一化。

`reasoningEffortOverride=null` 表示跟随 Provider 配置；若 Provider 也是 null 才表示不发送可选字段。`none` 是明确要求关闭。跟随一个已配置的 medium 时，滑块显示 medium 但标题标明跟随，拖动提交后转为会话覆盖。默认未知时不把 none 画成已选择，轨道无有效选择提示；重置恢复跟随，不发明中档默认。

拖动中仅更新显示，松手 onValueChangeFinished 提交；不要每帧写 config 或调用模型。滑块使用 48dp 操作区，白色 thumb；可键盘方向键/Home/End操作，TalkBack/stateDescription 含中文名和原始值。文本选项始终可作为精确选择方式。

详细选项显示：中文名 + canonical wireValue + 当前 adapter 的字段预览。示例 adaptive Anthropic 为 output_config.effort，关闭为 thinking.type=disabled；OpenAI 兼容为 reasoning_effort；示例 Gemini level 模式实际位于 generationConfig.thinkingConfig.thinkingLevel。手动预算模式必须调用现有预算映射，不能将字符串直接写入 thinkingBudget。CUSTOM 保持原样。不在 UI 重新实现协议判断。

执行中选择只影响下一次模型请求，不能声称改变当前在途请求。切换模型后不支持的 override 沿用当前 ChatViewModel 的处理与提示，不引入第二套持久状态。

## 6. 反馈横幅上移，且占布局空间

普通成功/提示在当前页面顶栏下方出现，推动正文，不覆盖底部输入或确认。打开 modal 时，提示 host 移至当前 modal 标题下方，不能落到 scrim 后，也不能叠在 sheetFooter 上。全局同一时刻只有一个反馈 host。

普通提示约 4.5 秒后消失，可关闭；鼠标悬停/聚焦暂停消失。错误应保持可读并允许关闭，任务失败详情仍放在对应内容中。原生时长采用无障碍建议时长，不强制 4.5 秒。权限审批与危险动作仍为原有明确确认，不降级成自动消失 toast。

## 7. 工具参数与返回原地展开

父级“完成了 n 个步骤”展开工具列表；每条有清楚的“详情”文字和箭头。点开后就在原地显示工具名、callId、已有状态、参数、原始结果；代码区可复制和局部滚动/换行。无对应结果显示等待，而非伪造成功。状态颜色辅以文字。

数据取 ToolCallInfo.argumentsJson 与对应 ChatMessage 的 content/isError/toolCallId；presentTool.summary 只作摘要，不替换原始返回。没有计时/完成数据时不捏造耗时。截图时间和 ID 均为明确的 demo 值。工具输出按纯文本/安全结构渲染，不执行 HTML，也不将返回中的命令变成自动执行按钮。

expanded 状态键包含 conversationId/messageId/toolCallId，主题切换、流式刷新或父级折叠后仍可恢复。不要更改 providerBlocks、replay、权限批准、cancel 链。长结果按需渲染，可分段展开，不能无标记截断后声称完整。产物/Diff入口可导航，但“查看调用详情”本身不打开新页面或 modal。

## 8. Provider 卡片简化

普通模式：左侧 RadioButton = 全局默认；卡片主体和右箭头 = 编辑。这两个操作有互不重叠的触控区，点击单选不能同时导航。

管理模式：RadioButton 位置换为 Checkbox，点击主体也只切选中；多个项目能选中，默认 Provider 不受影响。退出管理还原单选。删除默认配置后的 fallback 仍由既有数据层决定，UI 不能私自选择另一项。

移除同卡片上的“设为默认”按钮、“编辑配置”按钮及同义“...”按钮。列表顶栏菜单保留管理、导入、导出等不同用途；连接测试和拉取模型保留在编辑页，不删功能。

## 9. Markdown 统一阅读组件

新建第 25 页是排版验收样本，不加入正式 App 导航。正文/文件/计划/记忆在正式实现复用统一样式 token；不把阅读页的大标题比例直接套到 chat 每个段落。

正文 16sp、约 29sp 行高；h1/h2/h3 为 27/21/18sp，段落下间距 17dp，h1/h2/h3 标题前分别 29/31/25dp；行内代码 0.88em；英文/长路径可折行，中文正常断行，不强行加字间距。引用用细陶土侧线 + 中性色底；列表保持悬挂缩进、嵌套层级，任务框只是文档状态，不是授权按钮。

代码容器 13dp 圆角、语言/操作栏、等宽正文 13sp/约24sp。默认局部横向滚动，切换自动换行；保留原文复制，语法高亮只做呈现。未知语言退回纯文本，不拖慢流式。宽表格在容器内部横向滚动，表头/分隔线与数字对齐清晰，不压成几条窄列，不撑开整个页面。

现有 MarkdownDocument/CommonMark 模型、SelectionContainer、链接 scheme 白名单、code copy/wrap 与原生 Compose 架构必须保留。原型 JS 只覆盖展示样本，不是完整 CommonMark/GFM 实现，不能复制替换生产解析器。不新增未经实现的 LaTeX/Mermaid/HTML 执行或远程图片承诺。

## 九项验收入口

| 请求 | 原型入口 | 最小验收 |
| --- | --- | --- |
| 动效 | 右侧“重播交互动效” | 进出、返回、折叠都有过渡；reduce 可关闭 |
| 白色按钮内容 | 深色组件页 / 发送 / 确认 | 前景 #FFFFFF，实心底色保持可读 |
| 彩色上下文 | composer 约17% → 详情 | 各色实际比例、unknown、summary |
| 模型文件类型 | 模型 picker | 图标/标签与有效能力对应 |
| 思考滑块 | 控制栏 → 点击标题 | 松手提交、每档字段、null≠none |
| 顶部消息 | 切模型、切默认、组件“消息提示” | 占位不遮挡 bottom bar |
| 工具详情 | 完成对话 → 查看过程 → 详情 | 参数/返回原地展开 |
| Provider 单多选 | 模型配置 → 单选 / 管理 | 单选默认和多选互不影响 |
| Markdown | 排版样本 / 文件预览 | 长代码/表格局部滚动、原文复制、流式不闪 |

原生未测项目：真实 IME/TalkBack、动画缩放、多指/返回手势、配置重建、网络/Provider 和真实文件操作。由 Codex 本地构建并交用户实机验收。
