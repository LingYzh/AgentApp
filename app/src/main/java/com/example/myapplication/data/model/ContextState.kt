package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ContextCompaction(
    val summary: String,
    val coveredMessageIds: List<String>,
    val sourceModel: String,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ContextUsageRecord(
    val usage: TokenUsage,
    val model: String,
    val recordedAt: Long = System.currentTimeMillis()
)

data class ContextSegment(val key: String, val label: String, val tokens: Long)

data class ContextOverview(
    val maxTokens: Int?,
    val segments: List<ContextSegment>,
    val lastUsage: ContextUsageRecord?,
    val compactedMessages: Int
) {
    val estimatedTokens: Long get() = segments.sumOf { it.tokens }
}
