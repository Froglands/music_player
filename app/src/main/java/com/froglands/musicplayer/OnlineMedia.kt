package com.froglands.musicplayer

import android.annotation.SuppressLint
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
            if (videoId != null) {
                YouTubeEmbed(videoId, Modifier.fillMaxWidth().height(220.dp))
                Text("Diputar melalui player embed resmi YouTube. Fitur download YouTube tidak disediakan.", color = OnlineMuted, fontSize = 11.sp)
            } else {
                Text("Tempel tautan video YouTube untuk memutarnya di sini.", color = OnlineMuted, fontSize = 12.sp)
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
    val value = input.trim()
    val patterns = listOf(
        Regex("""(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})"""),
        Regex("""^([A-Za-z0-9_-]{11})$""")
    )
    return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp)),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
            }
        },
        update = { webView ->
            val target = "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&autoplay=0"
            if (webView.tag != target) {
                webView.tag = target
                webView.loadUrl(target)
            }
        }
    )
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
