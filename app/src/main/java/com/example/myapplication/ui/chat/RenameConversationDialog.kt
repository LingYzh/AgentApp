package com.example.myapplication.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.components.UiTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RenameConversationDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> String?
) {
    var title by rememberSaveable { mutableStateOf(currentTitle) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改会话标题") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, onValueChange = { title = it; error = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("会话标题") }, enabled = !saving, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { UiTextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
        confirmButton = {
            UiTextButton(enabled = !saving && title.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    try {
                        error = onSave(title.trim())
                        if (error == null) onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "标题保存失败"
                    } finally { saving = false }
                }
            }) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "保存中…" else "保存")
            }
        }
    )
}
