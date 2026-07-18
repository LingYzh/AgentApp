package com.example.myapplication.ui.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.data.model.SkillMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class SkillsViewModel(val app: AgentApp) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillMeta>>(emptyList())
    val skills = _skills.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = app.store.listSkills()
        }
    }

    fun delete(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.deleteSkill(name)
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: SkillsViewModel = viewModel(factory = viewModelFactory {
        initializer { SkillsViewModel(app) }
    })
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val skills by vm.skills.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.skillEdit("new")) }) {
                Icon(Icons.Filled.Add, "新建 Skill")
            }
        }
    ) { padding ->
        if (skills.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无 Skill。可以手动创建，也可以让 Agent 用 save_skill 工具自行创建。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skills, key = { it.name }) { skill ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navController.navigate(Routes.skillEdit(skill.name))
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                Text(skill.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.delete(skill.name) }) {
                                Icon(Icons.Filled.Delete, "删除")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditScreen(navController: NavHostController, skillName: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val decoded = remember(skillName) {
        URLDecoder.decode(skillName, StandardCharsets.UTF_8.toString())
    }
    val existing = remember(decoded) {
        if (decoded == "new") null else app.store.readSkill(decoded)?.let { raw ->
            app.store.parseSkill(raw).let { (meta, body) -> Triple(meta, body, raw) }
        }
    }

    var name by remember { mutableStateOf(existing?.first?.get("name") ?: "") }
    var description by remember { mutableStateOf(existing?.first?.get("description") ?: "") }
    var content by remember { mutableStateOf(existing?.second ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建 Skill" else "编辑 Skill") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (name.isNotBlank()) {
                            // 改名时删除旧文件
                            if (existing != null && decoded != name.trim()) {
                                app.store.deleteSkill(decoded)
                            }
                            app.store.saveSkill(name.trim(), description.trim(), content)
                            navController.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Check, "保存") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("名称（Agent 用 use_skill 按名调用）") }, singleLine = true)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
                label = { Text("描述（告诉 Agent 何时该用这个 Skill）") }, singleLine = true)
            OutlinedTextField(
                content, { content = it }, Modifier.fillMaxWidth(),
                label = { Text("指令正文（Markdown）") },
                minLines = 12,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}
