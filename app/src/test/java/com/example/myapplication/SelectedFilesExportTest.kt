package com.example.myapplication

import com.example.myapplication.data.backup.SelectedFilesExport
import com.example.myapplication.data.store.FileStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SelectedFilesExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    @Test
    fun `workspace export contains only selected relative files`() {
        store.writeWorkspace("nested/one.txt", "one")
        store.writeWorkspace("two.txt", "two")

        val output = ByteArrayOutputStream()
        SelectedFilesExport.writeWorkspaceZip(store, setOf("nested/one.txt"), output)

        val entries = readZip(output.toByteArray())
        assertEquals(mapOf("nested/one.txt" to "one"), entries)
    }

    @Test(expected = SecurityException::class)
    fun `workspace export rejects traversal before creating archive`() {
        val output = ByteArrayOutputStream()
        SelectedFilesExport.writeWorkspaceZip(store, setOf("../outside.txt"), output)
    }

    @Test
    fun `skills export contains only selected skill directories`() {
        store.saveSkill("alpha", "first", "alpha body")
        store.saveSkill("beta", "second", "beta body")
        store.skillDir("alpha").resolve("examples.txt").writeText("example")

        val archive = store.exportSkillsZip(setOf("alpha"))
        val entries = readZip(archive.readBytes())

        assertTrue(entries.keys.contains("alpha/SKILL.md"))
        assertTrue(entries.keys.contains("alpha/examples.txt"))
        assertFalse(entries.keys.any { it.startsWith("beta/") })

        val restored = FileStore(tmp.newFolder("restored"))
        archive.inputStream().use { restored.importSkillZip(it) }
        assertEquals("alpha body", restored.readSkillBody("alpha"))
    }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
