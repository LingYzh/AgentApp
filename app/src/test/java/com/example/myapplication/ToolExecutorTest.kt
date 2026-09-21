package com.example.myapplication

import com.example.myapplication.agent.ToolExecutor
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.store.FileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolExecutorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    @Test
    fun `save skill then use skill returns normalized body`() = runBlocking {
        val executor = ToolExecutor(store)
        val saved = executor.execute(
            Tools.SAVE_SKILL,
            """{"name":"Demo Skill","description":"demo","content":"# Body\nfollow steps"}"""
        )

        assertTrue(saved.contains("demo-skill"))
        val loaded = executor.execute(
            Tools.USE_SKILL,
            """{"name":"demo-skill"}"""
        )
        assertTrue(loaded.contains("# Body"))
        assertTrue(loaded.contains("follow steps"))
    }

    @Test
    fun `invalid tool arguments return a visible error`() = runBlocking {
        val result = ToolExecutor(store).execute(Tools.SAVE_SKILL, "{invalid")

        assertTrue(result.startsWith("错误: 工具参数不是合法 JSON"))
    }

    @Test
    fun `tool executor propagates cancellation from subagent`() {
        val executor = ToolExecutor(
            store = store,
            onRunSubagent = { _, _, _ -> throw CancellationException("stopped") }
        )

        try {
            runBlocking {
                executor.execute(Tools.RUN_SUBAGENT, """{"task":"work"}""")
            }
            throw AssertionError("expected CancellationException")
        } catch (e: CancellationException) {
            assertTrue(e.message?.contains("stopped") == true)
        }
    }

    @Test
    fun `write file callback captures existing content before and after`() = runBlocking {
        store.writeWorkspace("notes.txt", "before")
        var change: FileChange? = null
        val executor = ToolExecutor(store = store, onFileChange = { change = it })

        executor.execute(Tools.WRITE_FILE, """{"path":"notes.txt","content":"after"}""")

        assertEquals(
            FileChange("notes.txt", before = "before", after = "after", beforeExists = true),
            change
        )
    }

    @Test
    fun `write file callback marks new and same content writes`() = runBlocking {
        val changes = mutableListOf<FileChange>()
        val executor = ToolExecutor(store = store, onFileChange = { changes += it })

        executor.execute(Tools.WRITE_FILE, """{"path":"new.txt","content":"same"}""")
        executor.execute(Tools.WRITE_FILE, """{"path":"new.txt","content":"same"}""")

        assertEquals(2, changes.size)
        assertEquals(FileChange("new.txt", after = "same"), changes[0])
        assertEquals(
            FileChange("new.txt", before = "same", after = "same", beforeExists = true),
            changes[1]
        )
        assertFalse(changes.any { it.previewOmitted })
    }
}
