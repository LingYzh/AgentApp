package com.example.myapplication

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.data.model.Conversation
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.example.myapplication.ui.chat.showHomeChat
import com.example.myapplication.ui.files.FileViewScreen
import com.example.myapplication.ui.files.FilesScreen
import com.example.myapplication.ui.memory.MemoryScreen
import com.example.myapplication.ui.providers.ProviderEditScreen
import com.example.myapplication.ui.providers.ProvidersScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.skills.SkillEditScreen
import com.example.myapplication.ui.skills.SkillsScreen
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.ui.theme.ExpressiveTokens
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val NEW_CHAT = "newChat?agentId={agentId}"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}?session={session}"
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

    fun newChat(agentId: String? = null) = "newChat" + (agentId?.let { "?agentId=$it" } ?: "")
    fun chat(id: String, session: String? = null) = "chat/$id" + (session?.let { "?session=$it" } ?: "")
    fun providerEdit(id: String) = "providerEdit/$id"
    fun fileView(path: String) = "fileView/" + URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
    fun skillEdit(name: String) = "skillEdit/" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
    fun agentEdit(id: String) = "agentEdit/$id"
}

/**
 * 顶级路由集合，用于判断是否开启侧滑抽屉手势
 */
val TopLevelRoutes = setOf(
    Routes.NEW_CHAT,
    Routes.CONVERSATIONS,
    Routes.AGENTS,
    Routes.PROVIDERS,
    Routes.FILES,
    Routes.MEMORY,
    Routes.SKILLS,
    Routes.SETTINGS
)

/**
 * 安全导航扩展：单顶导航并保持状态，防止重复入栈与时序竞争
 */
