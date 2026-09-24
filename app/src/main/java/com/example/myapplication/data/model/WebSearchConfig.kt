package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
enum class WebSearchProvider(val label: String, val defaultUrl: String, val help: String) {
    SEARXNG("SearXNG", "", "自建实例；需在 search.formats 中启用 json。手机上的 localhost 指手机本身。"),
    SERPAPI("SerpApi · Google / Bing / 百度", "https://serpapi.com/search.json", "使用 SerpApi 的 API Key，选择需要的搜索引擎。"),
    BRAVE("Brave Search", "https://api.search.brave.com/res/v1/web/search", "使用 Brave Search API 的订阅密钥。"),
    TAVILY("Tavily", "https://api.tavily.com/search", "使用 Tavily API Key，返回网页搜索摘要。"),
    EXA("Exa", "https://api.exa.ai/search", "使用 Exa API Key，返回搜索结果与内容摘录。"),
    GOOGLE("Google Custom Search · 存量账号", "https://customsearch.googleapis.com/customsearch/v1", "需要 Google API Key 和搜索引擎 ID（cx）。官方已停止新客户接入，存量服务计划于 2027-01-01 结束。")
}

@Serializable
data class WebSearchService(
    val baseUrl: String = "",
    val apiKey: String = "",
    val engine: String = "google",
    val searchEngineId: String = ""
)

/** Only the selected service is used. Retain the original URL for old configuration archives. */
@Serializable
data class WebSearchConfig(
    val baseUrl: String = "",
    val enabled: Boolean = true,
    val provider: WebSearchProvider = WebSearchProvider.SEARXNG,
    val services: Map<WebSearchProvider, WebSearchService> = emptyMap()
) {
    fun service(forProvider: WebSearchProvider = provider): WebSearchService = services[forProvider]
        ?: WebSearchService(baseUrl = if (forProvider == WebSearchProvider.SEARXNG) baseUrl else "")

    fun endpoint(): HttpUrl? {
        val url = service().baseUrl.trim().ifBlank { provider.defaultUrl }.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
        if (provider != WebSearchProvider.SEARXNG) return url
        val path = url.encodedPath.trimEnd('/')
        return url.newBuilder().encodedPath(if (path.endsWith("/search")) path else "$path/search").build()
    }

    val isConfigured: Boolean get() = enabled && endpoint() != null &&
        (provider == WebSearchProvider.SEARXNG || (service().apiKey.isNotBlank() &&
            service().apiKey.trim().all { it.code in 33..126 })) &&
        (provider != WebSearchProvider.GOOGLE || service().searchEngineId.isNotBlank()) &&
        (provider != WebSearchProvider.SERPAPI || service().engine in listOf("google", "bing", "baidu"))
}
