package com.example.myapplication.ui.components

import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Copy always uses the original record, including whitespace and line endings. */
@Composable
fun InlineCodePanel(title: String, text: String, emptyLabel: String = "本次命令没有输出") {
    var wrap by rememberSaveable { mutableStateOf(false) }
    var tall by rememberSaveable { mutableStateOf(false) }
    var copied by remember(text) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val horizontal = rememberScrollState()
    val lines = remember(text) { text.split('\n') }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column {
            Text(title, Modifier.padding(start = 10.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium)
            Row {
                UiTextButton(onClick = { wrap = !wrap }) { Text(if (wrap) "不换行" else "换行") }
                UiTextButton(onClick = { tall = !tall }) { Text(if (tall) "收起高度" else "展开高度") }
                UiTextButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
                    androidx.compose.animation.Crossfade(copied, label = "copy") { Text(if (it) "已复制" else "复制") }
                }
            }
            if (text.isEmpty()) Text(emptyLabel, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            else SelectionContainer {
                BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
                    val longest = remember(text) { lines.maxOfOrNull(::codeColumns) ?: 0 }
                    val width = if (wrap) maxWidth else maxOf(maxWidth, with(LocalDensity.current) { (longest * 8).sp.toDp() } + 20.dp)
                    Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
                        LazyColumn(Modifier.width(width).heightIn(max = if (tall) 520.dp else 238.dp),
                            contentPadding = PaddingValues(10.dp)) {
                            itemsIndexed(lines) { _, line ->
                                Text(line, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                    lineHeight = 21.sp, softWrap = wrap)
                            }
                        }
                    }
                }
            }
        }
    }
}
