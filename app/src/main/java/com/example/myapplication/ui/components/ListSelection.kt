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
import com.example.myapplication.ui.components.UiTextButton
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
    val availableIds: Set<String>,
    val allSelected: Boolean,
    val hasVisibleItems: Boolean,
    val onEnter: () -> Unit,
    val onExit: () -> Unit,
    val onToggle: (String) -> Unit,
    val onToggleAll: () -> Unit,
    val labelForId: (String) -> String
)

/** 选择集合独立于业务默认项；刷新后只向操作回调提供仍存在的 ID。 */
@Composable
fun rememberListSelection(
    ids: List<String>,
    labels: Map<String, String> = emptyMap(),
    visibleIds: List<String> = ids
): ListSelection {
    var active by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    val available = ids.toSet()
    val visible = visibleIds.toSet().intersect(available)
    val valid = availableSelection(selected.toSet(), available)
    fun exit() {
        active = false
        selected = arrayListOf()
    }
    BackHandler(enabled = active) { exit() }
    return ListSelection(
        active = active,
        selectedIds = valid,
        availableIds = available,
        allSelected = visible.isNotEmpty() && valid.containsAll(visible),
        hasVisibleItems = visible.isNotEmpty(),
        onEnter = { selected = arrayListOf(); active = true },
        onExit = ::exit,
        onToggle = { id ->
            if (id in available) selected = ArrayList(if (id in valid) valid - id else valid + id)
        },
        onToggleAll = {
            selected = ArrayList(toggleVisibleSelection(valid, available, visible))
        },
        labelForId = { labels[it]?.ifBlank { it } ?: it }
    )
}

/** 管理模式下的显式操作栏。删除确认使用快照，避免确认期间列表变化扩大范围。 */
@Composable
fun ListSelectionBar(
    selection: ListSelection,
    busy: Boolean = false,
    onDelete: (Set<String>) -> Unit,
    onExport: ((Set<String>) -> Unit)? = null,
    deleteNotice: String? = null
) {
    var deleteIds by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var deleteNames by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    Surface(modifier = Modifier.inertWhen(!selection.active), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已选 ${selection.selectedIds.size} 项")
                UiTextButton(onClick = selection.onToggleAll, enabled = !busy && selection.hasVisibleItems) {
                    Text(if (selection.allSelected) "取消全选" else "全选")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                onExport?.let { action ->
                    UiTextButton(onClick = { action(selection.selectedIds) },
                        enabled = !busy && selection.selectedIds.isNotEmpty()) { Text("导出已选") }
                }
                UiTextButton(onClick = {
                    deleteIds = ArrayList(selection.selectedIds)
                    deleteNames = ArrayList(selection.selectedIds.map(selection.labelForId))
                },
                    enabled = !busy && selection.selectedIds.isNotEmpty()) {
                    Text("删除已选", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!busy) deleteIds = arrayListOf() },
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
            dismissButton = { UiTextButton(onClick = { deleteIds = arrayListOf() }) { Text("取消") } },
            confirmButton = {
                UiTextButton(enabled = !busy, onClick = {
                    val snapshot = confirmedSelection(deleteIds.toSet(), selection.selectedIds,
                        selection.availableIds)
                    deleteIds = arrayListOf()
                    if (snapshot.isNotEmpty()) onDelete(snapshot)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}
