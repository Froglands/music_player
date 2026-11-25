package com.froglands.musicplayer

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

private val Ink = Color(0xFF090D18)
private val Panel = Color(0xFF171F32)
private val Mint = Color(0xFF75F0D0)
private val Muted = Color(0xFF9AA8BD)
private val TargetFrequencies = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 10000f, 12000f, 16000f)
private val FrequencyLabels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "10k", "12k", "16k")

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long, val album: String = "Unknown album", val albumArtist: String = "", val albumId: Long = -1L, val year: Int = 0, val trackNumber: Int = 0, val composer: String = "")

private enum class Screen { LIBRARY, ONLINE, PLAYER, EQUALIZER, SETTINGS }
private enum class SpectrumStyle(val title: String, val description: String) { WAVE("Wave", "Gelombang halus"), BARS("Bars", "Batang gradasi"), MIRROR("Mirror", "Batang simetris"), CIRCULAR("Circular", "Melingkar"), LINE("Line", "Garis minimalis"), FILLED("Filled", "Gelombang terisi"), DOTS("Dots", "Titik responsif"), PULSE("Pulse", "Pulse simetris") }

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: PlayerNotificationManager
    private var permissionGranted by mutableStateOf(false)
    private var recordPermission by mutableStateOf(false)
    private var notificationPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    private val recordPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        recordPermission = it
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationPermission = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
        notificationManager = PlayerNotificationManager.Builder(this, 1001, "music_playback")
            .setChannelNameResourceId(android.R.string.untitled)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence =
                    player.mediaMetadata.title ?: player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment ?: "Music Player"
                override fun getCurrentContentText(player: Player): CharSequence? =
                    player.mediaMetadata.artist ?: player.mediaMetadata.albumTitle ?: "Now playing"
                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    return PendingIntent.getActivity(this@MainActivity, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
                override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): android.graphics.Bitmap? {
                    val data = player.mediaMetadata.artworkData ?: return null
                    return BitmapFactory.decodeByteArray(data, 0, data.size)
                }
            })
            .setSmallIconResourceId(android.R.drawable.ic_media_play)
            .build()
        notificationManager.setMediaSessionToken(mediaSession.platformToken)
        notificationManager.setUseNextAction(true)
        notificationManager.setUsePreviousAction(true)
        notificationManager.setUsePlayPauseActions(true)
        notificationManager.setPlayer(player)
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        notificationPermission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33 && !notificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!permissionGranted) permissionLauncher.launch(permission)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, background = Ink, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                MusicPlayerApp(
                    player = player,
                    hasPermission = permissionGranted,
                    hasRecordPermission = recordPermission,
                    requestPermission = { permissionLauncher.launch(permission) },
                    requestRecordPermission = { recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    loadTracks = { queryTracks() }
                )
            }
        }
    }

    private suspend fun queryTracks(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.COMPOSER)
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val composerCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(id, cursor.getString(titleCol) ?: "Unknown title",
                    cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    ContentUris.withAppendedId(collection, id), cursor.getLong(durationCol), cursor.getString(albumCol) ?: "Unknown album", cursor.getString(albumArtistCol) ?: "", cursor.getLong(albumIdCol), cursor.getInt(yearCol), cursor.getInt(trackCol), cursor.getString(composerCol) ?: "")
            }
        }
        result
    }

    override fun onDestroy() {
        notificationManager.setPlayer(null)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun MusicPlayerApp(
    player: ExoPlayer,
    hasPermission: Boolean,
    hasRecordPermission: Boolean,
    requestPermission: () -> Unit,
    requestRecordPermission: () -> Unit,
    loadTracks: suspend () -> List<Track>
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var playing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var onlineUrl by remember { mutableStateOf("") }
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    var libraryScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var lyricsEnabled by rememberSaveable { mutableStateOf(true) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { appContext.getSharedPreferences("music_player_preferences", android.content.Context.MODE_PRIVATE) }
    var lyricsText by remember { mutableStateOf("") }
    val lyricsKey = selected?.let { "lyrics_${it.id}" } ?: player.currentMediaItem?.localConfiguration?.uri?.toString()?.let { "lyrics_$it" } ?: "lyrics_empty"
    LaunchedEffect(lyricsKey) { lyricsText = preferences.getString(lyricsKey, "") ?: "" }
    var spectrumStyle by remember { mutableStateOf(SpectrumStyle.WAVE) }
    LaunchedEffect(Unit) {
        lyricsEnabled = preferences.getBoolean("lyrics_enabled", true)
        spectrumStyle = runCatching { SpectrumStyle.valueOf(preferences.getString("spectrum_style", "WAVE") ?: "WAVE") }.getOrDefault(SpectrumStyle.WAVE)
    }
    var audioSessionId by remember { mutableIntStateOf(0) }
    var liveMetadata by remember { mutableStateOf<androidx.media3.common.MediaMetadata?>(null) }
    var spectrum by remember { mutableStateOf(List(28) { 0.025f }) }
    var spectrumStatus by remember { mutableStateOf("Putar lagu untuk membaca spektrum audio.") }
    var eqEnabled by remember { mutableStateOf(false) }
    var eqLevels by remember { mutableStateOf(List(12) { 0f }) }
    var subEnabled by remember { mutableStateOf(false) }
    var subGain by remember { mutableFloatStateOf(0f) }
    var subFrequency by remember { mutableFloatStateOf(80f) }
    var eqStatus by remember { mutableStateOf("Menunggu audio session dari pemutar.") }
    var eqEffect by remember { mutableStateOf<Equalizer?>(null) }
    var bassEffect by remember { mutableStateOf<BassBoost?>(null) }

    LaunchedEffect(hasPermission) { if (hasPermission) tracks = loadTracks() }

    val updateAudioSessionId = rememberUpdatedState<(Int) -> Unit> { id -> audioSessionId = id }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onAudioSessionIdChanged(audioSessionId: Int) { if (audioSessionId > 0) updateAudioSessionId.value(audioSessionId) }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) { liveMetadata = mediaMetadata }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val uri = mediaItem.localConfiguration?.uri
                    selected = tracks.firstOrNull { it.uri == uri }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, playing) {
        while (playing) {
            val session = runCatching { player.audioSessionId }.getOrDefault(0)
            if (session > 0 && session != audioSessionId) audioSessionId = session
            delay(250)
        }
    }

    // The spectrum is tapped from this player's audio session (not the microphone).
    DisposableEffect(audioSessionId, playing, hasRecordPermission) {
        var visualizer: Visualizer? = null
        if (playing && audioSessionId > 0 && hasRecordPermission) {
            try {
                val effect = Visualizer(audioSessionId)
                effect.captureSize = Visualizer.getCaptureSizeRange()[1]
                effect.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                effect.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 16) return
                        val nyquist = (samplingRate / 2000f).coerceAtLeast(1f)
                        val bins = fft.size / 2
                        val lowLimit = 35f
                        val highLimit = min(16000f, nyquist * 0.96f).coerceAtLeast(100f)
                        val ratio = (highLimit / lowLimit).coerceAtLeast(1.01f)
                        val levels = List(28) { band ->
                            val f0 = lowLimit * ratio.pow(band / 28f)
                            val f1 = lowLimit * ratio.pow((band + 1) / 28f)
                            val first = (f0 * fft.size * 1000f / samplingRate).toInt().coerceIn(1, bins - 1)
                            val last = (f1 * fft.size * 1000f / samplingRate).toInt().coerceIn(first + 1, bins)
                            var sum = 0.0
                            var count = 0
                            for (bin in first until last) {
                                val re = fft[bin * 2].toInt()
                                val im = fft[bin * 2 + 1].toInt()
                                sum += hypot(re.toDouble(), im.toDouble())
                                count++
                            }
                            val magnitude = if (count > 0) sum / count else 0.0
                            ((ln(1.0 + magnitude) / ln(1.0 + 100.0)).toFloat()).coerceIn(0.025f, 1f)
                        }
                        spectrum = levels
                        spectrumStatus = "SPECTRUM LIVE • FFT dari output pemutar"
                    }
                }, Visualizer.getMaxCaptureRate(), false, true)
                effect.enabled = true
                visualizer = effect
                spectrumStatus = "Visualizer tersambung ke audio session."
            } catch (e: Exception) {
                spectrumStatus = "Visualizer tidak didukung / akses ditolak: ${e.javaClass.simpleName}"
            }
        } else {
            spectrum = List(28) { 0.025f }
            spectrumStatus = when {
                !hasRecordPermission -> "Izinkan akses audio untuk membaca output pemutar."
                !playing -> "Putar lagu untuk membaca spektrum audio."
                else -> "Menunggu audio session..."
            }
        }
        onDispose {
            runCatching { visualizer?.enabled = false }
            runCatching { visualizer?.release() }
        }
    }

    fun applyEqualizer() {
        val eq = eqEffect ?: return
        try {
            val hardwareCount = eq.numberOfBands.toInt()
            if (hardwareCount <= 0) { eqStatus = "Equalizer hardware tidak menyediakan band."; return }
            val hardwareFreqs = (0 until hardwareCount).map { (eq.getCenterFreq(it.toShort()) / 1000f).coerceAtLeast(1f) }
            val range = eq.bandLevelRange
            for (band in 0 until hardwareCount) {
                val frequency = hardwareFreqs[band]
                val upper = TargetFrequencies.indexOfFirst { it >= frequency }.let { if (it < 0) TargetFrequencies.lastIndex else it }
                val lower = (upper - 1).coerceAtLeast(0)
                val lowHz = TargetFrequencies[lower]
                val highHz = TargetFrequencies[upper]
                val fraction = if (upper == lower || highHz == lowHz) 0f else (ln(frequency / lowHz) / ln(highHz / lowHz)).coerceIn(0f, 1f)
                val baseDb = eqLevels[lower] + (eqLevels[upper] - eqLevels[lower]) * fraction
                val subDb = if (subEnabled && frequency <= subFrequency) subGain else 0f
                val level = ((if (eqEnabled) baseDb else 0f) + subDb).times(100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), level.toShort())
            }
            eq.enabled = eqEnabled || (subEnabled && subGain > 0f)
            bassEffect?.let {
                it.setStrength(if (subEnabled) (subGain / 6f * 500f).toInt().coerceIn(0, 500).toShort() else 0)
                it.enabled = subEnabled && subGain > 0f
            }
            eqStatus = "12 kontrol disesuaikan ke ${hardwareCount} band hardware perangkat."
        } catch (e: Exception) {
            eqStatus = "Gagal menerapkan EQ: ${e.javaClass.simpleName}"
        }
    }

    DisposableEffect(audioSessionId) {
        runCatching { eqEffect?.release() }
        runCatching { bassEffect?.release() }
        eqEffect = null
        bassEffect = null
        if (audioSessionId > 0) {
            try {
                eqEffect = Equalizer(0, audioSessionId)
                bassEffect = BassBoost(0, audioSessionId)
                applyEqualizer()
                eqStatus = "Equalizer terhubung ke sesi audio."
            } catch (e: Exception) {
                eqStatus = "Efek audio tidak didukung: ${e.javaClass.simpleName}"
            }
        } else {
            eqStatus = "Putar lagu terlebih dahulu untuk menghubungkan efek audio."
        }
        onDispose {
            runCatching { eqEffect?.release() }
            runCatching { bassEffect?.release() }
            eqEffect = null
            bassEffect = null
        }
    }

    LaunchedEffect(eqEnabled, eqLevels, subEnabled, subGain, subFrequency, audioSessionId) { applyEqualizer() }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101B31), Ink, Color(0xFF102A32))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MUSIC PLAYER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Feel every beat.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.GraphicEq, null, tint = Mint, modifier = Modifier.size(32.dp))
            }

            AnimatedContent(screen, label = "main-screen", modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    Screen.LIBRARY -> {
                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(18.dp))
                            Text("YOUR COLLECTION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Cari lagu atau artis…", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                                shape = RoundedCornerShape(16.dp), colors = fieldColors())
                            Spacer(Modifier.height(12.dp))
                            if (!hasPermission) EmptyState("Izinkan akses audio", "Agar aplikasi bisa membaca lagu di perangkat.", "Izinkan", requestPermission)
                            else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tracks.size} lagu ditemukan", color = Muted, fontSize = 12.sp)
                                    TextButton(onClick = { tracks = emptyList() }) { Text("Muat ulang", color = Mint) }
                                }
                                val libraryListState = rememberLazyListState(initialFirstVisibleItemIndex = libraryScrollIndex, initialFirstVisibleItemScrollOffset = libraryScrollOffset)
                                LaunchedEffect(libraryListState) {
                                    snapshotFlow { libraryListState.firstVisibleItemIndex to libraryListState.firstVisibleItemScrollOffset }
                                        .collect { (index, offset) -> libraryScrollIndex = index; libraryScrollOffset = offset }
                                }
                                LazyColumn(state = libraryListState, contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    items(tracks.filter { it.title.contains(search, true) || it.artist.contains(search, true) }, key = { it.id }) { track ->
                                        TrackRow(track, selected?.id == track.id, onClick = {
                                            selected = track
                                            player.setMediaItems(tracks.map { item ->
                                                MediaItem.Builder().setUri(item.uri).setMediaMetadata(
                                                    MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                                                        .setAlbumTitle(item.album).build()
                                                ).build()
                                            }, tracks.indexOf(track), 0L); player.prepare(); player.play()
                                            screen = Screen.PLAYER
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Screen.ONLINE -> {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Text("PLAY FROM URL", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = onlineUrl, onValueChange = { onlineUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("https://…/audio.mp3", color = Muted) }, leadingIcon = { Icon(Icons.Default.Link, null, tint = Muted) },
                                shape = RoundedCornerShape(16.dp), colors = fieldColors())
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                val url = onlineUrl.trim()
                                if (url.startsWith("https://")) {
                                    selected = null
                                    player.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(
                                        MediaMetadata.Builder().setTitle(url.substringAfterLast('/').substringBefore('?').ifBlank { "Online stream" }).build()
                                    ).build()); player.prepare(); player.play()
                                    screen = Screen.PLAYER
                                }
                            }, enabled = onlineUrl.startsWith("https://"), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Putar stream")
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Gunakan URL audio langsung dari sumber yang mengizinkan pemutaran. Tautan halaman web biasa tidak selalu bisa diputar.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                    Screen.PLAYER -> FullPlayer(
                        track = selected, player = player, playing = playing, spectrum = spectrum, spectrumStatus = spectrumStatus, spectrumStyle = spectrumStyle, metadata = liveMetadata,
                        hasRecordPermission = hasRecordPermission, requestRecordPermission = requestRecordPermission,
                        lyricsEnabled = lyricsEnabled, lyricsText = lyricsText,
                        onLyricsChange = { lyricsText = it; preferences.edit().putString(lyricsKey, it).apply() },
                        onBack = { screen = Screen.LIBRARY }, onEqualizer = { screen = Screen.EQUALIZER },
                        onPrevious = {
                            val index = tracks.indexOfFirst { it.id == selected?.id }
                            if (index > 0) player.seekToPreviousMediaItem()
                        },
                        onNext = {
                            val index = tracks.indexOfFirst { it.id == selected?.id }
                            if (index >= 0 && index < tracks.lastIndex) player.seekToNextMediaItem()
                        }
                    )
                    Screen.EQUALIZER -> EqualizerScreen(
                        enabled = eqEnabled, onEnabled = { eqEnabled = it }, levels = eqLevels, onLevel = { i, v -> eqLevels = eqLevels.toMutableList().also { it[i] = v } },
                        subEnabled = subEnabled, onSubEnabled = { subEnabled = it }, subGain = subGain, onSubGain = { subGain = it },
                        subFrequency = subFrequency, onSubFrequency = { subFrequency = it }, status = eqStatus, onBack = { screen = Screen.PLAYER }
                    )
                    Screen.SETTINGS -> SettingsScreen(spectrum, spectrumStyle,
                        onStyleSelected = { style -> spectrumStyle = style; preferences.edit().putString("spectrum_style", style.name).apply() },
                        lyricsEnabled = lyricsEnabled,
                        onLyricsEnabled = { enabled -> lyricsEnabled = enabled; preferences.edit().putBoolean("lyrics_enabled", enabled).apply() }
                    )
                }
            }

            if (screen != Screen.PLAYER && screen != Screen.EQUALIZER) {
                MiniPlayer(track = selected, playing = playing, onOpen = { screen = Screen.PLAYER }, onToggle = { if (player.isPlaying) player.pause() else player.play() })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCC1B2940))
                    .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(Screen.LIBRARY, Icons.Default.LibraryMusic, "Library"),
                    Triple(Screen.ONLINE, Icons.Default.Language, "Online"),
                    Triple(Screen.PLAYER, Icons.Default.GraphicEq, "Now Playing"),
                    Triple(Screen.EQUALIZER, Icons.Default.Equalizer, "EQ"),
                    Triple(Screen.SETTINGS, Icons.Default.Settings, "Settings")
                )
                tabs.forEach { (target, icon, label) ->
                    val active = screen == target
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (active) Mint.copy(alpha = .16f) else Color.Transparent)
                            .clickable { screen = target }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, null, tint = if (active) Mint else Muted, modifier = Modifier.size(20.dp))
                        Text(label, color = if (active) Mint else Muted, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.White.copy(alpha = .12f), focusedBorderColor = Mint,
    unfocusedTextColor = Color.White, focusedTextColor = Color.White
)

