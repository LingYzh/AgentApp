package com.example.myapplication

import com.example.myapplication.data.model.*
import com.example.myapplication.data.store.FileStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewChatDefaultsTest {
    @get:Rule val temp = TemporaryFolder()
    private val provider = ProviderConfig(id = "p", model = "gateway", models = listOf("selected", "composer-model"), reasoningEffort = ReasoningEffort.MAX)
    private val config = AppConfig(providers = listOf(provider))

    @Test fun firstDraftUsesMediumInsteadOfLegacyProviderDefault() {
        val draft = NewChatDefaults().draft(config, emptyList())
        assertNull(ModelResolver.resolve(draft, config, emptyList()))
        assertEquals(ReasoningEffort.MEDIUM, draft.reasoningEffortOverride)
        assertEquals(ReasoningEffort.MEDIUM, ModelResolver.resolve(Conversation(providerIdOverride = provider.id, modelOverride = "selected"), config, emptyList())!!.reasoningEffort)
        assertEquals(ReasoningEffort.NONE, ModelResolver.resolve(
            Conversation(providerIdOverride = provider.id, modelOverride = "selected", reasoningEffortOverride = ReasoningEffort.NONE), config, emptyList()
        )!!.reasoningEffort)
    }

    @Test fun removedRememberedModelDoesNotUseTestModelOrFirstCatalogEntry() {
        val defaults = NewChatDefaults(providerId = provider.id, model = "removed")
        val draft = defaults.draft(config, emptyList())
        assertNull(draft.modelOverride)
        assertNull(ModelResolver.resolve(draft, config, emptyList()))
    }

    @Test fun unsentSettingsAreAvailableImmediatelyAndSurviveRestartWithoutDraftContent() {
        val store = FileStore(temp.root)
        val agent = AgentProfile(id = "a", name = "agent")
        val draft = Conversation(id = "", agentId = agent.id, providerIdOverride = provider.id,
            modelOverride = "selected", reasoningEffortOverride = ReasoningEffort.HIGH,
            permissionMode = PermissionMode.AUTO, allowedDirectories = listOf(temp.root.path),
            workingDirectory = temp.root.path,
            messages = mutableListOf(ChatMessage(role = "user", content = "never inherit")))
        store.rememberNewChatDefaults(NewChatDefaults.fromDraft(draft, store.loadNewChatDefaults()))
        val immediate = store.loadNewChatDefaults().draft(config, listOf(agent))
        assertEquals(agent.id, immediate.agentId)
        assertEquals("selected", immediate.modelOverride)
        assertEquals(ReasoningEffort.HIGH, immediate.reasoningEffortOverride)
        assertEquals(PermissionMode.AUTO, immediate.permissionMode)
        assertEquals(listOf(temp.root.path), immediate.allowedDirectories)
        assertEquals(temp.root.path, immediate.workingDirectory)
        assertTrue(immediate.messages.isEmpty())
        assertEquals("", immediate.id)
        assertTrue(store.listConversations().isEmpty())
        store.flushNewChatDefaults()
        assertEquals(store.loadNewChatDefaults(), FileStore(temp.root).loadNewChatDefaults())
    }

    @Test fun queuedFlushAlwaysWritesLatestSelectionAndConfigSaveCannotOverwriteIt() {
        val store = FileStore(temp.root)
        store.rememberNewChatDefaults(NewChatDefaults(reasoningEffort = ReasoningEffort.LOW))
        store.rememberNewChatDefaults(NewChatDefaults(reasoningEffort = ReasoningEffort.MAX))
        store.flushNewChatDefaults()
        store.saveConfig(config)
        store.flushNewChatDefaults()
        assertEquals(ReasoningEffort.MAX, FileStore(temp.root).loadNewChatDefaults().reasoningEffort)
    }

    @Test fun unavailableAgentAndProviderFallBackAndUnsupportedReasoningKeepsPreference() {
        val prior = NewChatDefaults(agentId = "deleted", providerId = "deleted", model = "old",
            reasoningEffort = ReasoningEffort.HIGH)
        val custom = AppConfig(providers = listOf(ProviderConfig(type = ProviderType.CUSTOM)))
        val draft = prior.draft(custom, emptyList())
        assertNull(draft.agentId)
        assertNull(draft.providerIdOverride)
        assertNull(draft.modelOverride)
        assertEquals(ReasoningEffort.HIGH, draft.reasoningEffortOverride)
        assertEquals(ReasoningEffort.HIGH, NewChatDefaults.fromDraft(draft, prior).reasoningEffort)
    }

    @Test fun modelWithoutMediumUsesAnAvailableLevel() {
        val image = ProviderConfig(type = ProviderType.GEMINI, model = "gemini-3.1-flash-lite-image")
        val draft = NewChatDefaults(providerId = image.id, model = image.model).draft(
            AppConfig(providers = listOf(image.copy(models = listOf(image.model)))), emptyList())
        assertEquals(ReasoningEffort.HIGH, draft.reasoningEffortOverride)
    }

    @Test fun explicitStartAgentUsesItsModelAndOrdinaryNewChatKeepsRememberedModel() {
        val agent = AgentProfile(id = "agent", providerId = provider.id, model = "agent-model")
        val defaults = NewChatDefaults(agentId = agent.id, providerId = provider.id, model = "composer-model")
        val ordinary = defaults.draft(config, listOf(agent))
        val explicit = defaults.draft(config, listOf(agent), agent.id)
        assertEquals("composer-model", ModelResolver.resolve(ordinary, config, listOf(agent))!!.model)
        assertEquals("agent-model", ModelResolver.resolve(explicit, config, listOf(agent))!!.model)
    }
}
