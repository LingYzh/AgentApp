package com.example.myapplication.ui.chat

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavHostController
import com.example.myapplication.Routes
import java.util.UUID

/** A draft identity is not a conversation ID. The latter is allocated by the first disk commit. */
@Composable
fun NewChatScreen(navController: NavHostController, agentId: String?, openDrawer: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("draft-${UUID.randomUUID()}") }
    key(key) {
        ChatScreen(navController, conversationId = null, sessionKey = key, draftAgentId = agentId,
            openDrawer = openDrawer, onCommitted = { id ->
                navController.navigate(Routes.chat(id, key)) { launchSingleTop = true }
                // Returning to this entry gets an empty draft; the committed VM belongs to CHAT.
                key = "draft-${UUID.randomUUID()}"
            })
    }
}