@Composable
private fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) Mint.copy(alpha = .13f) else Color.White.copy(alpha = .055f)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Mint) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (active) Icons.Default.Equalizer else Icons.Default.PlayArrow, null, tint = if (active) Mint else Muted)
    }
}

@Composable
private fun MiniPlayer(track: Track?, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xCC27364D)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Album, null, tint = Mint, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track?.title ?: "Belum ada lagu diputar", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "Ketuk untuk membuka pemutar", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Mint) }
    }
}

@Composable
private fun FullPlayer(
    track: Track?, player: ExoPlayer, playing: Boolean, spectrum: List<Float>, spectrumStatus: String, spectrumStyle: SpectrumStyle, metadata: androidx.media3.common.MediaMetadata?,
    hasRecordPermission: Boolean, requestRecordPermission: () -> Unit, onBack: () -> Unit, onEqualizer: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit,
    lyricsEnabled: Boolean, lyricsText: String, onLyricsChange: (String) -> Unit
) {
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, playing, track) {
        while (true) {
            duration = player.duration.takeIf { it > 0 } ?: track?.duration ?: 0L
            position = if (duration > 0) player.currentPosition.toFloat() / duration else 0f
            delay(400)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text("NOW PLAYING", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onEqualizer) { Icon(Icons.Default.Equalizer, null, tint = Mint) }
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            val coverSize = maxWidth.coerceAtMost(300.dp)
            Box(Modifier.size(coverSize).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xAA42677A), Color(0xAA17263D))))
                .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(28.dp)).padding(9.dp)) {
                AlbumCover(uri = track?.let { if (it.albumId >= 0) Uri.parse("content://media/external/audio/albumart/${it.albumId}") else null } ?: metadata?.artworkUri,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(track?.title ?: player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Belum ada lagu diputar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track?.artist ?: metadata?.artist?.toString() ?: "Online stream", color = Muted, fontSize = 14.sp)
        Text(listOfNotNull(track?.album?.takeIf { it != "Unknown album" } ?: metadata?.albumTitle?.toString(), track?.year?.takeIf { it > 0 }?.toString(), track?.trackNumber?.takeIf { it > 0 }?.let { "Track $it" }).joinToString(" • "), color = Muted, fontSize = 11.sp, maxLines = 1)
        track?.composer?.takeIf { it.isNotBlank() }?.let { Text("Composer: $it", color = Muted, fontSize = 10.sp) }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .045f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            SpectrumRenderer(spectrum, spectrumStyle, Modifier.fillMaxWidth().height(100.dp))
        }
        Text(spectrumStatus, color = if (spectrumStatus.contains("LIVE")) Mint else Muted, fontSize = 10.sp)
        if (!hasRecordPermission) TextButton(onClick = requestRecordPermission) { Text("Izinkan akses audio untuk spectrum", color = Mint) }
        Spacer(Modifier.height(10.dp))
        Slider(value = position.coerceIn(0f, 1f), onValueChange = { position = it }, onValueChangeFinished = { if (duration > 0) player.seekTo((position * duration).toLong()) }, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Color.White.copy(alpha = .16f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime((position * duration).toLong()), color = Muted, fontSize = 11.sp)
            Text(formatTime(duration), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(18.dp))
            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(68.dp).clip(CircleShape).background(Mint)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.width(18.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onEqualizer, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)) {
            Icon(Icons.Default.Equalizer, null); Spacer(Modifier.width(8.dp)); Text("12-Band Equalizer + Subwoofer")
        }
        if (lyricsEnabled) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = .055f))
                .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lyrics, null, tint = Mint)
                    Spacer(Modifier.width(8.dp))
                    Text("LYRICS", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                    Text("Tersimpan otomatis", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = lyricsText, onValueChange = onLyricsChange, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text("Tempel atau tulis lirik lagu di sini…", color = Muted) },
                    colors = fieldColors(), shape = RoundedCornerShape(14.dp), minLines = 4)
                Text("Lirik disimpan per lagu di perangkat. Isi manual; aplikasi tidak mengambil lirik berhak cipta dari sumber tidak resmi.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}


@Composable
private fun AlbumCover(uri: Uri?, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF29465A), Color(0xFF18243B)))), contentAlignment = Alignment.Center) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), contentDescription = "Album cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Album, null, tint = Mint.copy(alpha = .9f), modifier = Modifier.size(104.dp))
    }
}

