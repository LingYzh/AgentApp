package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModelMetadata(
    val contextWindow: Int? = null,
    val maxInputTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val reasoning: Boolean? = null,
    val temperature: Boolean? = null,
    val toolCall: Boolean? = null,
    val promptCaching: Boolean? = null,
    val reasoningEfforts: List<String>? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val raw: JsonObject = JsonObject(emptyMap())
)
