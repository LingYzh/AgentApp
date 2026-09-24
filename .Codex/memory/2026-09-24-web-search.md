# SearXNG 全局搜索服务与网络工具 · 2026-09-24

## 用户决定

- 前序工作已作为检查点提交 d4a987c；本功能为后续未提交改动。
- 首版接入用户熟悉的 SearXNG；全局一个当前服务，Agent 仅决定是否允许 search。fetch 独立于搜索服务。

## 实现

- AppConfig.webSearch 保存 WebSearchConfig（实例地址、启用状态），旧配置缺失字段保持未配置，完整备份沿用 config.json 自动包含。地址支持实例根、子路径和 /search，拒绝 URL 内账号、查询参数与片段。
- 新增抽屉“网络搜索服务”及原生配置页：启用开关、地址、测试查询、测试结果和保存。测试使用草稿地址，不自动保存；清空地址保存可清除配置。
- Tools 注册 fetch/search。ToolExecutor 每次构造声明与执行时检查当前配置；未配置或停用时隐藏 search 声明，并拒绝模型伪造调用。沿用 Agent 工具授权和子代理限制，Plan/Readonly 可使用这两项只读工具。
- Agent 编辑页始终显示工具列表；不可用的 search 灰色未勾选，不能点击。“全选”排除不可用工具；允许全部仍受运行时检查。点选单项可从允许全部转为显式选择；既有显式工具列表不强行增加权限。
- ContextWindows 的本地估算同步排除不可用 search。
- SearXNG GET /search?q=...&format=json，默认最多 5 条，返回标题、摘要、来源和部分引擎失败提示。配置完整只指启用且地址合法，不要求联网测试成功才能保存。
- fetch 仅 HTTP(S) GET，提取 HTML/文本与相对链接，不执行 JS、不使用浏览器登录状态、不共享模型凭据或诊断拦截器。正文最多下载 2 MiB、返回 20000 字符；有连接/读取/总超时，沿用覆盖整个响应读取的取消机制。外部内容明确标记为不可信资料。

## 验证与边界

- assembleDebug、lintDebug 通过，lint 0 errors / 39 warnings / 5 hints；git diff --check 通过。本阶段未运行 JVM 测试。
- emulator-5554 上使用本地 5584 模拟 SearXNG 与模型接口：配置页测试搜索、保存；完整 search→fetch 工具循环、脚本文本移除、相对链接解析通过。
- 停用服务后 Agent 页 search disabled 且 unchecked，模型声明不含 search；伪造 search 调用被拒绝，fetch 仍执行成功。
- 模拟器数据和脚本位于忽略目录 build/web-search-qa，UI XML/截图位于 build/reply-fixes-20260924/web-*。没有用户实际 SearXNG 地址，尚未验证真实实例。
- APK SHA256 8F085A2928CF180675E7A577EBCEDECE6C4306F68C5569041C9C5B6C479751DA。
- 用户操作：抽屉→网络搜索服务→填写手机可访问的实例地址→测试搜索→保存；实例 search.formats 必须包含 json；到 Agent 工具中勾选 search/fetch。手机 localhost 指手机本身。

## 参考

- Codex 的内置搜索模式：https://developers.openai.com/codex/config-basic/
- OpenCode 的搜索供应商配置：https://opencode.ai/v2/docs/websearch
- OpenClaw 独立网页读取：https://docs.openclaw.ai/tools/web-fetch
- SearXNG 搜索接口：https://docs.searxng.org/dev/search_api.html