@Composable
private fun SpectrumWave(levels: List<Float>, modifier: Modifier = Modifier) {
    val smoothed = levels.mapIndexed { index, level ->
        animateFloatAsState(
            targetValue = level.coerceIn(.02f, 1f),
            animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
            label = "wave-$index"
        ).value
    }
    Canvas(modifier) {
        if (smoothed.isEmpty()) return@Canvas
        val mid = size.height * .52f
        val points = smoothed.mapIndexed { index, level ->
            val x = if (smoothed.size == 1) size.width / 2f else index * size.width / (smoothed.size - 1)
            val amplitude = (level * size.height * .42f).coerceAtLeast(2.dp.toPx())
            androidx.compose.ui.geometry.Offset(x, mid - amplitude)
        }
        val lower = smoothed.mapIndexed { index, level ->
            val x = if (smoothed.size == 1) size.width / 2f else index * size.width / (smoothed.size - 1)
            val amplitude = (level * size.height * .28f).coerceAtLeast(2.dp.toPx())
            androidx.compose.ui.geometry.Offset(x, mid + amplitude)
        }
        val area = Path().apply {
            moveTo(points.first().x, mid)
            points.forEachIndexed { i, p ->
                if (i == 0) lineTo(p.x, p.y) else {
                    val prev = points[i - 1]
                    val cx = (prev.x + p.x) / 2f
                    cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                }
            }
            lower.asReversed().forEachIndexed { i, p ->
                val prev = if (i == 0) lower.last() else lower[lower.lastIndex - i + 1]
                val cx = (prev.x + p.x) / 2f
                cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
            }
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Mint.copy(alpha = .34f), Mint.copy(alpha = .035f), Color.Transparent)))
        val line = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else {
                    val prev = points[i - 1]
                    val cx = (prev.x + p.x) / 2f
                    cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                }
            }
        }
        drawPath(line, Mint.copy(alpha = .95f), style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
        val bottomLine = Path().apply {
            lower.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else {
                    val prev = lower[i - 1]
                    val cx = (prev.x + p.x) / 2f
                    cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                }
            }
        }
        drawPath(bottomLine, Color(0xFF49BBAA).copy(alpha = .75f), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}


