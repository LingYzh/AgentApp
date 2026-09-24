package com.example.myapplication.ui.components

/** Bound each visual row without truncating the original record used for copy/export. */
internal fun codeDisplayLines(text: String): List<String> = buildList {
    text.split('\n').forEach { line ->
        if (line.isEmpty()) add("")
        var start = 0
        while (start < line.length) {
            var end = (start + 512).coerceAtMost(line.length)
            if (end < line.length && line[end - 1].isHighSurrogate() && line[end].isLowSurrogate()) end--
            add(line.substring(start, end))
            start = end
        }
    }
}
