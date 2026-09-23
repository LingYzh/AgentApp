package com.example.myapplication.ui.components

/** Keep hidden selections, but never expose IDs removed by a data refresh. */
internal fun availableSelection(selected: Set<String>, available: Set<String>): Set<String> =
    selected.intersect(available)

/** Select all applies only to the current search results. */
internal fun toggleVisibleSelection(
    selected: Set<String>,
    available: Set<String>,
    visible: Set<String>
): Set<String> {
    val valid = availableSelection(selected, available)
    val visibleValid = visible.intersect(available)
    return if (visibleValid.isEmpty()) valid
    else if (valid.containsAll(visibleValid)) valid - visibleValid
    else valid + visibleValid
}

/** Confirmation cannot add items selected after the dialog opened. */
internal fun confirmedSelection(
    snapshot: Set<String>,
    selectedNow: Set<String>,
    availableNow: Set<String>
): Set<String> = snapshot.intersect(selectedNow).intersect(availableNow)