@Composable
private fun SpectrumRenderer(levels: List<Float>, style: SpectrumStyle, modifier: Modifier = Modifier) {
    when (style) {
        SpectrumStyle.WAVE -> SpectrumWave(levels, modifier)
        SpectrumStyle.BARS -> SpectrumBarsPremium(levels, modifier, false)
        SpectrumStyle.MIRROR -> SpectrumBarsPremium(levels, modifier, true)
        SpectrumStyle.CIRCULAR -> SpectrumCircular(levels, modifier)
        SpectrumStyle.LINE -> SpectrumLine(levels, modifier)
        SpectrumStyle.FILLED -> SpectrumFilled(levels, modifier)
        SpectrumStyle.DOTS -> SpectrumDots(levels, modifier)
        SpectrumStyle.PULSE -> SpectrumBarsPremium(levels, modifier, true)
    }
}

@Composable
private fun SpectrumBarsPremium(levels: List<Float>, modifier: Modifier, mirrored: Boolean) {
    val values = levels.mapIndexed { i, v ->
        animateFloatAsState(v.coerceIn(.02f, 1f), tween(90, easing = FastOutSlowInEasing), label = "bar-$i").value
    }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val h = (v * size.height * (if (mirrored) .42f else .88f)).coerceAtLeast(3.dp.toPx())
            val x = i * (barW + gap)
            val y = if (mirrored) (size.height - h) / 2f else size.height - h
            drawRoundRect(Mint.copy(alpha = .12f), androidx.compose.ui.geometry.Offset(x - 1.dp.toPx(), y - 2.dp.toPx()), androidx.compose.ui.geometry.Size(barW + 2.dp.toPx(), h + 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(barW, barW))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE8FFF9), Mint, Color(0xFF25A99B))), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(barW, h), androidx.compose.ui.geometry.CornerRadius(barW, barW))
        }
    }
}

