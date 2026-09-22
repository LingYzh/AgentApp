package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val LocalTransferFeedback = staticCompositionLocalOf<SnackbarHostState?> { null }

/** Feedback floats below the active app bar without resizing the reading area. */
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
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = { padding ->
            Box(Modifier.fillMaxSize()) {
                content(padding)
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())) {
                    if (transferFeedback?.currentSnackbarData != null) TopFeedbackHost(transferFeedback)
                    else snackbarHost()
                }
            }
        }
    )
}
