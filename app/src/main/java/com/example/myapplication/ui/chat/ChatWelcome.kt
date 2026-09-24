package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.AppModalBottomSheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.ui.agents.AgentAvatar
import com.example.myapplication.ui.components.UiTextButton

/** Use the supplied UAH vector at every in-app brand mark location. */
@Composable
internal fun AgentMark(modifier: Modifier = Modifier) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_uah_mark),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = modifier.size(44.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatWelcome(modifier: Modifier = Modifier, onSuggestion: (String) -> Unit) {
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < 380.dp
        // Center the hero in the available body, but allow scrolling when IME/font size reduces it.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight).padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Center) {
            AgentMark()
            Text("把想法，\n慢慢变成现实。", Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 11.dp),
                fontFamily = FontFamily(Font(R.font.home_serif)), fontSize = if (narrow) 30.sp else 32.sp,
                lineHeight = 52.sp, letterSpacing = (-1.1).sp)
            Text("从一个问题、一份文件，\n或一件想完成的小事开始。", fontSize = 14.sp, lineHeight = 27.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("整理文件", Icons.Outlined.Folder, "整理这份项目说明，给我一份可执行的迭代计划。"),
                    Triple("理解代码", Icons.Outlined.Code, "阅读工作区中的代码，帮我梳理结构和风险。"),
                    Triple("打磨文字", Icons.Outlined.Edit, "帮我润色这份文稿，保留我的语气。")
                ).forEach { (label, icon, prompt) ->
                    OutlinedButton(onClick = { onSuggestion(prompt) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftAgentPicker(selected: AgentProfile?, agents: List<AgentProfile>, onSelect: (AgentProfile?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    UiTextButton(onClick = { expanded = true }) {
        Text(selected?.name ?: "通用助手", maxLines = 1, modifier = Modifier.widthIn(max = 150.dp),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 13.sp)
        Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
    }
    if (expanded) AppModalBottomSheet(onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text("选择 Agent", style = MaterialTheme.typography.titleLarge)
            UiTextButton(onClick = { onSelect(null); expanded = false }) { Text("通用助手 · 跟随全局配置") }
            agents.forEach { agent ->
                Surface(onClick = { onSelect(agent); expanded = false }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(agent.emoji, agent.avatarPath, size = 32.dp)
                        Text(agent.name, Modifier.weight(1f).padding(start = 12.dp))
                        RadioButton(selected?.id == agent.id, onClick = null)
                    }
                }
            }
        }
    }
}