@Composable
private fun SpectrumCircular(levels: List<Float>, modifier: Modifier) {
    val values = levels.mapIndexed { i, v ->
        animateFloatAsState(v.coerceIn(.02f, 1f), tween(100, easing = FastOutSlowInEasing), label = "radial-$i").value
    }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * .22f
        val maxBar = size.minDimension * .22f
        val stroke = 3.dp.toPx()
        values.forEachIndexed { i, v ->
            val angle = (i.toFloat() / values.size) * (2f * PI.toFloat()) - PI.toFloat() / 2f
            val inner = base
            val outer = base + maxBar * v
            val start = androidx.compose.ui.geometry.Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
            val end = androidx.compose.ui.geometry.Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer)
            drawLine(Mint.copy(alpha = .18f), start, end, strokeWidth = stroke + 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Mint, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(Color.White.copy(alpha = .08f), radius = base - 5.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Mint.copy(alpha = .75f), radius = 3.dp.toPx(), center = center)
    }
}
@Composable
private fun SpectrumLine(levels: List<Float>, modifier: Modifier) {
    val values = levels.mapIndexed { i, v -> animateFloatAsState(v.coerceIn(.02f, 1f), tween(95), label = "line-$i").value }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) size.width / 2f else i * size.width / (values.size - 1)
            val y = size.height * .88f - v * size.height * .76f
            if (i == 0) path.moveTo(x, y) else {
                val px = (i - 1) * size.width / (values.size - 1)
                val py = size.height * .88f - values[i - 1] * size.height * .76f
                val cx = (px + x) / 2f
                path.cubicTo(cx, py, cx, y, x, y)
            }
        }
        drawPath(path, Mint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SpectrumFilled(levels: List<Float>, modifier: Modifier) {
    val values = levels.mapIndexed { i, v -> animateFloatAsState(v.coerceIn(.02f, 1f), tween(100), label = "filled-$i").value }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val path = Path().apply {
            moveTo(0f, size.height)
            values.forEachIndexed { i, v ->
                val x = if (values.size == 1) size.width / 2f else i * size.width / (values.size - 1)
                val y = size.height * .92f - v * size.height * .82f
                if (i == 0) lineTo(x, y) else {
                    val px = (i - 1) * size.width / (values.size - 1)
                    val py = size.height * .92f - values[i - 1] * size.height * .82f
                    val cx = (px + x) / 2f
                    cubicTo(cx, py, cx, y, x, y)
                }
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Mint.copy(alpha = .72f), Mint.copy(alpha = .04f))))
    }
}

