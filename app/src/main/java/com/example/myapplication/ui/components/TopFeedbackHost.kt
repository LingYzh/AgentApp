package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ErrorFeedback(override val message: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction = true
    override val duration = SnackbarDuration.Indefinite
}

/** Keeps Material's timeout/accessibility handling in the prototype's top feedback slot. */
@Composable
fun TopFeedbackHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val isError = data.visuals is ErrorFeedback
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                    null, Modifier.size(18.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.visuals.message, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
                data.visuals.actionLabel?.let { label ->
                    UiTextButton(onClick = data::performAction) { Text(label) }
                }
                IconButton(onClick = data::dismiss) { Icon(Icons.Outlined.Close, "关闭提示", Modifier.size(18.dp)) }
            }
        }
    }
}
