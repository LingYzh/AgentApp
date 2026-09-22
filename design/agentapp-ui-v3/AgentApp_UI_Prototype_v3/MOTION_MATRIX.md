# AgentApp UI v3 · 动效覆盖矩阵

这是本方案的设计取值，不是 Android 强制规范。目标是让用户看懂连续变化，而非让操作等待。浏览器的 CSS/WAAPI 只是参考，原生使用现有 Compose 版本可用的动画 API，不引入 WebView。

| 触发 | 动画属性/范围 | 设计时长 | 原生落点 |
| --- | --- | --- | --- |
| 新路由/返回 | 新旧页面轻位移与淡入淡出，返回相反方向 | 入260ms / 出180ms | NavHost enter/exit/popEnter/popExit |
| 抽屉 | 横向位移、scrim 透明度 | 入260ms / 出180ms | 既有 Drawer 状态，不另叠同方向动画 |
| 底部面板 | 垂直位移、透明度、遮罩 | 入280ms / 出180ms | ModalBottomSheet 原生转场 |
| 小思考面板 | 12dp位移、0.97→1、淡入 | 入280ms / 出180ms | Popup内容 transition |
| 小面板→详细选择 | 旧层退出和新层进入，旧层立刻禁止交互 | 180–280ms | 同一modal宿主的目标内容切换 |
| 工具/Shell/Diff/高级项折叠 | 真正布局高度0↔测量高度、chevron旋转 | 240ms / 箭头200ms | AnimatedVisibility或受控尺寸，择一负责高度 |
| 代码换行/展开高度 | 内容局部尺寸过渡 | 220–240ms | 局部animateContentSize，不包整条会话 |
| 复制成功 | 局部图标/文字反馈 | 160ms | 小范围AnimatedContent或Crossfade |
| 列表新增、移除、筛选 | 内容淡入淡出，存续项平移到新位置 | 180–220ms | stable key + animateItem（以项目依赖可用性为准） |
| Provider正常↔管理 | 相同项目保留，radio↔checkbox与操作区过渡 | 180–220ms | keyed列表，局部slot变化 |
| radio/checkbox/switch/默认选中 | 色彩、thumb/mark、描边 | 160–180ms | updateTransition / animateColorAsState |
| tab/过滤/预览↔源码 | 选中标记与内容局部变化 | 160–220ms | 保留滚动状态，避免整页重建 |
| 附件加入/移除 | chip进入退出与位置变化，输入区尺寸跟随 | 180–220ms | AnimatedVisibility / keyed row |
| 顶部提示进入/消失 | host高度+内容淡入，不覆盖正文/底栏 | 200ms / 180ms | 顶栏下占位AnimatedVisibility |
| 表单保存/连接测试/错误 | 原控件保留，相关反馈/按钮状态变化 | 160–220ms | 状态slot动画，不重建TextField |
| 发送↔停止↔可发送/禁用 | 图标/按钮底色和强调度 | 160–180ms | 局部AnimatedContent；取消回调立即执行 |
| 主题 | 语义背景/文字/描边色，不旋转/位移整页 | 180ms | 有限颜色过渡；不要每次重组重开动画 |
| 按下/松开 | 很轻的scale0.98（发送底形0.94） | 110ms | InteractionSource + graphicsLayer；保留点击指示 |
| 评审360↔412宽度 | 设备壳宽度 | 260ms | 仅网页评审；Android响应窗口变化，不硬编码手机壳 |

默认 easing：`cubic-bezier(.2,.8,.2,1)`。原生也可采用无明显回弹、可打断的 spring；时长并非数据层等待时间。

## 必须保留的连续性

- 动画依附稳定语义节点：conversationId / messageId / toolCallId；不能以屏幕 index 当唯一身份。
- 同页更新不清空整页容器。Provider选中不能重建列表并跳回顶部；滑块变化不能清空焦点；工具主题切换不能丢失展开/滚动。
- 新路由与同页状态分开：切默认 Provider 不是重播页面进入动画；输入一个字符不是页面变化。
- 高度动画会影响兄弟布局时，不能只做视觉 offset；内容不得画到 composer 或点击区上。
- 连续操作从当前显示状态过渡到最后目标。退出中的层立刻 inert/不可点击、退出完成再移除；取消旧动画也应清理其回调。
- 网络停止、权限收紧、取消压缩等业务动作先执行，不等待动画。
- 不追求所有组件同时运动。单个用户动作只让相关区域变化，周围静态正文留在原位。

## 不应动画的高频/系统输入

键盘输入、光标、文本选择即时响应；滑块在拖动时直接跟随手指。流式 Markdown 不按每个 Token/每个120ms采样重新淡入，不对正在增长的整段回答不断animateContentSize。新消息首次加入可轻量出现，后续增量保留已排版文本。

系统 IME、状态栏、外部文件选择器、预测返回由原生协作，不提供自造系统动画；仍需真机验证窗口insets、键盘动画与返回手势。大结果/大Diff采用预算和按需渲染，而非对全部行挨个延迟进场。

## 减少动态效果

网页遵循 `prefers-reduced-motion`，评审开关不能覆盖系统要求。原生遵循系统动画设置和对应的无障碍需求。关闭时直接显示最终布局，取消循环动画；旧层不能留在无障碍树或继续吃点击。不是把opacity改0但仍占位/可聚焦。

## 覆盖证据与边界

浏览器脚本验证了真实展开中间高度、快速反向操作、面板间替换清理、顶部消息占位动画、同页局部motion记录、CSS状态过渡、减少动效以及不对流式Token重播动画。`motion-preview.gif` 是原型交互录像；它不是Android帧率、无障碍认证或生产性能基线。

官方参考：见 SOURCES.md 中 Android Animation Quick Guide、Lists、Layout Basics、W3C C39。实现应检查项目当前 Compose 依赖，不能只因最新文档示例而升级全栈。
