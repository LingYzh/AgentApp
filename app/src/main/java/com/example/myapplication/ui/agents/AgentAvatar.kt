package com.example.myapplication.ui.agents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * Agent 统一圆角方形头像组件。
 * 兼容自选图片与 Emoji 图标。
 */
@Composable
fun AgentAvatar(
    emoji: String,
    avatarPath: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(size * 0.28f)
) {
    val context = LocalContext.current
    val file = avatarPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    // The archive restore can replace an image at the same path.
    val version = file?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    val request = remember(context, version) {
        file?.let {
            val cacheKey = version ?: it.absolutePath
            ImageRequest.Builder(context).data(it).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build()
        }
    }
    var loadFailed by remember(version) { mutableStateOf(false) }

    if (request != null && !loadFailed) {
        AsyncImage(
            model = request,
            onError = { loadFailed = true },
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
                .size(size)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = emoji.ifBlank { "🤖" },
                    fontSize = (size.value * 0.52f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
