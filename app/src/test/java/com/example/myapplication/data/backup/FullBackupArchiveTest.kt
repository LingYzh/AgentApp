package com.example.myapplication.data.backup

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.store.FileStore
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FullBackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val pixels = byteArrayOf(1, 7, 3, 9, 11)
    private fun store(name: String) = FileStore(temp.newFolder(name))
    private fun export(store: FileStore): ByteArray = ByteArrayOutputStream().also { FullBackupArchive(store).export(it) }.toByteArray()
    private fun putAgent(store: FileStore, id: String = "agent"): AgentProfile {
        val path = store.saveAgentAvatar(id, ByteArrayInputStream(pixels))
        return AgentProfile(id = id, name = "自定义助手", avatarPath = path).also { store.saveAgents(listOf(it)) }
    }
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, content) -> output.putNextEntry(ZipEntry(name)); output.write(content); output.closeEntry() }
        }
    }.toByteArray()
    private fun agentIndex(store: FileStore, path: String?) = store.json.encodeToString(listOf(AgentProfile(id = "agent", avatarPath = path))).toByteArray()

    @Test fun roundTripRelocatesPicturesWithoutChangingSource() {
        val source = store("source"); val original = putAgent(source)
        source.saveConfig(AppConfig())
        val bytes = export(source)
        assertEquals(original.avatarPath, source.loadAgents().single().avatarPath)
        val target = store("target")
        val result = FullBackupArchive(target).restore(bytes.inputStream())
        val image = File(target.loadAgents().single().avatarPath!!)
        assertEquals(File(target.avatarsDir, "agent.png"), image)
        assertArrayEquals(pixels, image.readBytes())
        assertEquals(0, result.missingAvatars)
        ZipInputStream(bytes.inputStream()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (entry.name == "agents.json") {
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    assertTrue(text.contains("avatars/agent.png"))
                    assertFalse(text.contains(source.avatarsDir.absolutePath))
                }
                entry = input.nextEntry
            }
        }
    }

    @Test fun safetyBackupIncludesPreviousAvatarAndRestoresIt() {
        val target = store("target"); putAgent(target, "previous")
        val source = store("incoming"); putAgent(source, "next")
        val result = FullBackupArchive(target).restore(export(source).inputStream())
        assertFalse(File(target.avatarsDir, "previous.png").exists())
        val recovery = store("recovery")
        FullBackupArchive(recovery).restore(result.safetyBackup.inputStream())
        assertEquals("previous", recovery.loadAgents().single().id)
        assertArrayEquals(pixels, File(recovery.loadAgents().single().avatarPath!!).readBytes())
    }

    @Test fun oldArchiveWithoutPicturesDoesNotBorrowExistingImage() {
        val target = store("target"); putAgent(target)
        val archive = zip("agents.json" to agentIndex(target, "/data/user/0/old.package/files/avatars/agent.png"))
        val result = FullBackupArchive(target).restore(archive.inputStream())
        assertEquals(1, result.missingAvatars)
        assertNull(target.loadAgents().single().avatarPath)
        assertFalse(File(target.avatarsDir, "agent.png").exists())
        assertTrue(result.safetyBackup.length() > 0)
    }

    @Test fun legacyAbsoluteReferenceUsesArchiveImageOnNewRoot() {
        val target = store("target")
        val archive = zip("agents.json" to agentIndex(target, "/data/user/0/old/files/avatars/agent.png"), "avatars/agent.png" to pixels)
        assertEquals(0, FullBackupArchive(target).restore(archive.inputStream()).missingAvatars)
        assertEquals(File(target.avatarsDir, "agent.png").absolutePath, target.loadAgents().single().avatarPath)
    }

    @Test fun missingAndEmptyPicturesFallBackToEmoji() {
        val target = store("target")
        val archive = zip("agents.json" to agentIndex(target, "avatars/agent.png"), "avatars/agent.png" to byteArrayOf())
        assertEquals(1, FullBackupArchive(target).restore(archive.inputStream()).missingAvatars)
        assertNull(target.loadAgents().single().avatarPath)
    }

    @Test fun maliciousPathsAndInvalidJsonLeaveOriginalUntouched() {
        val target = store("target"); val original = putAgent(target)
        val invalidArchives = listOf(
            zip("../escaped.png" to pixels),
            zip("avatars/../../escaped.png" to pixels),
            zip("avatars\\escaped.png" to pixels),
            zip("agents.json" to "not json".toByteArray()),
            zip("agents.json" to agentIndex(target, "avatars/../config.json")),
            "not a zip".toByteArray()
        )
        invalidArchives.forEach { archive ->
            assertTrue(runCatching { FullBackupArchive(target).restore(archive.inputStream()) }.isFailure)
            assertEquals(original, target.loadAgents().single())
            assertArrayEquals(pixels, File(original.avatarPath!!).readBytes())
        }
        assertTrue(target.backupsDir.listFiles().isNullOrEmpty())
    }
}
