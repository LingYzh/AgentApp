package com.example.myapplication.ui.chat

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.AgentApp
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MessageAttachment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatUiState(
    val draft: Conversation? = null,
    val conversationId: String? = null,
    val input: String = "",
    val attachments: List<MessageAttachment> = emptyList()
)

/** Saved UI belongs to the Activity; running tasks are retained in the application pool. */
class ChatSessions(private val saved: SavedStateHandle) : ViewModel() {
    private val pool = AgentApp.instance.chatSessionPool
    private val stores get() = pool.stores
    private val sessions get() = pool.sessions
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        saved.setSavedStateProvider("chat-ui") {
            Bundle().apply {
                saved.get<Bundle>("chat-ui")?.let { putAll(it) }
                sessions.forEach { (key, vm) ->
                    val state = vm.uiSnapshot()
                    // A history entry may use an ID key after a draft-key entry was restored.
                    if (state.conversationId != null) keySet().toList().filter { old ->
                        old != key && decode(getString(old))?.conversationId == state.conversationId
                    }.forEach(::remove)
                    putString(key, json.encodeToString(ChatUiState.serializer(), state))
                }
            }
        }
    }

    fun session(app: AgentApp, key: String, id: String?, agentId: String?): ChatViewModel {
        // Opening a running first-send conversation from history must not create a second writer.
        if (id != null) sessions.values.firstOrNull { it.committedId.value == id }?.let { return it }
        return sessions.getOrPut(key) {
            val bundle = saved.get<Bundle>("chat-ui")
            val restored = decode(bundle?.getString(key)) ?: id?.let { conversationId ->
                bundle?.keySet()?.asSequence()?.mapNotNull { decode(bundle.getString(it)) }
                    ?.firstOrNull { it.conversationId == conversationId }
            }
            val owner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore().also { stores[key] = it }
            }
            ViewModelProvider(owner, viewModelFactory {
                initializer { ChatViewModel(app, id, key, agentId, restored) }
            })[key, ChatViewModel::class.java]
        }
    }

    private fun decode(value: String?): ChatUiState? = value?.let {
        runCatching { json.decodeFromString(ChatUiState.serializer(), it) }.getOrNull()
    }

    /** Join final cancellation saves before deletion, otherwise a running job can resurrect history. */
    suspend fun stopForDeletion(ids: Set<String>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        sessions.filterValues { it.committedId.value in ids }.keys.toList().forEach { key ->
            sessions.remove(key)?.stopForDeletion()
            stores.remove(key)?.clear()
            saved.get<Bundle>("chat-ui")?.remove(key)
        }
    }

    override fun onCleared() { pool.discardInactive() }
}
