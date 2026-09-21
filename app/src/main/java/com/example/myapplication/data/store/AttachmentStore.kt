package com.example.myapplication.data.store

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.capabilitiesFor
import java.io.InputStream
import java.util.UUID

/** SAF 文件复制到应用工作区；不依赖外部 URI 的长期授权。 */
class AttachmentStore(private val store: FileStore) {
    fun importFile(name: String, mimeType: String, input: InputStream): MessageAttachment {
        val id = UUID.randomUUID().toString()
        val safeName = FileStore.sanitizeFileName(name).replace(Regex("[\\p{Cntrl}]"), "_")
            .take(120).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "attachment"
        val path = "attachments/$id/$safeName"
        val target = store.workspaceFile(path)
        check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
            return MessageAttachment(id, name.take(200), mimeType.lowercase(), target.length(), path)
        } catch (error: Exception) {
            target.delete()
            target.parentFile?.delete()
            throw error
        }
    }

    fun nativeRejection(config: ProviderConfig, attachment: MessageAttachment): String? {
        val caps = config.capabilitiesFor()
        val mime = attachment.mimeType
        val supported = when {
            mime in IMAGE_MIMES -> caps.image && config.type != ProviderType.CUSTOM
            mime == "application/pdf" -> caps.pdf && config.type != ProviderType.CUSTOM
            mime.startsWith("audio/") -> caps.audio && config.type == ProviderType.GEMINI
            mime.startsWith("video/") -> caps.video && config.type == ProviderType.GEMINI
            else -> false
        }
        return when {
            !supported -> "当前模型或协议未启用此文件的原生读取能力"
            else -> null
        }
    }

    fun validateNative(config: ProviderConfig, attachments: List<MessageAttachment>) {
        val native = attachments.filter { it.delivery == "native" }
        native.forEach { attachment ->
            nativeRejection(config, attachment)?.let {
                error("${attachment.name}：$it。请切换到支持的模型或调整模型能力配置")
            }
        }
    }

    fun fileFor(attachment: MessageAttachment): java.io.File {
        val file = store.workspaceFile(attachment.workspacePath)
        require(file.isFile) { "附件 ${attachment.name} 已缺失，请重新添加文件" }
        require(file.length() == attachment.sizeBytes) {
            "附件 ${attachment.name} 大小已改变，请重新添加"
        }
        return file
    }

    fun readBytes(attachment: MessageAttachment): ByteArray = fileFor(attachment).readBytes()

    /** read_file 读取媒体时保留快照，下一次模型请求附上原生内容，不按文本解码。 */
    fun readWorkspaceMedia(config: ProviderConfig, path: String): MessageAttachment? {
        return readMediaFile(config, store.workspaceFile(path))
    }

    /** Tool execution validates access before providing an absolute device file here. */
    fun readMediaFile(config: ProviderConfig, file: java.io.File): MessageAttachment? {
        val path = file.path
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> return null
        }
        require(file.isFile) { "文件不存在：$path" }
        val reference = MessageAttachment(name = file.name, mimeType = mime,
            sizeBytes = file.length(), workspacePath = path, delivery = "native")
        nativeRejection(config, reference)?.let { error("$path：$it，请选择支持该文件的模型") }
        return file.inputStream().use { importFile(file.name, mime, it) }.copy(delivery = "native")
    }

    fun messageText(message: ChatMessage): String = buildString {
        append(message.content)
        message.attachments.forEach { attachment ->
            append(if (message.originToolCallId == null) "\n\n[用户附件：" else "\n\n[工具读取的附件：")
            append(attachment.name.replace('\n', ' ').replace('\r', ' '))
            append("；类型：${attachment.mimeType}；工作区路径：${attachment.workspacePath}")
            append(if (attachment.delivery == "native") "；已附原生内容]" else "；请按需用 read_file 读取（图片/PDF 需要模型支持对应能力）]")
        }
    }

    companion object {
        val IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    }
}
