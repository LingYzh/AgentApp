package com.example.myapplication.ui.chat

import androidx.lifecycle.ViewModelStore

/** Process-owned sessions let a foreground device task survive Activity destruction. */
class ChatSessionPool {
    internal val stores = linkedMapOf<String, ViewModelStore>()
    internal val sessions = linkedMapOf<String, ChatViewModel>()

    fun discardInactive() {
        sessions.filterValues { !it.streaming.value || !it.deviceTaskActive }.keys.toList().forEach { key ->
            stores.remove(key)?.clear()
            sessions.remove(key)
        }
    }
}