@Composable
private fun SpectrumDots(levels: List<Float>, modifier: Modifier) {
    val values = levels.mapIndexed { i, v -> animateFloatAsState(v.coerceIn(.02f, 1f), tween(90), label = "dots-$i").value }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = size.width / (values.size + 1)
        values.forEachIndexed { i, v ->
            val x = gap * (i + 1)
            val radius = 2.dp.toPx() + v * 5.dp.toPx()
            val y = size.height - radius - v * (size.height - radius * 2f)
            drawCircle(Mint.copy(alpha = .14f), radius * 1.8f, androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(Mint, radius, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun SettingsScreen(spectrum: List<Float>, selectedStyle: SpectrumStyle, onStyleSelected: (SpectrumStyle) -> Unit, lyricsEnabled: Boolean, onLyricsEnabled: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("PENGATURAN", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(5.dp))
        Text("Spectrum visualizer", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Pilih tampilan visualisasi yang muncul di Now Playing.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        SpectrumStyle.values().toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val active = style == selectedStyle
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                        .background(if (active) Mint.copy(alpha = .11f) else Color.White.copy(alpha = .045f))
                        .border(1.dp, if (active) Mint.copy(alpha = .65f) else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                        .clickable { onStyleSelected(style) }.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(style.title, color = if (active) Mint else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (active) Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x55202F43)).padding(5.dp)) {
                            SpectrumRenderer(spectrum, style, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(style.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, minLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("Pilihan langsung diterapkan ke pemutar. Spektrum membaca FFT dari output audio, bukan mikrofon.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(22.dp))
        Divider(color = Color.White.copy(alpha = .12f))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Tampilkan lirik", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Tampilkan panel lirik di Now Playing", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = lyricsEnabled, onCheckedChange = onLyricsEnabled)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EqualizerScreen(
    enabled: Boolean, onEnabled: (Boolean) -> Unit, levels: List<Float>, onLevel: (Int, Float) -> Unit,
    subEnabled: Boolean, onSubEnabled: (Boolean) -> Unit, subGain: Float, onSubGain: (Float) -> Unit,
    subFrequency: Float, onSubFrequency: (Float) -> Unit, status: String, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text("AUDIO ENGINE", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("12-Band Equalizer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Atur tiap frekuensi secara langsung", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xAA263B52), Color(0x88202D43))))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp))
                .padding(vertical = 16.dp)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                levels.forEachIndexed { index, value ->
                    Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (value > 0f) "+%.1f".format(value) else "%.1f".format(value),
                            color = if (enabled) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        VerticalEqFader(value, enabled, { onLevel(index, it) }, Modifier.height(164.dp).width(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(FrequencyLabels[index], color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Divider(color = Color.White.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Subwoofer / Deep Bass", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Low-frequency enhancement", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = subEnabled, onCheckedChange = onSubEnabled)
        }
        Text("Gain  ${"%.1f".format(subGain)} dB", color = Mint, fontSize = 12.sp)
        Slider(value = subGain, onValueChange = onSubGain, valueRange = 0f..6f, enabled = subEnabled, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
        Text("Cutoff  ${subFrequency.toInt()} Hz", color = Mint, fontSize = 12.sp)
        Slider(value = subFrequency, onValueChange = onSubFrequency, valueRange = 50f..160f, enabled = subEnabled, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
        Spacer(Modifier.height(8.dp))
        Text(status, color = Muted, fontSize = 11.sp)
        Text("12 kontrol dipetakan ke band EQ hardware yang tersedia. Hasil akhir mengikuti dukungan audio perangkat dan headphone/speaker.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun VerticalEqFader(
    value: Float,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minValue = -15f
    val maxValue = 15f
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val faderHeightPx = with(density) { 132.dp.toPx() }
    val trackColor = if (enabled) Color.White.copy(alpha = .18f) else Color.White.copy(alpha = .08f)
    val activeColor = if (enabled) Mint else Muted.copy(alpha = .55f)
    val updateFromY: (Float, Float) -> Unit = { y, height ->
        val f = (1f - (y / height).coerceIn(0f, 1f))
        onValue((minValue + f * (maxValue - minValue)).coerceIn(minValue, maxValue))
    }
    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { point -> updateFromY(point.y, size.height.toFloat()) }
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = enabled,
                reverseDirection = false,
                state = rememberDraggableState { delta ->
                    val next = (value - delta / faderHeightPx * (maxValue - minValue)).coerceIn(minValue, maxValue)
                    onValue(next)
                }
            )
    ) {
        val centerX = size.width / 2f
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val trackW = 4.dp.toPx()
        val centerY = (top + bottom) / 2f
        val knobY = bottom - fraction * (bottom - top)
        drawRoundRect(trackColor, androidx.compose.ui.geometry.Offset(centerX - trackW / 2, top), androidx.compose.ui.geometry.Size(trackW, bottom - top), androidx.compose.ui.geometry.CornerRadius(trackW, trackW))
        drawRoundRect(activeColor.copy(alpha = .9f), androidx.compose.ui.geometry.Offset(centerX - trackW / 2, min(centerY, knobY)), androidx.compose.ui.geometry.Size(trackW, abs(centerY - knobY)), androidx.compose.ui.geometry.CornerRadius(trackW, trackW))
        drawCircle(activeColor.copy(alpha = .12f), radius = 15.dp.toPx(), center = androidx.compose.ui.geometry.Offset(centerX, knobY))
        drawRoundRect(activeColor, androidx.compose.ui.geometry.Offset(centerX - 11.dp.toPx(), knobY - 7.dp.toPx()), androidx.compose.ui.geometry.Size(22.dp.toPx(), 14.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()))
        drawCircle(Color.White.copy(alpha = if (enabled) .8f else .35f), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(centerX, knobY))
        drawLine(Color.White.copy(alpha = .22f), androidx.compose.ui.geometry.Offset(centerX - 9.dp.toPx(), centerY), androidx.compose.ui.geometry.Offset(centerX + 9.dp.toPx(), centerY), strokeWidth = 1.dp.toPx())
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

private fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
