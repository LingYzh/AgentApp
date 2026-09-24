package com.example.myapplication.data.model

/** Explicit catalogue declarations refine choices; unknown metadata keeps protocol defaults. */
fun ProviderConfig.reasoningSupportForModel(modelId: String = model): ReasoningSupport {
    val fallback = reasoningSupportFor(type, modelId)
    if (fallback.protocol == ReasoningProtocol.UNSUPPORTED) return fallback
    val metadata = discoveredModelMetadata[modelId] ?: return fallback
    if (metadata.reasoning == false) return fallback.copy(
        protocol = ReasoningProtocol.UNSUPPORTED,
        efforts = emptyList(),
        description = "模型列表接口声明此模型不支持思考参数。"
    )
    val advertised = metadata.reasoningEfforts ?: return fallback
    // The catalogue describes available values, not a new wire protocol. Never
    // interpret a vendor's arbitrary thinkingSchemaPath as executable configuration.
    val supported = advertised.mapNotNull { value ->
        ReasoningEffort.entries.firstOrNull { it.wireValue == value.lowercase() }
    }.distinct().filter { type != ProviderType.GEMINI || it in fallback.efforts }
        .sortedBy { it.ordinal }
    return fallback.copy(efforts = supported, description =
        "可选思考档位来自模型列表接口：${advertised.joinToString("、")}。请求格式仍使用所选供应商协议。")
}