fun NavHostController.safeNavigate(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 安全直接导航：用于下钻到二级详情页
 */
fun NavHostController.safeNavigateDirect(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * 安全返回扩展：防止连续狂按或手势竞态把起始路由弹空导致白屏/无控件底色
 */
fun NavHostController.safePopBackStack(): Boolean {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as AgentApp
            val themeMode by app.themeMode.collectAsStateWithLifecycle()
            val darkSystemBars = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                // SystemBarStyle's default detector follows Android, not the app override.
                // Reapply when either the chosen theme or system configuration changes.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { darkSystemBars },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { darkSystemBars }
                )
            }
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
    val isTopLevel = currentRoute in TopLevelRoutes
    val app = LocalContext.current.applicationContext as AgentApp
    var recent by remember { mutableStateOf(emptyList<Conversation>()) }
    LaunchedEffect(drawerState.targetValue, currentRoute) {
        if (drawerState.targetValue == DrawerValue.Open) {
            recent = withContext(Dispatchers.IO) {
                app.store.listConversations().filter { it.parentConversationId == null }
                    .sortedByDescending { it.messages.maxOfOrNull { message -> message.timestamp } ?: it.createdAt }.take(3)
            }
        }
    }

    // 当抽屉打开时，按下物理或手势返回键优先合拢抽屉，杜绝手势穿透
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTopLevel,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(.86f),
                drawerShape = RoundedCornerShape(topEnd = 23.dp, bottomEnd = 23.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerTonalElevation = 0.dp
            ) {
                AppDrawerSheetContent(
                    currentRoute = currentRoute,
                    recent = recent,
                    onClose = { scope.launch { drawerState.close() } },
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        when {
                            route == Routes.newChat() -> navController.showHomeChat()
                            route.startsWith("chat/") -> navController.showHomeChat(route.substringAfter("chat/").substringBefore('?'))
                            currentRoute != route -> navController.safeNavigate(route)
                        }
                    }
                )
            }
        }
    ) {
        // Surface 提供统一背景色，避免窗口灰底露出
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val detailEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                slideInHorizontally(tween(260), initialOffsetX = { it / 12 }) + fadeIn(tween(260))
            }
            val detailExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                slideOutHorizontally(tween(180), targetOffsetX = { -it / 16 }) + fadeOut(tween(180))
            }
            val detailPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                slideInHorizontally(tween(260), initialOffsetX = { -it / 12 }) + fadeIn(tween(260))
            }
            val detailPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                slideOutHorizontally(tween(180), targetOffsetX = { it / 16 }) + fadeOut(tween(180))
            }

            NavHost(
                navController = navController,
                startDestination = Routes.NEW_CHAT,
                modifier = Modifier.fillMaxSize(),
                enterTransition = detailEnter,
                exitTransition = detailExit,
                popEnterTransition = detailPopEnter,
                popExitTransition = detailPopExit
            ) {
                composable(Routes.NEW_CHAT,
                    arguments = listOf(navArgument("agentId") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { entry ->
                    com.example.myapplication.ui.chat.NewChatScreen(navController, entry, openDrawer)
                }
                composable(Routes.CONVERSATIONS) {
                    ConversationsScreen(navController, openDrawer)
                }
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType },
                        navArgument("session") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
                ) { backStack ->
                    ChatScreen(
                        navController = navController,
                        conversationId = requireNotNull(backStack.arguments?.getString("conversationId")),
                        sessionKey = backStack.arguments?.getString("session") ?: requireNotNull(backStack.arguments?.getString("conversationId"))
                    )
                }
                composable(Routes.PROVIDERS) { ProvidersScreen(navController, openDrawer) }
                composable(
                    Routes.PROVIDER_EDIT,
                    arguments = listOf(navArgument("providerId") { type = NavType.StringType }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
                ) { backStack ->
                    ProviderEditScreen(
                        navController = navController,
                        providerId = backStack.arguments?.getString("providerId").orEmpty()
                    )
                }
                composable(Routes.FILES) { FilesScreen(navController, openDrawer) }
                composable(
                    Routes.FILE_VIEW,
                    arguments = listOf(navArgument("path") { type = NavType.StringType }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
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
                    arguments = listOf(navArgument("name") { type = NavType.StringType }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
                ) { backStack ->
                    SkillEditScreen(
                        navController = navController,
                        skillName = backStack.arguments?.getString("name").orEmpty()
                    )
                }
                composable(Routes.AGENTS) { AgentsScreen(navController, openDrawer) }
                composable(
                    Routes.AGENT_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
                ) { backStack ->
                    AgentEditScreen(
                        navController = navController,
                        agentId = backStack.arguments?.getString("id").orEmpty()
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(openDrawer, onOpenSection = { route ->
                        if (route in setOf(Routes.PROVIDERS, Routes.AGENTS, Routes.CONVERSATIONS, Routes.MEMORY, Routes.SKILLS)) {
                            navController.safeNavigateDirect(route)
                        }
                    })
                }
            }
        }
    }
}

/**
 * 侧边抽屉导航内容组件 (MD3e 规范重构)
 */
@Composable
fun AppDrawerSheetContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    recent: List<Conversation> = emptyList(),
    onClose: () -> Unit = {}
) {
    val entries = listOf(
        DrawerEntry(Routes.AGENTS, "Agents", Icons.Outlined.SmartToy),
        DrawerEntry(Routes.FILES, "工作区文件", Icons.Outlined.Folder),
        DrawerEntry(Routes.SKILLS, "Skills", Icons.Outlined.Inventory2),
        DrawerEntry(Routes.MEMORY, "长期记忆", Icons.Outlined.Psychology),
        DrawerEntry(Routes.PROVIDERS, "模型配置", Icons.Outlined.Memory)
    )
    Column(Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("AgentApp", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭导航", Modifier.size(20.dp)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Button(onClick = { onNavigate(Routes.newChat()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.myapplication.ui.theme.drawerActionColor(), contentColor = androidx.compose.ui.graphics.Color.White)) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("新对话")
            }
            Spacer(Modifier.height(16.dp))
            DrawerNavigationRow("搜索与全部对话", Icons.Outlined.Search) { onNavigate(Routes.CONVERSATIONS) }
            Text("最近", Modifier.padding(top = 20.dp, bottom = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recent.isEmpty()) Text("暂无对话", Modifier.padding(13.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.forEach { conversation ->
                key(conversation.id) {
                    Surface(onClick = { onNavigate(Routes.chat(conversation.id)) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 13.dp), contentAlignment = Alignment.CenterStart) {
                            Text(conversation.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                    }
                }
            }
            Text("我的工作环境", Modifier.padding(top = 22.dp, bottom = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entries.forEach { entry ->
                DrawerNavigationRow(entry.label, entry.icon, currentRoute == entry.route) { onNavigate(entry.route) }
            }
            Spacer(Modifier.height(15.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            DrawerNavigationRow("设置", Icons.Outlined.Tune, currentRoute == Routes.SETTINGS) { onNavigate(Routes.SETTINGS) }
            Text("本地优先 · 自由连接", Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrawerNavigationRow(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainer else androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, Modifier.padding(start = 13.dp), fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AppDrawerPreviewLight() {
    AgentTheme(themeMode = "light") {
        Surface(modifier = Modifier.fillMaxWidth(.86f), color = MaterialTheme.colorScheme.background) {
            AppDrawerSheetContent(
                currentRoute = Routes.CONVERSATIONS,
                onNavigate = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AppDrawerPreviewDark() {
    AgentTheme(themeMode = "dark") {
        Surface(modifier = Modifier.fillMaxWidth(.86f), color = MaterialTheme.colorScheme.background) {
            AppDrawerSheetContent(
                currentRoute = Routes.PROVIDERS,
                onNavigate = {}
            )
        }
    }
}
