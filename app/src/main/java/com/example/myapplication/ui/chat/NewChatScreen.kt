package com.example.myapplication.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.myapplication.Routes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** The home slot holds either a draft or a saved conversation; first send keeps its identity. */
@Serializable
internal data class HomeChatSelection(
    val sessionKey: String,
    val conversationId: String? = null,
    val agentId: String? = null
) {
    companion object {
        fun draft(agentId: String? = null) = HomeChatSelection("draft-${UUID.randomUUID()}", agentId = agentId)
        fun history(id: String) = HomeChatSelection(id, conversationId = id)
    }
}

private const val HOME_CHAT_STATE = "home-chat-selection"
private val homeChatJson = Json { encodeDefaults = true }

/** Explicit user selection replaces only the home slot, leaving persisted conversations intact. */
fun NavHostController.showHomeChat(conversationId: String? = null, agentId: String? = null) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    val home = getBackStackEntry(Routes.NEW_CHAT)
    val selection = conversationId?.let(HomeChatSelection::history) ?: HomeChatSelection.draft(agentId)
    home.savedStateHandle[HOME_CHAT_STATE] = homeChatJson.encodeToString(HomeChatSelection.serializer(), selection)
    if (currentBackStackEntry != home) popBackStack(Routes.NEW_CHAT, inclusive = false)
}

/** A deleted active conversation must not leave the home slot pointing at a missing record. */
internal fun NavHostController.clearDeletedHomeChat(ids: Set<String>) {
    val saved = getBackStackEntry(Routes.NEW_CHAT).savedStateHandle
    val encoded = saved.get<String>(HOME_CHAT_STATE) ?: return
    val selection = homeChatJson.decodeFromString(HomeChatSelection.serializer(), encoded)
    if (selection.conversationId in ids) {
        saved[HOME_CHAT_STATE] = homeChatJson.encodeToString(HomeChatSelection.serializer(), HomeChatSelection.draft())
    }
}

@Composable
fun NewChatScreen(navController: NavHostController, entry: NavBackStackEntry, openDrawer: () -> Unit) {
    val selectionFlow = remember(entry) {
        entry.savedStateHandle.getStateFlow(HOME_CHAT_STATE, homeChatJson.encodeToString(
            HomeChatSelection.serializer(), HomeChatSelection.draft(entry.arguments?.getString("agentId"))
        ))
    }
    val encoded by selectionFlow.collectAsStateWithLifecycle()
    val selection = remember(encoded) { homeChatJson.decodeFromString(HomeChatSelection.serializer(), encoded) }
    key(selection.sessionKey) {
        ChatScreen(navController, conversationId = selection.conversationId, sessionKey = selection.sessionKey,
            draftAgentId = selection.agentId, openDrawer = openDrawer, onCommitted = { id ->
                val current = entry.savedStateHandle.get<String>(HOME_CHAT_STATE)?.let {
                    homeChatJson.decodeFromString(HomeChatSelection.serializer(), it)
                }
                if (current?.sessionKey == selection.sessionKey && current.conversationId != id) {
                    // Record the durable ID without changing the component key or navigating.
                    entry.savedStateHandle[HOME_CHAT_STATE] = homeChatJson.encodeToString(
                        HomeChatSelection.serializer(), current.copy(conversationId = id)
                    )
                }
            })
    }
}
