package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.agents.AgentEditScreen
import com.example.myapplication.ui.agents.AgentsScreen
import com.example.myapplication.ui.chat.ChatScreen
import com.example.myapplication.ui.chat.ConversationsScreen
import com.example.myapplication.ui.files.FileViewScreen
import com.example.myapplication.ui.files.FilesScreen
import com.example.myapplication.ui.memory.MemoryScreen
import com.example.myapplication.ui.providers.ProviderEditScreen
import com.example.myapplication.ui.providers.ProvidersScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.skills.SkillEditScreen
import com.example.myapplication.ui.skills.SkillsScreen
import com.example.myapplication.ui.theme.AgentTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDIT = "providerEdit/{providerId}"
    const val FILES = "files"
    const val FILE_VIEW = "fileView/{path}"
    const val MEMORY = "memory"
    const val SKILLS = "skills"
    const val SKILL_EDIT = "skillEdit/{name}"
    const val AGENTS = "agents"
    const val AGENT_EDIT = "agentEdit/{id}"
    const val SETTINGS = "settings"

    fun chat(id: String) = "chat/$id"
    fun providerEdit(id: String) = "providerEdit/$id"
    fun fileView(path: String) = "fileView/" + URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
    fun skillEdit(name: String) = "skillEdit/" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
    fun agentEdit(id: String) = "agentEdit/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as AgentApp
            val themeMode by app.themeMode.collectAsStateWithLifecycle()
            AgentTheme(themeMode = themeMode) {
                AppRoot()
            }
        }
    }
}

private data class DrawerEntry(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val entries = listOf(
        DrawerEntry(Routes.CONVERSATIONS, "对话", Icons.AutoMirrored.Filled.Chat),
        DrawerEntry(Routes.AGENTS, "Agents", Icons.Filled.Face),
        DrawerEntry(Routes.PROVIDERS, "模型配置", Icons.Filled.Terminal),
        DrawerEntry(Routes.FILES, "文件工作区", Icons.Filled.Folder),
        DrawerEntry(Routes.MEMORY, "记忆", Icons.Filled.Star),
        DrawerEntry(Routes.SKILLS, "Skills", Icons.Filled.Build),
        DrawerEntry(Routes.SETTINGS, "设置 / 备份", Icons.Filled.Settings)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "手机 Agent",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                entries.forEach { entry ->
                    NavigationDrawerItem(
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                        selected = currentRoute == entry.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute != entry.route) {
                                navController.navigate(entry.route) {
                                    launchSingleTop = true
                                    popUpTo(Routes.CONVERSATIONS)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) {
        // Surface 提供背景色，避免导航转场时露出窗口灰底
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.CONVERSATIONS,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.CONVERSATIONS) {
                    ConversationsScreen(navController, openDrawer)
                }
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { backStack ->
                    ChatScreen(
                        navController = navController,
                        conversationId = backStack.arguments?.getString("conversationId").orEmpty()
                    )
                }
                composable(Routes.PROVIDERS) { ProvidersScreen(navController, openDrawer) }
                composable(
                    Routes.PROVIDER_EDIT,
                    arguments = listOf(navArgument("providerId") { type = NavType.StringType })
                ) { backStack ->
                    ProviderEditScreen(
                        navController = navController,
                        providerId = backStack.arguments?.getString("providerId").orEmpty()
                    )
                }
                composable(Routes.FILES) { FilesScreen(navController, openDrawer) }
                composable(
                    Routes.FILE_VIEW,
                    arguments = listOf(navArgument("path") { type = NavType.StringType })
                ) { backStack ->
                    FileViewScreen(
                        navController = navController,
                        path = backStack.arguments?.getString("path").orEmpty()
                    )
                }
                composable(Routes.MEMORY) { MemoryScreen(openDrawer) }
                composable(Routes.SKILLS) { SkillsScreen(navController, openDrawer) }
                composable(
                    Routes.SKILL_EDIT,
                    arguments = listOf(navArgument("name") { type = NavType.StringType })
                ) { backStack ->
                    SkillEditScreen(
                        navController = navController,
                        skillName = backStack.arguments?.getString("name").orEmpty()
                    )
                }
                composable(Routes.AGENTS) { AgentsScreen(navController, openDrawer) }
                composable(
                    Routes.AGENT_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStack ->
                    AgentEditScreen(
                        navController = navController,
                        agentId = backStack.arguments?.getString("id").orEmpty()
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(openDrawer) }
            }
        }
    }
}
