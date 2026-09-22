package com.example.myapplication.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.ui.components.MarkdownContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.min
import kotlin.math.roundToInt

/** The built-in preview modes are deliberately small and local-only. */
internal enum class AttachmentPreviewKind {
    IMAGE,
    PDF,
    MARKDOWN,
    TEXT,
    OTHER
}

/**
 * Classifies an attachment using its declared MIME type first and its filename as a fallback.
 * This is pure so callers can test the routing without starting Android rendering code.
 */
internal fun classifyAttachmentPreview(attachment: MessageAttachment): AttachmentPreviewKind {
    val mime = attachment.mimeType.trim().lowercase()
    val extension = attachment.name.substringAfterLast('.', "").lowercase()

    if (mime.startsWith("image/") || extension in IMAGE_EXTENSIONS) {
        return AttachmentPreviewKind.IMAGE
    }
    if (mime == "application/pdf" || extension == "pdf") {
        return AttachmentPreviewKind.PDF
    }
    if (mime == "text/markdown" || mime == "text/x-markdown" || extension in MARKDOWN_EXTENSIONS) {
        return AttachmentPreviewKind.MARKDOWN
    }
    if (mime.startsWith("text/") || mime in TEXT_MIME_TYPES || extension in TEXT_EXTENSIONS) {
        return AttachmentPreviewKind.TEXT
    }
    return AttachmentPreviewKind.OTHER
}

