package com.example.myapplication

import com.example.myapplication.diagnostics.DiagnosticLog
import com.example.myapplication.diagnostics.RuntimeDiagnostics
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `rotation is bounded and fields remain valid json lines`() {
        val log = DiagnosticLog(temp.root, maxBytes = 800)
        repeat(60) { log.append("tool_end", mapOf("path" to "name\nwith\"quotes", "index" to it)) }
        val files = temp.root.listFiles()!!.toList()
        assertEquals(3, files.size)
        files.forEach { file ->
            assertTrue(file.length() <= 800)
            file.readLines().forEach { Json.parseToJsonElement(it) }
        }
        assertTrue(temp.root.resolve("runtime.jsonl").readText().contains("59"))
    }

    @Test fun `android access errors include wrapped causes`() {
        assertTrue(RuntimeDiagnostics.permissionFailure(java.io.IOException("open failed: EACCES (Permission denied)")))
        assertTrue(RuntimeDiagnostics.permissionFailure(Exception("wrapper", Exception("EPERM"))))
        assertFalse(RuntimeDiagnostics.permissionFailure(Exception("file not found")))
    }
}
