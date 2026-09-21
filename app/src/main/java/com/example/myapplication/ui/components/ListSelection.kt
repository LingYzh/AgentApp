package com.example.myapplication.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ListSelection(
    val active: Boolean,
    val selectedIds: Set<String>,
    val allSelected: Boolean,
    val onEnter: () -> Unit,
    val onExit: () -> Unit,
    val onToggle: (String) -> Unit,
    val onToggleAll: () -> Unit,
    val labelForId: (String) -> String
)

/** 选择集合独立于业务默认项；刷新后只向操作回调提供仍存在的 ID。 */
@Composable
fun rememberListSelection(ids: List<String>, labels: Map<String, String> = emptyMap()): ListSelection {
    var active by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    val available = ids.toSet()
    val valid = selected.toSet().intersect(available)
    fun exit() {
        active = false
        selected = arrayListOf()
    }
    BackHandler(enabled = active) { exit() }
    return ListSelection(
        active = active,
        selectedIds = valid,
        allSelected = available.isNotEmpty() && valid == available,
        onEnter = { selected = arrayListOf(); active = true },
        onExit = ::exit,
        onToggle = { id ->
            if (id in available) selected = ArrayList(if (id in valid) valid - id else valid + id)
        },
        onToggleAll = { selected = ArrayList(if (valid == available) emptySet() else available) },
        labelForId = { labels[it]?.ifBlank { it } ?: it }
    )
}

/** 管理模式下的显式操作栏。删除确认使用快照，避免确认期间列表变化扩大范围。 */
@Composable
fun ListSelectionBar(
    selection: ListSelection,
    busy: Boolean = false,
    onDelete: (Set<String>) -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: ((Set<String>) -> Unit)? = null,
    deleteNotice: String? = null
) {
    var deleteIds by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var deleteNames by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已选 ${selection.selectedIds.size} 项")
                TextButton(onClick = selection.onToggleAll, enabled = !busy) {
                    Text(if (selection.allSelected) "取消全选" else "全选")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                onImport?.let { TextButton(onClick = it, enabled = !busy) { Text("导入") } }
                onExport?.let { action ->
                    TextButton(onClick = { action(selection.selectedIds) },
                        enabled = !busy && selection.selectedIds.isNotEmpty()) { Text("导出已选") }
                }
                TextButton(onClick = {
                    deleteIds = ArrayList(selection.selectedIds)
                    deleteNames = ArrayList(selection.selectedIds.map(selection.labelForId))
                },
                    enabled = !busy && selection.selectedIds.isNotEmpty()) { Text("删除已选") }
            }
        }
    }
    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteIds = arrayListOf() },
            title = { Text("删除所选项目？") },
            text = {
                Column {
                    Text("将删除所选的 ${deleteIds.size} 项，此操作无法撤销。")
                    deleteNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(deleteNames.joinToString("\n"),
                        modifier = Modifier.padding(top = 12.dp).heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()))
                }
            },
            dismissButton = { TextButton(onClick = { deleteIds = arrayListOf() }) { Text("取消") } },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val snapshot = deleteIds.toSet().intersect(selection.selectedIds)
                    deleteIds = arrayListOf()
                    if (snapshot.isNotEmpty()) onDelete(snapshot)
                }) { Text("删除") }
            }
        )
    }
}
