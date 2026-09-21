package com.example.myapplication

import com.example.myapplication.data.model.*
import com.example.myapplication.data.store.AttachmentStore
import com.example.myapplication.data.store.FileStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class AttachmentStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun config(type: ProviderType = ProviderType.OPENAI) = ProviderConfig(
        type = type, model = "vision", capabilityOverrides = mapOf("vision" to ModelCapabilities(image = true, pdf = true)))

    @Test fun `attachment round trips without retaining external uri or inline bytes in message`() {
        val store = FileStore(temp.root)
        val files = AttachmentStore(store)
        val attachment = files.importFile("../photo.png", "image/png", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertTrue(attachment.workspacePath.startsWith("attachments/"))
        assertArrayEquals(byteArrayOf(1, 2, 3), files.readBytes(attachment))
        val message = ChatMessage(role = "user", attachments = listOf(attachment))
        val conversation = Conversation(messages = mutableListOf(message))
        store.saveConversation(conversation)
        assertEquals(listOf(attachment), store.loadConversation(conversation.id)!!.messages.single().attachments)
        assertTrue(files.messageText(message).contains(attachment.workspacePath))
    }

    @Test fun `unknown model and protocol cannot silently receive native file`() {
        val files = AttachmentStore(FileStore(temp.root))
        val image = files.importFile("image.png", "image/png", ByteArrayInputStream(byteArrayOf(1))).copy(delivery = "native")
        assertNotNull(files.nativeRejection(ProviderConfig(model = "vision"), image))
        assertNull(files.nativeRejection(config(), image))
        assertNotNull(files.nativeRejection(config(ProviderType.CUSTOM), image))
        try { files.validateNative(ProviderConfig(), listOf(image)); fail("should reject unknown capability") }
        catch (_: IllegalStateException) { }
        files.validateNative(ProviderConfig(), listOf(image.copy(delivery = "workspace")))
    }

    @Test fun `missing or modified attachment fails before payload encoding`() {
        val store = FileStore(temp.root)
        val files = AttachmentStore(store)
        val image = files.importFile("a.png", "image/png", ByteArrayInputStream(byteArrayOf(1)))
        store.workspaceFile(image.workspacePath).writeBytes(byteArrayOf(1, 2))
        try { files.readBytes(image); fail("changed size") } catch (_: IllegalArgumentException) { }
        store.workspaceFile(image.workspacePath).delete()
        try { files.readBytes(image); fail("missing") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `attachment path cannot escape workspace`() {
        val files = AttachmentStore(FileStore(temp.root))
        val image = MessageAttachment(name = "secret", mimeType = "image/png", sizeBytes = 1, workspacePath = "attachments/../../secret")
        try { files.readBytes(image); fail("path traversal") } catch (_: SecurityException) { }
    }

    @Test fun `failed import removes partial workspace file`() {
        val store = FileStore(temp.root)
        val stream = object : InputStream() {
            var first = true
            override fun read(): Int = throw java.io.IOException("read failed")
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (!first) throw java.io.IOException("read failed")
                first = false
                buffer[off] = 1
                return 1
            }
        }
        try { AttachmentStore(store).importFile("broken.bin", "application/octet-stream", stream); fail("read failure") }
        catch (_: java.io.IOException) { }
        assertTrue(store.listWorkspace().isEmpty())
    }

    @Test fun `large attachments and workspace media are not rejected by local size limits`() {
        val store = FileStore(temp.root)
        val files = AttachmentStore(store)
        var remaining = 21L * 1024 * 1024
        val stream = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len.toLong(), remaining).toInt()
                remaining -= count
                return count
            }
        }
        val imported = files.importFile("huge.png", "image/png", stream).copy(delivery = "native")
        assertEquals(21L * 1024 * 1024, imported.sizeBytes)
        files.validateNative(config(), listOf(imported, imported))
        val media = files.readWorkspaceMedia(config(), imported.workspacePath)!!
        assertEquals(imported.sizeBytes, files.fileFor(media).length())
        assertEquals("native", media.delivery)
    }
}
