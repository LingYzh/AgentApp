package com.example.myapplication

import com.example.myapplication.ui.components.codeDisplayLines
import org.junit.Assert.*
import org.junit.Test

class CodeDisplayLinesTest {
    @Test fun longJsonKeepsEveryCharacterInBoundedRows() {
        val text = "{\"nodes\":[\"" + "界面😀abc".repeat(10000) + "\"]}"
        val rows = codeDisplayLines(text)
        assertEquals(text, rows.joinToString(""))
        assertTrue(rows.all { it.length <= 512 })
        assertTrue(rows.none { it.first().isLowSurrogate() || it.last().isHighSurrogate() })
    }

    @Test fun ordinaryWhitespaceAndEmptyLinesRemainUnchanged() {
        val text = "  first\t\r\n\nlast\n"
        assertEquals(text, codeDisplayLines(text).joinToString("\n"))
        assertEquals(listOf(""), codeDisplayLines(""))
    }
}