/** Read-only attachment dialog. The parent owns visibility and supplies a validated workspace file. */
@Composable
internal fun AttachmentPreviewDialog(
    attachment: MessageAttachment,
    file: File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val kind = remember(attachment) { classifyAttachmentPreview(attachment) }
    var openError by remember(attachment.id, file) { mutableStateOf<String?>(null) }
    var fileAvailable by remember(attachment.id, file) {
        mutableStateOf<Boolean?>(if (file == null) false else null)
    }
    var autoOpenAttempted by remember(attachment.id, file) { mutableStateOf(false) }

    LaunchedEffect(attachment.id, file) {
        if (file == null) {
            fileAvailable = false
        } else {
            fileAvailable = withContext(Dispatchers.IO) {
                runCatching { file.isFile && file.canRead() }.getOrDefault(false)
            }
        }
    }

    fun openWithOtherApp() {
        if (file == null || fileAvailable != true) {
            openError = "文件不可用，无法交给其他应用打开。"
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachmentMimeType(attachment, file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "用其他应用打开").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            onDismiss()
        } catch (_: ActivityNotFoundException) {
            openError = "系统中没有可以打开此文件的应用。"
        } catch (error: IllegalArgumentException) {
            openError = "无法共享此文件：${error.message ?: "文件路径不受支持"}"
        } catch (error: SecurityException) {
            openError = "系统拒绝了对文件的访问：${error.message ?: "请检查文件权限"}"
        } catch (error: Exception) {
            openError = "打开文件失败：${error.message ?: "未知错误"}"
        }
    }

    LaunchedEffect(kind, fileAvailable) {
        if (kind == AttachmentPreviewKind.OTHER && fileAvailable == true && !autoOpenAttempted) {
            autoOpenAttempted = true
            openWithOtherApp()
        }
    }

    val availableFile = file.takeIf { fileAvailable == true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = previewTypeLabel(kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        file == null || fileAvailable == false -> PreviewMessage(
                            title = "文件不可用",
                            message = "附件文件不存在或已被移除，请重新添加文件。"
                        )
                        fileAvailable == null -> LoadingPreview()
                        kind == AttachmentPreviewKind.IMAGE -> ImageAttachmentPreview(availableFile!!, attachment.name)
                        kind == AttachmentPreviewKind.PDF -> PdfAttachmentPreview(availableFile!!)
                        kind == AttachmentPreviewKind.MARKDOWN -> TextAttachmentPreview(availableFile!!, markdown = true)
                        kind == AttachmentPreviewKind.TEXT -> TextAttachmentPreview(availableFile!!, markdown = false)
                        else -> PreviewMessage(
                            title = "暂不支持内置预览",
                            message = "此文件类型可以交给系统中的其他应用打开。"
                        )
                    }
                }

                openError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = ::openWithOtherApp,
                        enabled = fileAvailable == true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("用其他应用打开")
                    }
                    UiTextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentPreview(file: File, name: String) {
    var failed by remember(file) { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(context, file) {
        ImageRequest.Builder(context)
            .data(file)
            // Keep Coil from decoding an arbitrarily large original into the dialog.
            .size(MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            .crossfade(true)
            .build()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            onSuccess = { failed = false },
            onError = { failed = true }
        )
        if (failed) {
            PreviewMessage(
                title = "图片预览失败",
                message = "图片无法读取，可以尝试用其他应用打开。"
            )
        }
    }
}

@Composable
private fun TextAttachmentPreview(file: File, markdown: Boolean) {
    var state by remember(file, markdown) {
        mutableStateOf<TextPreviewState>(TextPreviewState.Loading)
    }
    LaunchedEffect(file, markdown) {
        state = TextPreviewState.Loading
        state = try {
            TextPreviewState.Ready(withContext(Dispatchers.IO) { readTextPreview(file) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            TextPreviewState.Error(readableError("读取文本失败", error))
        }
    }

    when (val current = state) {
        TextPreviewState.Loading -> LoadingPreview()
        is TextPreviewState.Error -> PreviewMessage("无法预览文本", current.message)
        is TextPreviewState.Ready -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (current.preview.truncated) {
                    Text(
                        text = "文件较大，此处仅显示前 ${TEXT_PREVIEW_LIMIT_BYTES / 1024} KB；原文件未被修改。",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                if (markdown) {
                    MarkdownContent(current.preview.text, Modifier.fillMaxWidth().padding(8.dp))
                } else {
                    SelectionContainer {
                        Text(
                            text = current.preview.text.ifEmpty { "（空文件）" },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfAttachmentPreview(file: File) {
    var pageCount by remember(file) { mutableIntStateOf(-1) }
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        pageCount = -1
        pageIndex = 0
        error = null
        try {
            val count = withContext(Dispatchers.IO) { readPdfPageCount(file) }
            if (count <= 0) {
                error = "PDF 没有可显示的页面。"
                pageCount = 0
            } else {
                pageCount = count
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = readableError("读取 PDF 失败", failure)
            pageCount = 0
        }
    }

    LaunchedEffect(file, pageIndex, pageCount) {
        if (pageCount <= 0 || pageIndex !in 0 until pageCount) return@LaunchedEffect
        bitmap = null
        error = null
        var rendered: Bitmap? = null
        try {
            // Assign inside the IO block so a cancellation while dispatching back still leaves
            // the pending bitmap in `rendered`, where the finally block can recycle it safely.
            withContext(Dispatchers.IO) {
                rendered = renderPdfPage(file, pageIndex)
            }
            currentCoroutineContext().ensureActive()
            bitmap = rendered
            // Ownership now belongs to the Compose state; only an unpublished result is recycled.
            rendered = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = readableError("渲染 PDF 页面失败", failure)
        } finally {
            rendered?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                error != null -> PreviewMessage("无法预览 PDF", error!!)
                pageCount < 0 || bitmap == null -> LoadingPreview()
                else -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF 第 ${pageIndex + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
        }
        if (pageCount > 0 && error == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一页")
                }
                Text(
                    text = "第 ${pageIndex + 1} / $pageCount 页",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                    enabled = pageIndex < pageCount - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一页")
                }
            }
        }
    }
}

@Composable
private fun LoadingPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text("正在读取…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewMessage(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun previewTypeLabel(kind: AttachmentPreviewKind): String = when (kind) {
    AttachmentPreviewKind.IMAGE -> "图片预览"
    AttachmentPreviewKind.PDF -> "PDF 只读预览"
    AttachmentPreviewKind.MARKDOWN -> "Markdown 只读预览"
    AttachmentPreviewKind.TEXT -> "文本只读预览"
    AttachmentPreviewKind.OTHER -> "系统文件预览"
}

private fun attachmentMimeType(attachment: MessageAttachment, file: File): String {
    val declared = attachment.mimeType.trim().lowercase()
    if (declared.isNotBlank() && declared != "application/octet-stream") return declared
    val guessed = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
    return guessed ?: declared.takeIf { it.isNotBlank() } ?: "*/*"
}

private data class TextPreview(
    val text: String,
    val truncated: Boolean
)

private sealed interface TextPreviewState {
    data object Loading : TextPreviewState
    data class Ready(val preview: TextPreview) : TextPreviewState
    data class Error(val message: String) : TextPreviewState
}

private fun readTextPreview(file: File): TextPreview {
    val bytes = ByteArray(TEXT_PREVIEW_LIMIT_BYTES + 1)
    var count = 0
    file.inputStream().use { input ->
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read < 0) break
            if (read == 0) continue
            count += read
        }
    }
    val truncated = count > TEXT_PREVIEW_LIMIT_BYTES
    val textBytes = bytes.copyOf(min(count, TEXT_PREVIEW_LIMIT_BYTES))
    return TextPreview(decodeText(textBytes), truncated)
}

private fun decodeText(bytes: ByteArray): String {
    return when {
        bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) ->
            String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        else -> String(bytes, StandardCharsets.UTF_8)
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun readPdfPageCount(file: File): Int {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        val renderer = PdfRenderer(descriptor)
        try {
            return renderer.pageCount
        } finally {
            renderer.close()
        }
    } finally {
        descriptor.close()
    }
}

/** Render exactly one bounded page and close all native PDF resources before returning. */
private suspend fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    var rendered: Bitmap? = null
    try {
        currentCoroutineContext().ensureActive()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                require(pageIndex in 0 until renderer.pageCount) { "PDF 页面不存在" }
                val page = renderer.openPage(pageIndex)
                try {
                    val sourceWidth = page.width.coerceAtLeast(1)
                    val sourceHeight = page.height.coerceAtLeast(1)
                    val scale = min(
                        PDF_MAX_WIDTH_PX / sourceWidth.toFloat(),
                        PDF_MAX_HEIGHT_PX / sourceHeight.toFloat()
                    )
                    val width = (sourceWidth * scale).roundToInt()
                        .coerceIn(1, PDF_MAX_WIDTH_PX)
                    val height = (sourceHeight * scale).roundToInt()
                        .coerceIn(1, PDF_MAX_HEIGHT_PX)
                    rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    rendered!!.eraseColor(AndroidColor.WHITE)
                    page.render(
                        rendered,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    currentCoroutineContext().ensureActive()
                    return rendered!!
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    } catch (failure: Throwable) {
        rendered?.takeUnless { it.isRecycled }?.recycle()
        throw failure
    }
}

private fun readableError(operation: String, error: Exception): String {
    val detail = error.message?.trim()?.takeIf { it.isNotEmpty() }
    return if (detail == null) "$operation。" else "$operation：$detail"
}

private const val TEXT_PREVIEW_LIMIT_BYTES = 512 * 1024
private const val MAX_IMAGE_DIMENSION = 2048
private const val PDF_MAX_WIDTH_PX = 1800
private const val PDF_MAX_HEIGHT_PX = 2400

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkdn")
private val TEXT_EXTENSIONS = setOf(
    "txt", "log", "csv", "tsv", "json", "ndjson", "xml", "yaml", "yml", "toml", "ini", "conf",
    "properties", "env", "html", "htm", "css", "js", "jsx", "ts", "tsx", "kt", "kts", "java",
    "py", "rb", "go", "rs", "swift", "c", "h", "cc", "cpp", "cxx", "hpp", "sql", "sh", "bash",
    "zsh", "fish", "bat", "cmd", "ps1", "gradle", "groovy", "diff", "patch", "tex", "srt", "vtt"
)
private val TEXT_MIME_TYPES = setOf(
    "application/json", "application/ld+json", "application/manifest+json", "application/ndjson",
    "application/xml", "application/xhtml+xml", "application/yaml", "application/x-yaml", "application/csv",
    "application/sql", "application/javascript", "application/x-javascript", "application/x-sh",
    "application/x-httpd-php"
)
