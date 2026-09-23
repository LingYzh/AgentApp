package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/** Only composer settings are remembered; message text and attachments belong to their draft. */
@Serializable
data class NewChatDefaults(
    val agentId: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val permissionMode: PermissionMode = PermissionMode.ACCEPT_EDIT,
    val allowedDirectories: List<String> = emptyList(),
    val workingDirectory: String? = null
) {
    fun draft(config: AppConfig, agents: List<AgentProfile>, requestedAgentId: String? = null): Conversation {
        val selectedAgent = (requestedAgentId ?: agentId)?.takeIf { id -> agents.any { it.id == id } }
        // Explicitly starting an Agent uses that Agent's model; ordinary New Chat remembers the composer.
        val agentHasModel = requestedAgentId?.let { id ->
            agents.firstOrNull { it.id == id }?.let { it.providerId != null && !it.model.isNullOrBlank() }
        } == true
        val selectedProvider = providerId?.takeIf { id -> !agentHasModel && config.providers.any { it.id == id } }
        val selectedModel = model?.takeIf { candidate -> config.providers.any {
            it.id == selectedProvider && candidate in it.models
        } }
        val draft = Conversation(
            id = "", agentId = selectedAgent,
            providerIdOverride = selectedProvider,
            modelOverride = selectedModel,
            reasoningEffortOverride = reasoningEffort,
            permissionMode = permissionMode,
            allowedDirectories = allowedDirectories.toList(),
            workingDirectory = workingDirectory
        )
        ModelResolver.resolve(draft, config, agents)?.let {
            draft.reasoningEffortOverride = it.sessionEffort(reasoningEffort)
        }
        return draft
    }

    companion object {
        fun fromDraft(draft: Conversation, prior: NewChatDefaults) = NewChatDefaults(
            agentId = draft.agentId,
            providerId = draft.providerIdOverride,
            model = draft.modelOverride,
            // A provider without reasoning support must not erase the last chosen strength.
            reasoningEffort = draft.reasoningEffortOverride ?: prior.reasoningEffort,
            permissionMode = draft.permissionMode,
            allowedDirectories = draft.allowedDirectories.toList(),
            workingDirectory = draft.workingDirectory
        )
    }
}

fun ReasoningSupport.defaultEffort(): ReasoningEffort? =
    ReasoningEffort.MEDIUM.takeIf { it in efforts } ?: efforts.getOrNull(efforts.size / 2)

fun ProviderConfig.sessionEffort(preferred: ReasoningEffort?): ReasoningEffort? {
    val support = reasoningSupportFor(type, model)
    return preferred?.takeIf { it in support.efforts } ?: support.defaultEffort()
}
