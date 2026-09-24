package com.example.myapplication

import com.example.myapplication.data.model.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveredReasoningTest {
    @Test fun `missing declaration keeps protocol defaults`() {
        val config = ProviderConfig(model = "gateway-model")
        assertEquals(reasoningSupportFor(config.type, config.model), config.reasoningSupportForModel())
    }

    @Test fun `explicit false disables automatic thinking parameters`() {
        val config = ProviderConfig(type = ProviderType.ANTHROPIC, model = "auto",
            discoveredModelMetadata = mapOf("auto" to ModelMetadata(reasoning = false)))
        assertTrue(config.reasoningSupportForModel().efforts.isEmpty())
        assertNull(config.sessionEffort(ReasoningEffort.HIGH))
    }

    @Test fun `advertised efforts refine the actual selectable values`() {
        val config = ProviderConfig(type = ProviderType.ANTHROPIC, model = "gateway-model",
            discoveredModelMetadata = mapOf("gateway-model" to ModelMetadata(reasoning = true,
                reasoningEfforts = listOf("high", "low", "medium", "unknown-future"))))
        assertEquals(listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            config.reasoningSupportForModel().efforts)
        assertEquals(ReasoningEffort.MEDIUM, config.sessionEffort(ReasoningEffort.MAX))
    }

    @Test fun `metadata does not invent a Gemini wire protocol or modify custom templates`() {
        listOf(ProviderType.GEMINI, ProviderType.CUSTOM).forEach { type ->
            val config = ProviderConfig(type = type, model = "unknown-alias",
                discoveredModelMetadata = mapOf("unknown-alias" to ModelMetadata(reasoning = true,
                    reasoningEfforts = listOf("high"))))
            assertEquals(ReasoningProtocol.UNSUPPORTED, config.reasoningSupportForModel().protocol)
        }
    }
}
