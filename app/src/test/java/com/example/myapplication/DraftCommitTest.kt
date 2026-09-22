package com.example.myapplication

import com.example.myapplication.data.model.*
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.ui.chat.ChatUiState
import com.example.myapplication.ui.chat.HomeChatSelection
import com.example.myapplication.agent.PermissionSession
import com.example.myapplication.agent.PermissionCoordinator
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors

class DraftCommitTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun draftReceiptsCannotBeEditedByAcceptEditTools() {
        val store = FileStore(temp.root)
        val session = PermissionSession(store, Conversation(
            permissionMode = PermissionMode.ACCEPT_EDIT,
            allowedDirectories = listOf(store.draftReceiptsDir.canonicalPath)
        ), PermissionCoordinator())
        assertTrue(session.canWritePath(File(store.draftReceiptsDir, "draft-one").absolutePath)
            ?.contains("会话元数据") == true)
        assertTrue(runCatching { store.commitDraft("../escape", Conversation()) }.isFailure)
    }

    @Test fun concurrentRetriesHaveOneConversationAndOneFirstMessage() {
        val store = FileStore(temp.root)
        assertTrue(store.listConversations().isEmpty())
        val candidate = Conversation(id = "", messages = mutableListOf(ChatMessage(role = "user", content = "first")))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { pool.submit<Conversation> { store.commitDraft("draft-one", candidate) } }
            val ids = jobs.map { it.get().id }.distinct()
            assertEquals(1, ids.size)
            assertEquals(1, store.listConversations().size)
            assertEquals(1, store.loadConversation(ids.single())!!.messages.size)
            assertEquals(ids.single(), FileStore(temp.root).committedDraft("draft-one")!!.id)
        } finally { pool.shutdownNow() }
    }

    @Test fun failedSaveLeavesNoHistoryAndRetryUsesReservedId() {
        val store = FileStore(temp.root)
        store.conversationsDir.delete()
        store.conversationsDir.writeText("block directory")
        val candidate = Conversation(id = "", messages = mutableListOf(ChatMessage(role = "user", content = "kept")))
        assertTrue(runCatching { store.commitDraft("draft-failed", candidate) }.isFailure)
        val reserved = File(temp.root, "draft-receipts/draft-failed").readText()
        store.conversationsDir.delete()
        store.conversationsDir.mkdirs()
        assertEquals(reserved, store.commitDraft("draft-failed", candidate).id)
        assertEquals("kept", store.committedDraft("draft-failed")!!.messages.single().content)
    }

    @Test fun emptyDraftCannotBeCommittedAndRecoveryDoesNotCreateHistory() {
        val store = FileStore(temp.root)
        assertNull(store.committedDraft("new-draft"))
        assertTrue(runCatching { store.commitDraft("new-draft", Conversation(id = "")) }.isFailure)
        assertTrue(store.listConversations().isEmpty())
    }

    @Test fun draftRestorationPreservesExplicitNoneAndInput() {
        val state = ChatUiState(draft = Conversation(id = "", agentId = "agent", modelOverride = "model",
            permissionMode = PermissionMode.READONLY, allowedDirectories = listOf("/storage/emulated/0"),
            reasoningEffortOverride = ReasoningEffort.NONE), input = "  pending\n")
        val json = Json { encodeDefaults = true }
        assertEquals(state, json.decodeFromString(ChatUiState.serializer(), json.encodeToString(ChatUiState.serializer(), state)))
        assertNull(state.conversationId)
    }

    @Test fun homeCanStartFreshAndReopenCommittedHistoryWithoutCreatingEmptyRecords() {
        val store = FileStore(temp.root)
        val home = HomeChatSelection.draft("agent")
        val committed = store.commitDraft(home.sessionKey, Conversation(id = "", agentId = home.agentId,
            messages = mutableListOf(ChatMessage(role = "user", content = "first"))))
        val json = Json { encodeDefaults = true }
        val restoredHome = json.decodeFromString(HomeChatSelection.serializer(),
            json.encodeToString(HomeChatSelection.serializer(), home))
        // Home keeps the draft session key after send, including across process restoration.
        assertEquals(committed.id, store.committedDraft(restoredHome.sessionKey)!!.id)
        val fresh = HomeChatSelection.draft()
        assertNotEquals(home.sessionKey, fresh.sessionKey)
        assertNull(fresh.conversationId)
        assertNull(fresh.agentId)
        assertNull(store.committedDraft(fresh.sessionKey))
        val history = HomeChatSelection.history(committed.id)
        assertEquals("first", store.loadConversation(history.conversationId!!)!!.messages.single().content)
        assertEquals(1, store.listConversations().size)
    }
}
