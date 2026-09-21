package com.example.myapplication

import com.example.myapplication.agent.ToolExecutor
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.store.DiffLineType
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.data.store.FileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class FileDiffRegressionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `inline preview exposes deletion after a long unchanged prefix`() {
        val before = (1..600).joinToString("\n") { "line-$it" }
        val after = before.replace("line-500", "replacement")
        val rows = com.example.myapplication.ui.files.diffPreviewRows(FileChanges.diff(before, after).lines)
        assertTrue(rows.first().omitted > 400)
        assertTrue(rows.take(8).any { it.line?.type == DiffLineType.REMOVED })
        assertTrue(rows.take(8).any { it.line?.type == DiffLineType.ADDED })
        assertTrue(rows.size < 15)
    }

    @Test fun `diff operations reconstruct both versions with repeated lines`() {
        val random = Random(831)
        repeat(200) {
            val before = List(random.nextInt(1, 30)) { "line-${random.nextInt(8)}" }
            val after = List(random.nextInt(1, 30)) { "line-${random.nextInt(8)}" }
            val diff = FileChanges.diff(before.joinToString("\n"), after.joinToString("\n"))
            assertEquals(before, diff.lines.filter { it.type != DiffLineType.ADDED }.map { it.text })
            assertEquals(after, diff.lines.filter { it.type != DiffLineType.REMOVED }.map { it.text })
        }
    }

    @Test fun `very many lines fall back instead of producing unbounded diff`() {
        val diff = FileChanges.diff("old", "x\n".repeat(13000))
        assertTrue(diff.usedFallback)
        assertTrue(diff.lines.isEmpty())
        assertEquals("x\n".repeat(13000), diff.afterText)
    }

    @Test fun `snapshot budget does not truncate written file`() = runBlocking {
        val store = FileStore(temp.root)
        val large = "x".repeat(FileChanges.MAX_SNAPSHOT_CHARS + 1)
        var change: FileChange? = null
        val result = ToolExecutor(store, onFileChange = { change = it }).execute(
            Tools.WRITE_FILE, """{"path":"large.txt","content":"$large"}""")
        assertFalse(result.startsWith("错误"))
        assertEquals(large, store.workspaceFile("large.txt").readText())
        assertTrue(change!!.previewOmitted)
        assertTrue(change!!.after.length <= FileChanges.MAX_SNAPSHOT_CHARS)
    }

    @Test fun `failed write does not publish change`() = runBlocking {
        val store = FileStore(temp.root)
        var change: FileChange? = null
        val result = ToolExecutor(store, onFileChange = { change = it }).execute(
            Tools.WRITE_FILE, """{"path":"../escape.txt","content":"bad"}""")
        assertTrue(result.startsWith("错误"))
        assertNull(change)
    }
}
