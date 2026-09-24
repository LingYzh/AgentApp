package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

// Let the child consume normal scrolling, but stop edge overscroll/flings from dragging
// the sheet. The handle stays outside this boundary and can still dismiss the sheet.
private val sheetContentScrollBoundary = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        scrimColor = scrimColor,
        dragHandle = dragHandle
    ) {
        Column(
            Modifier.fillMaxWidth().nestedScroll(sheetContentScrollBoundary).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var movement = Offset.Zero
                    do {
                        // Main runs from children to parents: lists/selection get first use of
                        // each move. Consume leftover moves before the sheet's draggable sees
                        // them, including when a long press has cancelled the child's scroll.
                        // Leave down/up untouched so item clicks keep working.
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val primary = event.changes.first()
                        movement += primary.position - primary.previousPosition
                        event.changes.forEach { change ->
                            if (change.pressed && change.previousPressed &&
                                movement.getDistance() > viewConfiguration.touchSlop
                            ) {
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
            content = content
        )
    }
}
