package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

/**
 * 顶级路由集合，用于判断是否开启侧滑抽屉手势
 */
val TopLevelRoutes = setOf(
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
                modifier = Modifier.width(ExpressiveTokens.DrawerWidth),
                drawerShape = ExpressiveTokens.DrawerShape,
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerTonalElevation = 2.dp
            ) {
                AppDrawerSheetContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        if (currentRoute != route) {
                            navController.safeNavigate(route)
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
            // MD3e 弹性物理弹簧视差过渡动效（下钻详情页）
            val detailEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { (it * 0.32f).toInt() },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(220))
            }
            val detailExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -(it * 0.15f).toInt() },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(180))
            }
            val detailPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -(it * 0.15f).toInt() },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220))
            }
            val detailPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { (it * 0.32f).toInt() },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(180))
            }

            NavHost(
                navController = navController,
                startDestination = Routes.CONVERSATIONS,
                modifier = Modifier.fillMaxSize(),
                // 平级页面切换采用 MD3e Shared Axis Z 柔和缩放淡入淡出
                enterTransition = {
                    fadeIn(animationSpec = tween(280, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))) +
                        scaleIn(
                            initialScale = 0.94f,
                            animationSpec = tween(280, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
                        )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                        scaleOut(
                            targetScale = 1.03f,
                            animationSpec = tween(180, easing = FastOutLinearInEasing)
                        )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.95f,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                        scaleOut(
                            targetScale = 1.04f,
                            animationSpec = tween(180, easing = FastOutLinearInEasing)
                        )
                }
            ) {
                composable(Routes.CONVERSATIONS) {
                    ConversationsScreen(navController, openDrawer)
                }
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                    enterTransition = detailEnter,
                    exitTransition = detailExit,
                    popEnterTransition = detailPopEnter,
                    popExitTransition = detailPopExit
                ) { backStack ->
                    ChatScreen(
                        navController = navController,
                        conversationId = backStack.arguments?.getString("conversationId").orEmpty()
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
                composable(Routes.SETTINGS) { SettingsScreen(openDrawer) }
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
    onNavigate: (String) -> Unit
) {
    val workspaceEntries = listOf(
        DrawerEntry(Routes.CONVERSATIONS, "对话", Icons.AutoMirrored.Filled.Chat),
        DrawerEntry(Routes.AGENTS, "Agents", Icons.Filled.Face),
        DrawerEntry(Routes.FILES, "文件工作区", Icons.Filled.Folder),
        DrawerEntry(Routes.PROVIDERS, "模型配置", Icons.Filled.Terminal)
    )

    val systemEntries = listOf(
        DrawerEntry(Routes.MEMORY, "记忆库", Icons.Filled.Star),
        DrawerEntry(Routes.SKILLS, "Skills 扩展", Icons.Filled.Build),
        DrawerEntry(Routes.SETTINGS, "设置 / 备份", Icons.Filled.Settings)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // MD3e Expressive Brand Header
        Surface(
            shape = ExpressiveTokens.HeaderCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "AgentApp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "智能助理与工作流",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = ExpressiveTokens.StatusBadgeShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "MD3e · Expressive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 分组一：工作空间
        Text(
            text = "工作空间",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 6.dp),
            fontWeight = FontWeight.SemiBold
        )

        workspaceEntries.forEach { entry ->
            val selected = currentRoute == entry.route
            NavigationDrawerItem(
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(entry.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                badge = {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                },
                selected = selected,
                onClick = { onNavigate(entry.route) },
                shape = ExpressiveTokens.PillShape,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 分组二：能力与偏好
        Text(
            text = "能力与偏好",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 6.dp),
            fontWeight = FontWeight.SemiBold
        )

        systemEntries.forEach { entry ->
            val selected = currentRoute == entry.route
            NavigationDrawerItem(
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(entry.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                badge = {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                },
                selected = selected,
                onClick = { onNavigate(entry.route) },
                shape = ExpressiveTokens.PillShape,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(16.dp))

        // Expressive Footer 卡片
        Surface(
            shape = ExpressiveTokens.CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "本地隔离沙盒 · 离线随时就绪",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppDrawerPreviewLight() {
    AgentTheme(themeMode = "light") {
        Surface(modifier = Modifier.width(ExpressiveTokens.DrawerWidth)) {
            AppDrawerSheetContent(
                currentRoute = Routes.CONVERSATIONS,
                onNavigate = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppDrawerPreviewDark() {
    AgentTheme(themeMode = "dark") {
        Surface(modifier = Modifier.width(ExpressiveTokens.DrawerWidth)) {
            AppDrawerSheetContent(
                currentRoute = Routes.PROVIDERS,
                onNavigate = {}
            )
        }
    }
}
