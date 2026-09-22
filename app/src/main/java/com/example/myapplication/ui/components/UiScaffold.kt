package com.example.myapplication.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val LocalTransferFeedback = staticCompositionLocalOf<SnackbarHostState?> { null }

/** Feedback participates in layout below the active app bar, never over the composer. */
@Composable
fun UiScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val transferFeedback = LocalTransferFeedback.current
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                topBar()
                // Only this feedback slot owns height animation; streaming content does not.
                Box(Modifier.fillMaxWidth().animateContentSize(tween(200)), contentAlignment = Alignment.TopCenter) {
                    if (transferFeedback?.currentSnackbarData != null) SnackbarHost(transferFeedback)
                    else snackbarHost()
                }
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content
    )
}
