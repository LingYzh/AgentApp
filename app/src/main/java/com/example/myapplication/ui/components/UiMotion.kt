package com.example.myapplication.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Outgoing animated content is visual only as soon as the user's target changes. */
fun Modifier.inertWhen(inert: Boolean): Modifier =
    if (!inert) this else this
        .clearAndSetSemantics { }
        .focusProperties { canFocus = false }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }

/** Conservative monospace width including tabs and wide glyphs; copying never uses this estimate. */
fun codeColumns(text: String): Int = text.fold(0) { total, char ->
    total + when { char == '\t' -> 4; char.code > 255 -> 2; else -> 1 }
}
