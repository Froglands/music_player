package com.froglands.musicplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF090D18)
private val Panel = Color(0xFF171F32)
private val Mint = Color(0xFF75F0D0)
private val Muted = Color(0xFF9AA8BD)

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var permissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) permissionLauncher.launch(permission)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, background = Ink, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                MusicPlayerApp(
                    player = player,
                    hasPermission = permissionGranted,
                    requestPermission = { permissionLauncher.launch(permission) },
                    loadTracks = { queryTracks() }
                )
            }
        }
    }

    private suspend fun queryTracks(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(
                    id,
                    cursor.getString(titleCol) ?: "Unknown title",
                    cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    ContentUris.withAppendedId(collection, id),
                    cursor.getLong(durationCol)
                )
            }
        }
        result
    }

    override fun onStop() {
        super.onStop()
        // Player is retained for configuration changes; lifecycle cleanup occurs in onDestroy.
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun MusicPlayerApp(
    player: ExoPlayer,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    loadTracks: suspend () -> List<Track>
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var playing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var onlineUrl by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(hasPermission) { if (hasPermission) tracks = loadTracks() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF101B31), Ink, Color(0xFF102A32)))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MUSIC PLAYER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Feel every beat.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GraphicEq, null, tint = Mint)
                }
            }

            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(5.dp)) {
                listOf("Library", "Online").forEachIndexed { index, label ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (tab == index) Mint.copy(alpha = .18f) else Color.Transparent)
                            .clickable { tab = index }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (tab == index) Mint else Muted, fontWeight = FontWeight.SemiBold) }
                }
            }

            AnimatedContent(tab, label = "content", modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Text("YOUR COLLECTION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Cari lagu atau artis…", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.White.copy(alpha = .12f),
                                focusedBorderColor = Mint, unfocusedTextColor = Color.White, focusedTextColor = Color.White
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                        if (!hasPermission) {
                            EmptyState("Izinkan akses audio", "Agar aplikasi bisa membaca lagu di perangkat.", "Izinkan", requestPermission)
                        } else if (tracks.isEmpty()) {
                            EmptyState("Belum ada lagu", "Tambahkan file audio ke perangkat, lalu tarik ulang daftar.", "Muat ulang") {
                                // Launched below via refresh signal.
                            }
                            TextButton(onClick = { }) { Text(" ") }
                        } else {
                            Text("${tracks.size} lagu ditemukan", color = Muted, fontSize = 12.sp)
                            LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(tracks.filter { it.title.contains(search, true) || it.artist.contains(search, true) }, key = { it.id }) { track ->
                                    TrackRow(track, selected?.id == track.id, onClick = {
                                        selected = track
                                        player.setMediaItem(MediaItem.fromUri(track.uri))
                                        player.prepare()
                                        player.play()
                                    })
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Text("PLAY FROM URL", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = onlineUrl, onValueChange = { onlineUrl = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("https://…/audio.mp3", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Link, null, tint = Muted) },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.White.copy(alpha = .12f),
                                focusedBorderColor = Mint, unfocusedTextColor = Color.White, focusedTextColor = Color.White
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val url = onlineUrl.trim()
                                if (url.startsWith("https://")) {
                                    selected = null
                                    player.setMediaItem(MediaItem.fromUri(url))
                                    player.prepare()
                                    player.play()
                                }
                            },
                            enabled = onlineUrl.startsWith("https://"),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                            shape = RoundedCornerShape(14.dp)
                        ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Putar stream") }
                        Spacer(Modifier.height(16.dp))
                        Text("Gunakan URL audio langsung dari sumber yang mengizinkan pemutaran. Tautan halaman web biasa tidak selalu bisa diputar.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }

            NowPlayingCard(
                track = selected,
                player = player,
                playing = playing,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onNext = {
                    val current = selected
                    val index = tracks.indexOfFirst { it.id == current?.id }
                    if (index >= 0 && index < tracks.lastIndex) {
                        val next = tracks[index + 1]
                        selected = next
                        player.setMediaItem(MediaItem.fromUri(next.uri)); player.prepare(); player.play()
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (active) Mint.copy(alpha = .13f) else Color.White.copy(alpha = .055f))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Mint)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) Icon(Icons.Default.Equalizer, null, tint = Mint) else Icon(Icons.Default.PlayArrow, null, tint = Muted)
    }
}

@Composable
private fun NowPlayingCard(
    track: Track?,
    player: ExoPlayer,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, playing, track) {
        while (true) {
            duration = player.duration.coerceAtLeast(0L)
            position = if (duration > 0) player.currentPosition.toFloat() / duration else 0f
            kotlinx.coroutines.delay(500)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xCC27364D), Color(0xCC172438))))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Mint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Album, null, tint = Mint, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track?.title ?: "Belum ada lagu diputar", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artist ?: "Pilih lagu dari library atau URL online", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.GraphicEq, null, tint = Mint)
        }
        Slider(
            value = position.coerceIn(0f, 1f),
            onValueChange = { position = it },
            onValueChangeFinished = { if (duration > 0) player.seekTo((position * duration).toLong()) },
            modifier = Modifier.fillMaxWidth().height(30.dp),
            colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Color.White.copy(alpha = .16f))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Default.Replay10, null, tint = Color.White) }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(54.dp).clip(CircleShape).background(Mint)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LibraryMusic, null, tint = Mint, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}
