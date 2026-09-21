package com.example.myapplication.provider

import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.store.AttachmentStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 按配置类型创建 Provider；持有共享的 OkHttpClient。open 以便测试替换。 */
open class ProviderFactory(
    val client: OkHttpClient = defaultClient(),
    val attachmentStore: AttachmentStore? = null
) {
    open fun create(type: ProviderType): ApiProvider = when (type) {
        ProviderType.OPENAI -> OpenAiProvider(client, attachmentStore)
        ProviderType.ANTHROPIC -> AnthropicProvider(client, attachmentStore)
        ProviderType.GEMINI -> GeminiProvider(client, attachmentStore)
        ProviderType.CUSTOM -> CustomProvider(client, attachmentStore)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES) // SSE 长连接
            .retryOnConnectionFailure(true)
            .build()
    }
}
