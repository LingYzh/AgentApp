package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Common list actions share one sheet while each page keeps its own transfer behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListOperationsMenu(
    selection: ListSelection,
    busy: Boolean,
    onImport: (() -> Unit)? = null,
    onExport: () -> Unit,
    exportLabel: String = "导出"
) {
    var expanded by remember { mutableStateOf(false) }
    if (selection.active) {
        UiTextButton(onClick = selection.onExit, enabled = !busy) { Text("完成") }
    } else {
        IconButton(onClick = { expanded = true }, enabled = !busy) {
            Icon(Icons.Filled.MoreVert, contentDescription = "列表操作")
        }
    }
    if (expanded) {
        AppModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("列表操作", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { expanded = false }) { Icon(Icons.Filled.Close, "关闭列表操作") }
                }
                ListItem(
                    headlineContent = { Text("管理与多选") },
                    leadingContent = { Icon(Icons.Filled.Checklist, null) },
                    modifier = Modifier.listAction(!busy) { expanded = false; selection.onEnter() }
                )
                HorizontalDivider()
                onImport?.let { importAction ->
                    ListItem(
                        headlineContent = { Text("导入") },
                        leadingContent = { Icon(Icons.Filled.FileUpload, null) },
                        modifier = Modifier.listAction(!busy) { expanded = false; importAction() }
                    )
                }
                ListItem(
                    headlineContent = { Text(exportLabel) },
                    leadingContent = { Icon(Icons.Filled.FileDownload, null) },
                    modifier = Modifier.listAction(!busy) { expanded = false; onExport() }
                )
            }
        }
    }
}

private fun Modifier.listAction(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.then(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick))
