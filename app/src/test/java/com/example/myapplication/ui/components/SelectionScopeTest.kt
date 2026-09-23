package com.example.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionScopeTest {
    @Test
    fun selectingSearchResultsKeepsHiddenSelection() {
        val available = setOf("hidden", "visible-1", "visible-2")
        val selected = toggleVisibleSelection(
            selected = setOf("hidden"),
            available = available,
            visible = setOf("visible-1", "visible-2")
        )

        assertEquals(available, selected)
        assertEquals(setOf("hidden"), toggleVisibleSelection(selected, available,
            setOf("visible-1", "visible-2")))
    }

    @Test
    fun emptySearchResultsCannotExpandSelection() {
        assertEquals(setOf("hidden"), toggleVisibleSelection(
            selected = setOf("hidden"),
            available = setOf("hidden", "other"),
            visible = emptySet()
        ))
    }

    @Test
    fun dataRefreshExcludesRemovedIdsFromOperations() {
        assertEquals(setOf("kept"), availableSelection(
            selected = setOf("kept", "removed"),
            available = setOf("kept", "new")
        ))
    }

    @Test
    fun deletionConfirmationCannotExpandBeyondOpeningSnapshot() {
        assertEquals(setOf("original"), confirmedSelection(
            snapshot = setOf("original", "removed"),
            selectedNow = setOf("original", "new"),
            availableNow = setOf("original", "new")
        ))
    }
}
