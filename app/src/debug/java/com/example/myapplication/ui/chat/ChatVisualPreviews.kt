package com.example.myapplication.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.myapplication.AppDrawerSheetContent
import com.example.myapplication.data.model.*
import com.example.myapplication.ui.theme.AgentTheme

@Preview(name = "360 light", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "412 light", widthDp = 412, heightDp = 868, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "360 dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "412 dark", widthDp = 412, heightDp = 868, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ChatVisualSizes

class ChatVisualStates : PreviewParameterProvider<String> {
    override val values = sequenceOf("home", "chat", "running", "drawer", "model", "permissions", "reasoning", "context")
}

/** Preview fixtures stay in debug sources and never enter release business state. */
@ChatVisualSizes
@Composable
private fun ChatVisualPreview(@PreviewParameter(ChatVisualStates::class) state: String) {
    val provider = ProviderConfig(id = "preview", name = "预览 Provider", model = "preview-model")
    val support = reasoningSupportFor(provider.type, provider.model)
    AgentTheme {
        Surface(Modifier.fillMaxSize()) {
            when (state) {
                "drawer" -> Box(Modifier.fillMaxWidth(.86f)) { AppDrawerSheetContent(null, {},
                    listOf(Conversation(title = "一段已保存的会话"))) }
                "model" -> ModelPicker(listOf(provider to provider.model), { _, _ -> }, onDismiss = {})
                "permissions" -> SessionPermissionsDialog(PermissionMode.ACCEPT_EDIT, emptyList(), {}, { _, _ -> })
                "reasoning" -> ReasoningEffortMenu(support, null, ReasoningEffort.MEDIUM, true, {}, initiallyExpanded = true)
                "context" -> ContextUsageSheet(null, false, null, false, {}, {}, {})
                else -> ChatContent(
                    messages = if (state == "home") emptyList() else listOf(
                        ChatMessage(role = "user", content = "梳理一下这份说明。"),
                        ChatMessage(role = "assistant", content = "先确认目标，再逐项处理。", thinking = "检查已有上下文。")),
                    title = "项目说明", agentProfile = null, currentModel = provider.model,
                    streaming = state == "running", toolStatus = if (state == "running") "正在处理" else null,
                    modelOptions = listOf(provider to provider.model), onBack = {}, onSwitchModel = { _, _ -> },
                    onSendMessage = {}, onViewFile = {}, isDraft = state == "home", reasoningSupport = support,
                    modelReasoningEffort = ReasoningEffort.MEDIUM)
            }
        }
    }
}
