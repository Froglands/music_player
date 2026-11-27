package com.froglands.musicplayer

import android.annotation.SuppressLint
import android.net.Uri
import java.util.Locale
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private val OnlineMint = Color(0xFF75F0D0)
private val OnlineMuted = Color(0xFF9AA8BD)

private enum class OnlineMode { YOUTUBE_VIDEO, AUDIO_STREAM }

@Composable
fun OnlineMediaScreen(
    onPlayAudio: (String) -> Unit,
    onDownload: (String, Boolean) -> Unit,
    downloadBusy: Boolean,
    downloadMessage: String
) {
    var mode by remember { mutableStateOf(OnlineMode.YOUTUBE_VIDEO) }
    var youtubeUrl by remember { mutableStateOf("") }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var audioUrl by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("ONLINE MEDIA", color = OnlineMuted, fontSize = 11.sp)
        Text("Streaming online", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .06f)).padding(4.dp)) {
            listOf(OnlineMode.YOUTUBE_VIDEO to "YouTube Video", OnlineMode.AUDIO_STREAM to "Audio Stream").forEach { (item, label) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (mode == item) OnlineMint.copy(alpha = .18f) else Color.Transparent)
                    .clickable { mode = item }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (mode == item) OnlineMint else OnlineMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (mode == OnlineMode.YOUTUBE_VIDEO) {
            OutlinedTextField(value = youtubeUrl, onValueChange = { youtubeUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Tempel URL YouTube", color = OnlineMuted) }, leadingIcon = { Icon(Icons.Default.VideoLibrary, null, tint = OnlineMint) })
            Spacer(Modifier.height(12.dp))
            val videoId = remember(youtubeUrl) { extractYouTubeId(youtubeUrl) }
            Button(
                onClick = { activeVideoId = videoId },
                enabled = videoId != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OnlineMint, contentColor = Color(0xFF090D18)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("EKSEKUSI / PUTAR YOUTUBE")
            }
            if (videoId == null && youtubeUrl.isNotBlank()) {
                Text("URL YouTube tidak valid. Gunakan link youtube.com/watch?v=… atau youtu.be/…", color = Color(0xFFFF9A9A), fontSize = 11.sp)
            }
            if (activeVideoId != null) {
                Spacer(Modifier.height(12.dp))
                YouTubeEmbed(activeVideoId!!, Modifier.fillMaxWidth().height(230.dp))
                Text("Tekan tombol Play di dalam pemutar. Streaming memakai embed resmi YouTube; download YouTube tidak tersedia.", color = OnlineMuted, fontSize = 11.sp)
            } else {
                Text("Tempel link, lalu tekan tombol Eksekusi untuk membuka pemutar video di aplikasi.", color = OnlineMuted, fontSize = 12.sp)
            }
        } else {
            OutlinedTextField(value = audioUrl, onValueChange = { audioUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("https://server.com/audio.mp3", color = OnlineMuted) }, leadingIcon = { Icon(Icons.Default.Link, null, tint = OnlineMint) })
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onPlayAudio(audioUrl.trim()) }, enabled = audioUrl.startsWith("https://"),
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineMint, contentColor = Color(0xFF090D18))) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Putar")
                }
                OutlinedButton(onClick = { onDownload(audioUrl.trim(), false) }, enabled = audioUrl.startsWith("https://") && !downloadBusy) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Download")
                }
            }
            Text("Download hanya untuk URL file audio/video langsung yang memang mengizinkan pengunduhan. Kualitas file mengikuti sumber.", color = OnlineMuted, fontSize = 11.sp)
        }
        if (downloadBusy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = OnlineMint)
        }
        if (downloadMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(downloadMessage, color = OnlineMint, fontSize = 12.sp)
        }
    }
}

