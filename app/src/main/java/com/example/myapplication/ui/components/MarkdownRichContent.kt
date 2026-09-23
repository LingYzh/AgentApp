package com.example.myapplication.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import android.util.Base64

/** Only math paragraphs and diagrams use an offline renderer; ordinary text stays native. */
@Composable
internal fun MarkdownRichBlock(source: String, kind: String, modifier: Modifier = Modifier) {
    val payload = remember(source, kind) { JSONObject().put("kind", kind).put("source", source) }
    OfflineMarkdownView(payload, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface, modifier)
}

@Composable
internal fun MarkdownMathText(
    runs: List<MarkdownRun>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onFootnote: (MarkdownRun) -> Unit = {}
) {
    val payload = remember(runs) {
        JSONObject().put("kind", "inline").put("runs", JSONArray().apply {
            runs.forEachIndexed { index, run ->
                put(JSONObject().put("text", run.text).put("math", run.math)
                    .put("code", run.code).put("bold", run.bold).put("italic", run.italic)
                    .put("strike", run.strike).put("highlight", run.highlight)
                    .put("superscript", run.superscript).put("subscript", run.subscript)
                    .put("link", when {
                        run.footnoteId != null -> "footnote://reference/$index"
                        run.imageUrl != null -> run.imageUrl
                        else -> run.link
                    }))
            }
        })
    }
    OfflineMarkdownView(payload, style, color, modifier) { index -> runs.getOrNull(index)?.let(onFootnote) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfflineMarkdownView(
    payload: JSONObject,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    onFootnote: (Int) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val currentFootnote by rememberUpdatedState(onFootnote)
    val fontScale = LocalDensity.current.fontScale
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = MaterialTheme.colorScheme.primary
    val size = (style.fontSize.value.takeIf { it.isFinite() } ?: 16f) * fontScale
    val lineHeight = (style.lineHeight.value.takeIf { it.isFinite() } ?: 29f) * fontScale
    val html = remember(payload, color, accent, size, lineHeight, dark) {
        val data = JSONObject(payload.toString()).put("dark", dark).toString()
        val encoded = Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val ink = "#%06x".format(color.toArgb() and 0xffffff)
        val link = "#%06x".format(accent.toArgb() and 0xffffff)
        val library = if (payload.optString("kind") == "mermaid") "mermaid.min.js" else "katex.min.js"
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src data:; connect-src 'none'; frame-src 'none'">
            <link rel="stylesheet" href="katex.min.css">
            <style>html,body{margin:0;padding:0;background:transparent;color:$ink;font-family:sans-serif;font-size:${size}px;line-height:${lineHeight}px;}#content{padding:2px 0;overflow-x:auto;overflow-wrap:anywhere;white-space:pre-wrap;}a{color:$link;}code{font-family:monospace;background:#8882;border-radius:4px;padding:1px 3px;}.highlight{background:#c9a84655;}svg{max-width:100%;height:auto;}.katex-display{margin:8px 0;}.katex{white-space:normal;}</style>
            <script src="$library"></script><script src="render.js"></script></head><body><div id="content"></div>
            <script>renderMarkdownPayload(JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('$encoded'),c=>c.charCodeAt(0)))));</script></body></html>""".trimIndent()
    }
    var height by remember { mutableIntStateOf(48) }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(height.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                settings.setSupportMultipleWindows(false)
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        title?.removePrefix("height:")?.toIntOrNull()?.let { height = it.coerceIn(24, 3000) }
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                        val uri = request.url
                        val path = uri.path.orEmpty().removePrefix("/")
                        if (uri.scheme == "https" && uri.host == "markdown.local" &&
                            path.matches(Regex("[A-Za-z0-9_./-]+")) && ".." !in path) {
                            val mime = when (path.substringAfterLast('.')) {
                                "js" -> "application/javascript"
                                "css" -> "text/css"
                                "woff2" -> "font/woff2"
                                "woff" -> "font/woff"
                                "ttf" -> "font/ttf"
                                else -> "application/octet-stream"
                            }
                            runCatching { context.assets.open("markdown/$path") }.getOrNull()?.let {
                                return WebResourceResponse(mime, "UTF-8", it)
                            }
                        }
                        // No remote resources, file URLs, content providers or native JS bridge.
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (request.hasGesture()) {
                            if (uri.scheme == "footnote") uri.lastPathSegment?.toIntOrNull()?.let(currentFootnote)
                            else if (uri.scheme in setOf("https", "http", "mailto", "tel")) {
                                runCatching { uriHandler.openUri(uri.toString()) }
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL("https://markdown.local/", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view -> view.stopLoading(); view.destroy() }
    )
}
