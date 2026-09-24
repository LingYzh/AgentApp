package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.ui.chat.ToolActivityRow
import com.example.myapplication.ui.theme.AgentTheme

/** Synthetic records only; never imports a user's conversation or device screen text. */
class ToolRecordTestActivity : ComponentActivity() {
    companion object {
        val payload = "{\"nodes\":[\"" + "synthetic界面😀".repeat(2200) + "\"]}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("tool") ?: "device_observe"
        setContent {
            AgentTheme {
                CompositionLocalProvider(LocalDensity provides Density(3f, 1.5f)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            ToolActivityRow(
                                call = ToolCallInfo(id = "synthetic-call", name = name, argumentsJson = "{}"),
                                result = ChatMessage(role = "tool", content = payload, toolCallId = "synthetic-call"),
                                running = false, child = null, onOpenChild = {}, onViewFile = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
