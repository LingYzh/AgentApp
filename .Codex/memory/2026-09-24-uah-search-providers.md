# UAH 品牌与多搜索服务

## 实现

- 按 design/UAH_Android_Icon_Kit_v1/CODEX_APPLY.md 安装原始 PNG、adaptive 与 monochrome 图标。系统全称 Used AI Harness，顶栏/抽屉 UAH，应用内 AgentMark 使用 ic_uah_mark。
- applicationId、namespace、Application 类、备份协议保持；安装脚本的本地备份目录已加入 gitignore。
- 搜索提供 SearXNG、SerpApi（Google/Bing/百度）、Brave、Tavily、Exa、Google Custom Search（仅存量账号）六种接入方式。
- WebSearchConfig 保留旧 baseUrl/enabled 的兼容读取；services 保存各服务配置，provider 指定唯一当前服务，切换不丢密钥、不自动回退。
- 自定义 URL 必须是对应协议的完整 endpoint，SearXNG 额外支持根目录及子路径。密钥遮挡；必需参数缺失则 search 不开放。fetch 无需配置。
- SearchServices 负责请求与结果标准化；WebTools 保留取消、2MiB 读取上限。搜索客户端禁止重定向，不回显可能带密钥的上游错误和网络异常详情。
- 未接入已退休 Bing v7；通过 SerpApi 获取 Bing 结果。Google 官方入口明确存量账号与截止日期。

## 原始接口文档

- https://serpapi.com/search-api
- https://serpapi.com/bing-search-api
- https://serpapi.com/baidu-search-api （q、engine=baidu、api_key）
- https://api-dashboard.search.brave.com/app/documentation/web-search
- https://docs.tavily.com/documentation/api-reference/endpoint/search
- https://exa.ai/docs/reference/search
- https://developers.google.com/custom-search/v1/reference/rest/v1/cse/list
- https://developers.google.com/custom-search/v1/overview
- https://learn.microsoft.com/en-us/lifecycle/announcements/bing-search-api-retirement

## 验证边界

- 未持有以上付费服务的 API Key，不能认定真实账户连通性已通过；配置页面可由用户填写后测试。
- 先前 SearXNG 模拟接口的 search→fetch 以及未配置禁用流程已验证，见同日 web-search 记忆；不能代替新增服务真实调用验证。
- assembleDebug、lintDebug、git diff --check 通过，未增加或运行 JVM 测试。
- 原始 Android 图标资源 19/19 逐字节相同；模拟器系统应用信息中看到 Used AI Harness 与新圆形图标，首页显示 UAH 与新标志。
- 模拟器确认原有 SearXNG 地址兼容读取、六服务菜单、SerpApi 三引擎与遮挡密钥输入界面。截图 build/reply-fixes-20260924/uah-*.png。
- APK 已覆盖安装到 emulator-5554 和 NX809J 912606610730，均 Success；未操作手机 UI。
- APK SHA256：4E4BF19CCC5F7D0CBDCD5D3E754115899D9597F682FC60A5D1D34C3601137030。
- 新搜索功能、品牌资源与相关文档纳入本次检查点提交；Agent 配置刷新和按需权限查询仍为待办。用户验收：抽屉“网络搜索服务”选服务→填写对应密钥/参数→启用→测试搜索→保存，再在 Agent 中勾选 search。