private fun extractYouTubeId(input: String): String? {
    val value = input.trim().trim('<', '>', '"', '\'')
    if (value.isBlank()) return null

    // Also accept a bare 11-character video ID.
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(value)) return value

    // Normalize links pasted without a scheme, then parse host/path/query.
    val normalized = if ("://" in value) value else "https://$value"
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null

    val candidate = when (host) {
        "youtu.be" -> uri.pathSegments.firstOrNull()
        "youtube.com", "m.youtube.com", "music.youtube.com" -> {
            uri.getQueryParameter("v") ?: run {
                val segments = uri.pathSegments
                when (segments.firstOrNull()?.lowercase(Locale.ROOT)) {
                    "embed", "shorts", "live", "v" -> segments.getOrNull(1)
                    else -> null
                }
            }
        }
        "youtube-nocookie.com" -> uri.pathSegments
            .takeIf { it.firstOrNull()?.equals("embed", ignoreCase = true) == true }
            ?.getOrNull(1)
        else -> null
    }
    return candidate?.takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(videoId: String, modifier: Modifier = Modifier) {
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }

    Box(modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString = settings.userAgentString
                        .replace("; wv", "")
                        .replace("Version/4.0 ", "")
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                            loadError = null
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                isLoading = false
                                loadError = "Gagal memuat pemutar (" + error.errorCode + "). Periksa koneksi internet lalu coba lagi."
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                isLoading = false
                                loadError = "Server pemutar mengembalikan HTTP " + errorResponse.statusCode + "."
                            }
                        }
                    }
                    webChromeClient = WebChromeClient()
                }
            },
            update = { webView ->
                val target = "youtube_embed_$videoId"
                if (webView.tag != target) {
                    webView.tag = target
                    val html = """
                        <!doctype html>
                        <html><head>
                        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                          html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}
                          #player{position:absolute;inset:0;width:100%;height:100%;border:0}
                        </style></head><body>
                        <iframe id="player"
                          src="https://www.youtube.com/embed/$videoId?playsinline=1&autoplay=0&controls=1&rel=0&enablejsapi=1"
                          title="YouTube video player"
                          referrerpolicy="strict-origin-when-cross-origin"
                          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                          allowfullscreen></iframe>
                        </body></html>
                    """.trimIndent()
                    webView.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
                }
            }
        )
        if (isLoading && loadError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(30.dp),
                color = OnlineMint,
                strokeWidth = 3.dp
            )
        }
        if (loadError != null) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(loadError!!, color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    loadError = null
                    isLoading = true
                }) { Text("Muat ulang", color = OnlineMint) }
            }
        }
    }
}
@Composable
fun OfflineMediaScreen(
    files: List<File>,
    onPlay: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("OFFLINE", color = OnlineMuted, fontSize = 11.sp)
        Text("Unduhan aplikasi", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        if (files.isEmpty()) Text("Belum ada file offline. Unduh file media yang mengizinkan penyimpanan dari tab Online.", color = OnlineMuted, fontSize = 13.sp)
        files.forEach { file ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .06f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Color.White, maxLines = 1)
                    Text(String.format("%.1f MB", file.length() / 1048576f), color = OnlineMuted, fontSize = 11.sp)
                }
                TextButton(onClick = { onPlay(file) }) { Text("Putar", color = OnlineMint) }
                TextButton(onClick = { onDelete(file) }) { Text("Hapus", color = Color(0xFFFF8A8A)) }
            }
        }
    }
}


suspend fun downloadPrivateMedia(context: android.content.Context, url: String): File =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        require(url.startsWith("https://")) { "Hanya URL HTTPS yang didukung." }
        val connection = java.net.URL(url).openConnection().apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "MusicPlayer/1.0")
        }
        connection.connect()
        val mime = connection.contentType?.substringBefore(";")?.lowercase().orEmpty()
        val pathName = url.substringBefore("?").substringAfterLast("/").ifBlank { "media_download" }
        val safeName = pathName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
        val extension = safeName.substringAfterLast(".", "").lowercase()
        val supported = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "mp4", "m4v", "webm", "mkv")
        require(mime.startsWith("audio/") || mime.startsWith("video/") || extension in supported) {
            "URL bukan file audio/video langsung (Content-Type: " + mime.ifBlank { "unknown" } + ")."
        }
        require(connection.contentLengthLong <= 500L * 1024L * 1024L || connection.contentLengthLong < 0) {
            "Ukuran file melebihi batas 500 MB."
        }
        val directory = File(context.filesDir, "offline_media").apply { mkdirs() }
        val target = File(directory, safeName.ifBlank { "media_" + System.currentTimeMillis() })
        val temporary = File(directory, target.name + ".part")
        connection.getInputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "Gagal menyimpan file unduhan." }
        target
    }
