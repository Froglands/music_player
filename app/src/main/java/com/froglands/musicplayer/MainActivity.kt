package com.froglands.musicplayer

import android.Manifest
import android.app.PendingIntent
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale
import android.widget.Toast
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.launch
import kotlin.math.*


private data class AppUpdateInfo(val version: String, val notes: String, val apkUrl: String)

private suspend fun fetchLatestAppUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.github.com/repos/Froglands/music_player/releases/latest").openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Froglands-MusicPlayer-Android")
    }
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException(if (connection.responseCode == 404) "Belum ada rilis resmi yang tersedia." else "Gagal memeriksa pembaruan (HTTP ${connection.responseCode}).")
        }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name").removePrefix("v").trim()
        val assetArray = json.optJSONArray("assets") ?: throw IllegalStateException("Rilis belum menyertakan file APK.")
        var apkUrl = ""
        for (i in 0 until assetArray.length()) {
            val asset = assetArray.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) throw IllegalStateException("File APK belum tersedia pada rilis terbaru.")
        val latestParts = tag.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val maxParts = maxOf(latestParts.size, currentParts.size)
        var comparison = 0
        for (i in 0 until maxParts) {
            val diff = (latestParts.getOrElse(i) { 0 }).compareTo(currentParts.getOrElse(i) { 0 })
            if (diff != 0) { comparison = diff; break }
        }
        if (comparison <= 0) null else AppUpdateInfo(tag, json.optString("body", "Pembaruan terbaru tersedia."), apkUrl)
    } finally {
        connection.disconnect()
    }
}

private fun downloadAppUpdate(context: Context, update: AppUpdateInfo) {
    val request = DownloadManager.Request(Uri.parse(update.apkUrl))
        .setTitle("Music Player v${update.version}")
        .setDescription("Mengunduh pembaruan aplikasi")
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MusicPlayer-${update.version}.apk")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private data class AppPalette(val name: String, val background: Color, val panel: Color, val accent: Color, val muted: Color)
private val AppPalettes = listOf(
    AppPalette("Midnight", Color(0xFF090D18), Color(0xFF171F32), Color(0xFF75F0D0), Color(0xFF9AA8BD)),
    AppPalette("Ocean", Color(0xFF061827), Color(0xFF102B43), Color(0xFF49C6E5), Color(0xFF9AB8C8)),
    AppPalette("Purple Haze", Color(0xFF140D22), Color(0xFF28183D), Color(0xFFC69BFF), Color(0xFFB5A6C9)),
    AppPalette("Rose", Color(0xFF1D0D16), Color(0xFF351927), Color(0xFFFF8FB1), Color(0xFFC7A5B2)),
    AppPalette("Sunset", Color(0xFF211109), Color(0xFF3B2417), Color(0xFFFFB36B), Color(0xFFD0B29A)),
    AppPalette("Emerald", Color(0xFF071B16), Color(0xFF123329), Color(0xFF65E6A8), Color(0xFF9BBDAA)),
    AppPalette("Crimson", Color(0xFF1D090D), Color(0xFF38151D), Color(0xFFFF7185), Color(0xFFC9A0A6)),
    AppPalette("Arctic", Color(0xFF101923), Color(0xFF202F40), Color(0xFFA7D8FF), Color(0xFFB0C2D2)),
    AppPalette("Gold", Color(0xFF1B1708), Color(0xFF342B12), Color(0xFFFFD76A), Color(0xFFC9BD94)),
    AppPalette("Monochrome", Color(0xFF101010), Color(0xFF252525), Color(0xFFE0E0E0), Color(0xFFAAAAAA)),
    AppPalette("Cyberpunk", Color(0xFF10091F), Color(0xFF21163A), Color(0xFFFF4DE1), Color(0xFFB5A0D4)),
    AppPalette("Lime", Color(0xFF101807), Color(0xFF253414), Color(0xFFC5F45B), Color(0xFFB3C58C)),
    AppPalette("Poweramp", Color(0xFF08090B), Color(0xFF191B20), Color(0xFFFF9D24), Color(0xFF9DA3AD)),
    AppPalette("Obsidian", Color(0xFF050609), Color(0xFF11151D), Color(0xFF8FA8FF), Color(0xFF8993A5)),
    AppPalette("Graphite", Color(0xFF101216), Color(0xFF20252D), Color(0xFFE3E8F0), Color(0xFFA3ACB9)),
    AppPalette("AMOLED Black", Color(0xFF000000), Color(0xFF101010), Color(0xFF53E0C1), Color(0xFF8B969F)),
    AppPalette("Slate Night", Color(0xFF0B1220), Color(0xFF182338), Color(0xFF79B8FF), Color(0xFF9EABC0)),
    AppPalette("Nord Dark", Color(0xFF171C24), Color(0xFF252E3B), Color(0xFF88C0D0), Color(0xFF9BA9B8))
)
private enum class VisualStyle(val label: String, val description: String) {
    GLASS("Glass", "Lapisan transparan dan efek kaca"),
    THREE_D("3D Depth", "Panel timbul dengan highlight dan kedalaman"),
    NEON("Neon", "Aksen terang dan kontras tinggi"),
    AURORA("Aurora", "Gradasi warna lembut"),
    MINIMAL("Minimal", "Tampilan sederhana dan bersih"),
    POWERAMP("Poweramp", "Gelap metalik dengan aksen audio oranye")
}
private val SupportedAppLanguages = listOf("id", "en", "es", "fr", "de", "pt")
private val AppTranslations = mapOf(
"settings" to listOf("Pengaturan","Settings","Ajustes","Paramètres","Einstellungen","Configurações","設定","설정","设置","الإعدادات","सेटिंग्स","Настройки"),
"settings_sub" to listOf("Atur pengalaman musikmu","Customize your music experience","Personaliza tu música","Personnalisez votre musique","Passe dein Musikerlebnis an","Personalize sua música","音楽体験をカスタマイズ","음악 환경 설정","自定义音乐体验","خصص تجربة الموسيقى","अपने संगीत अनुभव को अनुकूलित करें","Настройте музыку"),
"settings_hint" to listOf("Setiap fitur punya halaman dan pengaturan sendiri.","Each feature has its own page and settings.","Cada función tiene su propia página.","Chaque fonction a sa propre page.","Jede Funktion hat eine eigene Seite.","Cada recurso tem sua própria página.","各機能に専用ページがあります。","각 기능에는 별도 페이지가 있습니다.","每项功能都有独立页面。","لكل ميزة صفحة وإعدادات خاصة.","हर सुविधा का अपना पेज है।","У каждой функции своя страница."),
"theme" to listOf("Tema aplikasi","App theme","Tema de la aplicación","Thème de l’application","App-Design","Tema do aplicativo","アプリテーマ","앱 테마","应用主题","مظهر التطبيق","ऐप थीम","Тема приложения"),
"visualizer" to listOf("Visualizer","Visualizer","Visualizador","Visualiseur","Visualisierung","Visualizador","ビジュアライザー","비주얼라이저","可视化器","المتخيل الصوتي","विज़ुअलाइज़र","Визуализатор"),
"lyrics" to listOf("Lirik","Lyrics","Letras","Paroles","Liedtexte","Letras","歌詞","가사","歌词","كلمات الأغاني","गीत","Тексты песен"),
"downloads" to listOf("Kualitas unduhan","Download quality","Calidad de descarga","Qualité de téléchargement","Downloadqualität","Qualidade do download","ダウンロード品質","다운로드 품질","下载质量","جودة التنزيل","डाउनलोड गुणवत्ता","Качество загрузки"),
"language" to listOf("Bahasa","Language","Idioma","Langue","Sprache","Idioma","言語","언어","语言","اللغة","भाषा","Язык"),
"appearance" to listOf("Gaya tampilan","Appearance style","Estilo visual","Style d’apparence","Darstellungsstil","Estilo visual","表示スタイル","화면 스타일","外观样式","نمط المظهر","दिखावट शैली","Стиль оформления"),
"credits" to listOf("Tentang & Kredit","About & Credits","Acerca de y créditos","À propos et crédits","Info & Mitwirkende","Sobre e créditos","情報・クレジット","정보 및 크레딧","关于与鸣谢","حول والتقديرات","जानकारी और श्रेय","О приложении"),
"auto" to listOf("Otomatis / Sistem","Automatic / System","Automático / Sistema","Automatique / Système","Automatisch / System","Automático / Sistema","自動 / システム","자동 / 시스템","自动 / 系统","تلقائي / النظام","स्वचालित / सिस्टम","Авто / Система"),
"follow_system" to listOf("Ikuti bahasa perangkat","Follow device language","Usar idioma del dispositivo","Suivre la langue de l’appareil","Gerätesprache verwenden","Seguir idioma do dispositivo","端末の言語に従う","기기 언어 사용","跟随设备语言","اتبع لغة الجهاز","डिवाइस की भाषा अपनाएं","Язык устройства"),
"choose_language" to listOf("Pilih bahasa aplikasi. Otomatis akan mengikuti bahasa perangkat.","Choose the app language. Automatic follows your device language.","Elige el idioma. Automático sigue el idioma del dispositivo.","Choisissez la langue. Automatique suit celle de l’appareil.","Wähle die Sprache. Automatisch folgt der Gerätesprache.","Escolha o idioma. Automático segue o idioma do dispositivo.","アプリの言語を選択。自動は端末の言語に従います。","앱 언어를 선택하세요. 자동은 기기 언어를 따릅니다.","选择应用语言。自动将跟随设备语言。","اختر لغة التطبيق. يتبع الوضع التلقائي لغة الجهاز.","ऐप की भाषा चुनें। स्वचालित डिवाइस भाषा का पालन करता है।","Выберите язык приложения. Авто использует язык устройства."),
"appearance_hint" to listOf("Pilih karakter visual seluruh antarmuka.","Choose the visual style for the whole interface.","Elige el estilo visual de toda la interfaz.","Choisissez le style visuel de toute l’interface.","Wähle den visuellen Stil der gesamten Oberfläche.","Escolha o estilo visual de toda a interface.","アプリ全体の表示スタイルを選択します。","앱 전체의 화면 스타일을 선택하세요.","选择整个界面的视觉风格。","اختر النمط المرئي لكامل الواجهة.","पूरे इंटरफ़ेस की दृश्य शैली चुनें।","Выберите стиль всего интерфейса."),
"library" to listOf("Library","Library","Biblioteca","Bibliothèque","Mediathek","Biblioteca","ライブラリ","라이브러리","音乐库","المكتبة","लाइब्रेरी","Библиотека"),
"online" to listOf("Online","Online","En línea","En ligne","Online","Online","オンライン","온라인","在线","عبر الإنترنت","ऑनलाइन","Онлайн"),
"offline" to listOf("Offline","Offline","Sin conexión","Hors ligne","Offline","Offline","オフライン","오프라인","离线","دون اتصال","ऑफ़लाइन","Офлайн"),
"now_playing" to listOf("Now Playing","Now Playing","Reproduciendo","En lecture","Wiedergabe","Tocando","再生中","재생 중","正在播放","قيد التشغيل","अभी चल रहा है","Сейчас играет")
)
private fun appText(key: String, language: String): String {
    val index = when (language.substringBefore('-')) {
        "en" -> 1; "es" -> 2; "fr" -> 3; "de" -> 4; "pt" -> 5; "ja" -> 6
        "ko" -> 7; "zh" -> 8; "ar" -> 9; "hi" -> 10; "ru" -> 11; else -> 0
    }
    return AppTranslations[key]?.getOrNull(index) ?: AppTranslations[key]?.first() ?: key
}

private val activeVisualStyle = mutableStateOf(VisualStyle.GLASS)
private fun visualSurfaceBrush(): Brush = when (activeVisualStyle.value) {
    // Aero-inspired glass: cool translucent tint, bright upper reflection and a darker lower edge.
    // Keep the material genuinely translucent: the background must remain visible.
    VisualStyle.GLASS -> Brush.verticalGradient(colorStops = arrayOf(
        0.00f to Color(0x38FFFFFF),
        0.035f to Color(0x24EAF7FF),
        0.18f to Color(0x1D9CC7E8),
        0.55f to Color(0x183B6685),
        0.88f to Color(0x22243F59),
        1.00f to Color(0x304D718A)
    ))
    VisualStyle.THREE_D -> Brush.verticalGradient(listOf(Color(0xFF526783), Panel, Color(0xFF080D18)))
    VisualStyle.NEON -> Brush.linearGradient(listOf(Mint.copy(alpha = .25f), Panel, Color(0xFF080D18)))
    VisualStyle.AURORA -> Brush.linearGradient(listOf(Mint.copy(alpha = .20f), Color(0xFF26304A), Panel))
    VisualStyle.MINIMAL -> Brush.linearGradient(listOf(Panel, Panel))
    VisualStyle.POWERAMP -> Brush.verticalGradient(listOf(Color(0xFF282B31), Color(0xFF101114), Color(0xFF050607)))
}
private fun visualSurfaceBorderBrush(): Brush = when (activeVisualStyle.value) {
    VisualStyle.GLASS -> Brush.verticalGradient(listOf(Color.White.copy(alpha = .72f), Color(0xFFB8D8F0).copy(alpha = .20f), Color.White.copy(alpha = .10f)))
    else -> Brush.linearGradient(listOf(Color.White.copy(alpha = .24f), Color.White.copy(alpha = .06f)))
}
private val activePalette = mutableStateOf(AppPalettes.first())
private val Ink get() = activePalette.value.background
private val Panel get() = activePalette.value.panel
private val Mint get() = activePalette.value.accent
private val Muted get() = activePalette.value.muted
private val TargetFrequencies = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 10000f, 12000f, 16000f)
private val FrequencyLabels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "10k", "12k", "16k")

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long, val album: String = "Unknown album", val albumArtist: String = "", val albumId: Long = -1L, val year: Int = 0, val trackNumber: Int = 0, val composer: String = "", val storagePath: String = "")

private enum class Screen { LIBRARY, ONLINE, OFFLINE, PLAYER, EQUALIZER, SETTINGS }
private enum class SpectrumStyle(val title: String, val description: String) { WAVE("Wave", "Gelombang halus"), BARS("Bars", "Batang gradasi"), MIRROR("Mirror", "Batang simetris"), CIRCULAR("Circular", "Melingkar"), LINE("Line", "Garis minimalis"), FILLED("Filled", "Gelombang terisi"), DOTS("Dots", "Titik responsif"), PULSE("Pulse", "Pulse simetris"), POWERAMP("Poweramp", "Batang spektrum bertingkat bergaya hi-fi"), VU("VU Meter", "Meter analog dua kanal"), MIRROR_WAVE("Twin Wave", "Gelombang simetris"), SPECTRUM_3D("3D Spectrum", "Batang berlapis"), ORBIT("Orbit", "Cincin audio radial"), DIGITAL("Digital", "Segmen digital"), FIRE("Fire Bars", "Batang dinamis bertingkat") }
private enum class SeekBarStyle(val title: String, val description: String) { CLASSIC("Classic", "Track standar dengan thumb"), THIN("Thin Line", "Garis tipis minimalis"), ROUNDED("Rounded", "Track membulat modern"), CAPSULE("Capsule", "Progress tebal seperti pil"), GRADIENT("Gradient", "Warna aksen bergradasi"), WAVE("Wave", "Gelombang audio halus"), SEGMENTED("Segmented", "Garis bersegmen"), GLOW("Glow", "Aksen bercahaya"), DOTTED("Dotted", "Titik-titik progress"), DOUBLE("Double Line", "Dua garis bertumpuk"), RAINBOW("Rainbow", "Gradasi spektrum warna"), BLOCKS("Blocks", "Blok tebal modern"), NEEDLE("Needle", "Penunjuk posisi presisi"), GLASS("Glass", "Track kaca transparan"), DASHED("Dashed", "Garis putus-putus") }

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
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.COMPOSER, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
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
            val relativePathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val duration = cursor.getLong(durationCol)
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                if (duration <= 0L || (mime.isNotBlank() && !mime.startsWith("audio/"))) continue
                val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol).orEmpty() else ""
                val absolutePath = if (dataPathCol >= 0) cursor.getString(dataPathCol).orEmpty() else ""
                val storagePath = if (absolutePath.isNotBlank()) absolutePath else relativePath
                result += Track(id, cursor.getString(titleCol) ?: "Unknown title",
                    cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    ContentUris.withAppendedId(collection, id), duration, cursor.getString(albumCol) ?: "Unknown album", cursor.getString(albumArtistCol) ?: "", cursor.getLong(albumIdCol), cursor.getInt(yearCol), cursor.getInt(trackCol), cursor.getString(composerCol) ?: "", storagePath)
            }
        }
        result
    }

    override fun onPause() {
        if (::player.isInitialized) {
            val uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            if (uri.isNotBlank()) {
                getSharedPreferences("music_player_preferences", Context.MODE_PRIVATE).edit()
                    .putString("last_played_uri", uri)
                    .putLong("last_played_position", player.currentPosition.coerceAtLeast(0L))
                    .putString("last_played_title", player.mediaMetadata.title?.toString().orEmpty())
                    .putString("last_played_artist", player.mediaMetadata.artist?.toString().orEmpty())
                    .apply()
            }
        }
        super.onPause()
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
    var selectedFolder by rememberSaveable { mutableStateOf("Semua lokasi") }
    var onlineUrl by remember { mutableStateOf("") }
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    var libraryScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var lyricsEnabled by rememberSaveable { mutableStateOf(true) }
    var seekBarStyle by rememberSaveable { mutableStateOf(SeekBarStyle.ROUNDED) }
    var selectedTheme by rememberSaveable { mutableStateOf("Midnight") }
    var selectedVisualStyle by rememberSaveable { mutableStateOf(VisualStyle.GLASS.name) }
    var selectedLanguage by rememberSaveable { mutableStateOf("auto") }
    val screenHistory = remember { mutableStateListOf<Screen>() }
    fun navigateTo(target: Screen) {
        if (target != screen) {
            screenHistory.add(screen)
            screen = target
        }
    }
    fun goBack() {
        if (screenHistory.isNotEmpty()) screen = screenHistory.removeAt(screenHistory.lastIndex)
        else if (screen != Screen.LIBRARY) screen = Screen.LIBRARY
    }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { appContext.getSharedPreferences("music_player_preferences", android.content.Context.MODE_PRIVATE) }
    var lyricsText by remember { mutableStateOf("") }
    var offlineFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var downloadBusy by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf("") }
    var audioDownloadQuality by remember { mutableStateOf("Original") }
    var videoDownloadQuality by remember { mutableStateOf("720p") }
    val coroutineScope = rememberCoroutineScope()
    val offlineDirectory = remember { java.io.File(appContext.filesDir, "offline_media").apply { mkdirs() } }
    LaunchedEffect(Unit) {
        offlineFiles = offlineDirectory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        audioDownloadQuality = preferences.getString("download_audio_quality", "Original") ?: "Original"
        videoDownloadQuality = preferences.getString("download_video_quality", "720p") ?: "720p"
        selectedVisualStyle = preferences.getString("visual_style", VisualStyle.GLASS.name) ?: VisualStyle.GLASS.name
        activeVisualStyle.value = runCatching { VisualStyle.valueOf(selectedVisualStyle) }.getOrDefault(VisualStyle.GLASS)
        selectedLanguage = preferences.getString("app_language", "auto") ?: "auto"
    }
    fun startPrivateDownload(url: String) {
        if (downloadBusy) return
        downloadBusy = true
        downloadMessage = "Mengunduh ke penyimpanan privat aplikasi…"
        coroutineScope.launch {
            val result = runCatching { downloadPrivateMedia(appContext, url) }
            result.onSuccess { file ->
                offlineFiles = offlineDirectory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                downloadMessage = "Tersimpan privat: " + file.name
            }.onFailure { downloadMessage = it.message ?: "Download gagal." }
            downloadBusy = false
        }
    }
    val lyricsKey = selected?.let { "lyrics_${it.id}" } ?: player.currentMediaItem?.localConfiguration?.uri?.toString()?.let { "lyrics_$it" } ?: "lyrics_empty"
    LaunchedEffect(lyricsKey) { lyricsText = preferences.getString(lyricsKey, "") ?: "" }
    var spectrumStyle by remember { mutableStateOf(SpectrumStyle.WAVE) }
    LaunchedEffect(Unit) {
        lyricsEnabled = preferences.getBoolean("lyrics_enabled", true)
        selectedTheme = preferences.getString("app_theme", "Midnight") ?: "Midnight"
        activePalette.value = AppPalettes.firstOrNull { it.name == selectedTheme } ?: AppPalettes.first()
        spectrumStyle = runCatching { SpectrumStyle.valueOf(preferences.getString("spectrum_style", "WAVE") ?: "WAVE") }.getOrDefault(SpectrumStyle.WAVE)
        seekBarStyle = runCatching { SeekBarStyle.valueOf(preferences.getString("seekbar_style", "ROUNDED") ?: "ROUNDED") }.getOrDefault(SeekBarStyle.ROUNDED)
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

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            tracks = loadTracks()
            val lastUri = preferences.getString("last_played_uri", null)
            if (!lastUri.isNullOrBlank()) {
                val lastPosition = preferences.getLong("last_played_position", 0L).coerceAtLeast(0L)
                val lastTrackIndex = tracks.indexOfFirst { it.uri.toString() == lastUri }
                if (lastTrackIndex >= 0) {
                    val restoredItems = tracks.map { item ->
                        MediaItem.Builder().setUri(item.uri).setMediaMetadata(
                            MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                                .setAlbumTitle(item.album).build()
                        ).build()
                    }
                    selected = tracks[lastTrackIndex]
                    player.setMediaItems(restoredItems, lastTrackIndex, lastPosition)
                    player.prepare()
                    screen = Screen.PLAYER
                } else {
                    val savedUri = Uri.parse(lastUri)
                    val restoredItem = MediaItem.Builder().setUri(savedUri).setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(preferences.getString("last_played_title", "Musik terakhir"))
                            .setArtist(preferences.getString("last_played_artist", ""))
                            .build()
                    ).build()
                    selected = null
                    player.setMediaItem(restoredItem, lastPosition)
                    player.prepare()
                    screen = Screen.PLAYER
                }
            }
        }
    }

    LaunchedEffect(player, playing) {
        while (true) {
            if (playing) {
                val uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                if (uri.isNotBlank()) {
                    preferences.edit()
                        .putString("last_played_uri", uri)
                        .putLong("last_played_position", player.currentPosition.coerceAtLeast(0L))
                        .putString("last_played_title", player.mediaMetadata.title?.toString().orEmpty())
                        .putString("last_played_artist", player.mediaMetadata.artist?.toString().orEmpty())
                        .apply()
                }
            }
            delay(2000)
        }
    }

    BackHandler {
        if (screenHistory.isNotEmpty()) screen = screenHistory.removeAt(screenHistory.lastIndex)
        else if (screen != Screen.LIBRARY) navigateTo(Screen.LIBRARY)
        else Toast.makeText(appContext, "Kamu sudah berada di halaman utama", Toast.LENGTH_SHORT).show()
    }

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
                                val folders = remember(tracks) { listOf("Semua lokasi") + tracks.map { it.storagePath.substringBeforeLast('/', it.storagePath).ifBlank { "Lokasi tidak diketahui" } }.distinct().sorted() }
                                LaunchedEffect(folders) { if (selectedFolder !in folders) selectedFolder = "Semua lokasi" }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tracks.size} file musik terdeteksi", color = Muted, fontSize = 12.sp)
                                    TextButton(onClick = { tracks = emptyList() }) { Text("Muat ulang", color = Mint) }
                                }
                                Text("FILTER LOKASI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    folders.forEach { folder ->
                                        FilterChip(selected = selectedFolder == folder, onClick = { selectedFolder = folder },
                                            label = { Text(if (folder == "Semua lokasi") folder else folder.substringAfterLast('/')) })
                                    }
                                }
                                val libraryListState = rememberLazyListState(initialFirstVisibleItemIndex = libraryScrollIndex, initialFirstVisibleItemScrollOffset = libraryScrollOffset)
                                LaunchedEffect(libraryListState) {
                                    snapshotFlow { libraryListState.firstVisibleItemIndex to libraryListState.firstVisibleItemScrollOffset }
                                        .collect { (index, offset) -> libraryScrollIndex = index; libraryScrollOffset = offset }
                                }
                                LazyColumn(state = libraryListState, contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    items(tracks.filter { (selectedFolder == "Semua lokasi" || it.storagePath.substringBeforeLast('/', it.storagePath).ifBlank { "Lokasi tidak diketahui" } == selectedFolder) && (it.title.contains(search, true) || it.artist.contains(search, true)) }, key = { it.id }) { track ->
                                        TrackRow(track, selected?.id == track.id, onClick = {
                                            selected = track
                                            player.setMediaItems(tracks.map { item ->
                                                MediaItem.Builder().setUri(item.uri).setMediaMetadata(
                                                    MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                                                        .setAlbumTitle(item.album).build()
                                                ).build()
                                            }, tracks.indexOf(track), 0L); player.prepare(); player.play()
                                            navigateTo(Screen.PLAYER)
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Screen.ONLINE -> OnlineMediaScreen(
                        onPlayAudio = { url ->
                            if (url.startsWith("https://")) {
                                selected = null
                                player.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(
                                    MediaMetadata.Builder().setTitle(url.substringAfterLast('/').substringBefore('?').ifBlank { "Online stream" }).build()
                                ).build())
                                player.prepare(); player.play(); screen = Screen.PLAYER
                            }
                        },
                        onDownload = { url, _ -> startPrivateDownload(url) },
                        downloadBusy = downloadBusy, downloadMessage = downloadMessage
                    )
                    Screen.OFFLINE -> OfflineMediaScreen(
                        files = offlineFiles,
                        onPlay = { file ->
                            selected = null
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                            player.prepare(); player.play(); screen = Screen.PLAYER
                        },
                        onDelete = { file -> file.delete(); offlineFiles = offlineFiles.filterNot { it == file } }
                    )
                    Screen.PLAYER -> FullPlayer(
                        track = selected, player = player, playing = playing, spectrum = spectrum, spectrumStatus = spectrumStatus, spectrumStyle = spectrumStyle, metadata = liveMetadata,
                        hasRecordPermission = hasRecordPermission, requestRecordPermission = requestRecordPermission,
                        lyricsEnabled = lyricsEnabled, lyricsText = lyricsText, seekBarStyle = seekBarStyle,
                        onLyricsChange = { lyricsText = it; preferences.edit().putString(lyricsKey, it).apply() },
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
                    Screen.SETTINGS -> SettingsScreen(spectrum, spectrumStyle, seekBarStyle,
                        onStyleSelected = { style -> spectrumStyle = style; preferences.edit().putString("spectrum_style", style.name).apply() },
                        onSeekBarStyleSelected = { style -> seekBarStyle = style; preferences.edit().putString("seekbar_style", style.name).apply() },
                        lyricsEnabled = lyricsEnabled,
                        onLyricsEnabled = { enabled -> lyricsEnabled = enabled; preferences.edit().putBoolean("lyrics_enabled", enabled).apply() },
                        audioQuality = audioDownloadQuality, videoQuality = videoDownloadQuality,
                        onAudioQuality = { value -> audioDownloadQuality = value; preferences.edit().putString("download_audio_quality", value).apply() },
                        onVideoQuality = { value -> videoDownloadQuality = value; preferences.edit().putString("download_video_quality", value).apply() },
                        selectedTheme = selectedTheme,
                        onThemeSelected = { name ->
                            selectedTheme = name
                            activePalette.value = AppPalettes.first { it.name == name }
                            preferences.edit().putString("app_theme", name).apply()
                        },
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { tag ->
                            selectedLanguage = tag
                            preferences.edit().putString("app_language", tag).apply()
                            val locale = if (tag == "auto") Locale.getDefault() else Locale.forLanguageTag(tag)
                            Locale.setDefault(locale)
                            val configuration = appContext.resources.configuration
                            configuration.setLocale(locale)
                            appContext.resources.updateConfiguration(configuration, appContext.resources.displayMetrics)
                        },
                        selectedVisualStyle = selectedVisualStyle,
                        appVersion = BuildConfig.VERSION_NAME,
                        onVisualStyleSelected = { name ->
                            selectedVisualStyle = name
                            activeVisualStyle.value = VisualStyle.valueOf(name)
                            preferences.edit().putString("visual_style", name).apply()
                        }
                    )
                }
            }

            if (screen != Screen.PLAYER && screen != Screen.EQUALIZER) {
                MiniPlayer(track = selected, playing = playing, onOpen = { screen = Screen.PLAYER }, onToggle = { if (player.isPlaying) player.pause() else player.play() })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .shadow(if (activeVisualStyle.value == VisualStyle.THREE_D) 18.dp else 0.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(visualSurfaceBrush())
                    .border(if (activeVisualStyle.value == VisualStyle.GLASS) 1.dp else 1.dp, visualSurfaceBorderBrush(), RoundedCornerShape(22.dp))
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(Screen.LIBRARY, Icons.Default.LibraryMusic, appText("library", selectedLanguage)),
                    Triple(Screen.ONLINE, Icons.Default.Language, appText("online", selectedLanguage)),
                    Triple(Screen.OFFLINE, Icons.Default.DownloadDone, appText("offline", selectedLanguage)),
                    Triple(Screen.PLAYER, Icons.Default.GraphicEq, appText("now_playing", selectedLanguage)),
                    Triple(Screen.EQUALIZER, Icons.Default.Equalizer, "EQ"),
                    Triple(Screen.SETTINGS, Icons.Default.Settings, "Settings")
                )
                tabs.forEach { (target, icon, label) ->
                    val active = screen == target
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (active) Mint.copy(alpha = .16f) else Color.Transparent)
                            .clickable { navigateTo(target) }
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
    Row(Modifier.fillMaxWidth().shadow(if (activeVisualStyle.value == VisualStyle.THREE_D) 12.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
        .clip(RoundedCornerShape(16.dp)).background(if (active) Brush.verticalGradient(listOf(Mint.copy(alpha = .22f), Color.White.copy(alpha = .035f))) else visualSurfaceBrush())
        .border(
            if (activeVisualStyle.value == VisualStyle.GLASS || activeVisualStyle.value == VisualStyle.THREE_D) 1.dp else 0.dp,
            if (activeVisualStyle.value == VisualStyle.GLASS) visualSurfaceBorderBrush()
            else Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .22f))),
            RoundedCornerShape(16.dp)
        ).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Mint) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (active) Icons.Default.Equalizer else Icons.Default.PlayArrow, null, tint = if (active) Mint else Muted)
        }
    }
}

@Composable
private fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Rincian lagu", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun detail(label: String, value: String) {
                    Column {
                        Text(label, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(value.ifBlank { "Tidak tersedia" }, color = Color.White, fontSize = 13.sp)
                    }
                }
                detail("JUDUL", track.title)
                detail("ARTIS", track.artist)
                detail("ALBUM", track.album)
                detail("ARTIST ALBUM", track.albumArtist)
                detail("TAHUN", if (track.year > 0) track.year.toString() else "")
                detail("NOMOR TRACK", if (track.trackNumber > 0) track.trackNumber.toString() else "")
                detail("KOMPOSER", track.composer)
                detail("DURASI", formatTime(track.duration))
                detail("JALUR PENYIMPANAN", track.storagePath.ifBlank { track.uri.toString() })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("TUTUP", color = Mint) } },
        containerColor = Panel
    )
}

@Composable
private fun MiniPlayer(track: Track?, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(visualSurfaceBrush())
        .border(1.dp, visualSurfaceBorderBrush(), RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Album, null, tint = Mint, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track?.title ?: "Belum ada lagu diputar", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "Ketuk untuk membuka pemutar", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Mint) }
    }
}

@Composable
private fun SeekBarPreview(style: SeekBarStyle, modifier: Modifier = Modifier) {
    MusicSeekBar(position = .62f, style = style, onPositionChange = {}, onSeekFinished = {}, interactive = false, modifier = modifier.fillMaxWidth().height(28.dp))
}

@Composable
private fun MusicSeekBar(
    position: Float, style: SeekBarStyle, onPositionChange: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit, interactive: Boolean = true, modifier: Modifier = Modifier
) {
    var dragProgress by remember(position) { mutableFloatStateOf(position.coerceIn(0f, 1f)) }
    val progress = dragProgress
    val gestureModifier = if (interactive) Modifier
        .pointerInput(Unit) {
            detectTapGestures { point ->
                dragProgress = (point.x / size.width).coerceIn(0f, 1f)
                onPositionChange(dragProgress)
                onSeekFinished(dragProgress)
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { point -> dragProgress = (point.x / size.width).coerceIn(0f, 1f); onPositionChange(dragProgress) },
                onDrag = { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    onPositionChange(dragProgress)
                    change.consume()
                },
                onDragEnd = { onSeekFinished(dragProgress) },
                onDragCancel = { onSeekFinished(dragProgress) }
            )
        }
    else Modifier
    Canvas(modifier.then(gestureModifier)) {
        val centerY = size.height / 2f
        val trackHeight = when (style) {
            SeekBarStyle.THIN -> 2.dp.toPx()
            SeekBarStyle.CAPSULE -> 8.dp.toPx()
            SeekBarStyle.WAVE -> 4.dp.toPx()
            else -> 5.dp.toPx()
        }
        val start = androidx.compose.ui.geometry.Offset(0f, centerY)
        val end = androidx.compose.ui.geometry.Offset(size.width, centerY)
        val activeEnd = androidx.compose.ui.geometry.Offset(size.width * progress, centerY)
        val inactive = Color.White.copy(alpha = .18f)
        if (style == SeekBarStyle.DOTTED || style == SeekBarStyle.DASHED) {
            val count = if (style == SeekBarStyle.DOTTED) 44 else 28
            val step = size.width / count
            repeat(count) { i ->
                val x = i * step + step / 2f
                val filled = x <= size.width * progress
                if (style == SeekBarStyle.DOTTED) drawCircle(if (filled) Mint else inactive, 1.8.dp.toPx(), androidx.compose.ui.geometry.Offset(x, centerY))
                else drawRoundRect(if (filled) Mint else inactive, androidx.compose.ui.geometry.Offset(x - step * .34f, centerY - 2.dp.toPx()), androidx.compose.ui.geometry.Size(step * .68f, 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
        } else if (style == SeekBarStyle.DOUBLE) {
            drawLine(inactive, androidx.compose.ui.geometry.Offset(0f, centerY - 3.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, centerY - 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(inactive, androidx.compose.ui.geometry.Offset(0f, centerY + 3.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, centerY + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Mint, androidx.compose.ui.geometry.Offset(0f, centerY - 3.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width * progress, centerY - 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF8B7CFF), androidx.compose.ui.geometry.Offset(0f, centerY + 3.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width * progress, centerY + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        } else if (style == SeekBarStyle.NEEDLE) {
            drawLine(inactive, start, end, 3.dp.toPx(), StrokeCap.Round)
            drawLine(Mint, start, activeEnd, 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(activeEnd.x, centerY - 9.dp.toPx()), androidx.compose.ui.geometry.Offset(activeEnd.x, centerY + 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Mint, 3.dp.toPx(), activeEnd)
        } else if (style == SeekBarStyle.SEGMENTED || style == SeekBarStyle.BLOCKS) {
            val count = if (style == SeekBarStyle.BLOCKS) 18 else 34
            val gap = if (style == SeekBarStyle.BLOCKS) 2.dp.toPx() else 3.dp.toPx()
            val segmentWidth = (size.width - gap * (count - 1)) / count
            repeat(count) { i ->
                val x = i * (segmentWidth + gap)
                val filled = x + segmentWidth / 2f <= size.width * progress
                drawRoundRect(if (filled) Mint else inactive, androidx.compose.ui.geometry.Offset(x, centerY - (if (style == SeekBarStyle.BLOCKS) 4.dp.toPx() else 2.dp.toPx())), androidx.compose.ui.geometry.Size(segmentWidth, if (style == SeekBarStyle.BLOCKS) 8.dp.toPx() else 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        } else if (style == SeekBarStyle.WAVE) {
            val path = Path()
            val amplitude = 3.dp.toPx()
            val samples = 90
            for (i in 0..samples) {
                val x = size.width * i / samples
                val y = centerY + sin(i * .34f) * amplitude
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, inactive, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val activePath = Path()
            val limit = (samples * progress).toInt().coerceIn(0, samples)
            for (i in 0..limit) {
                val x = size.width * i / samples
                val y = centerY + sin(i * .34f) * amplitude
                if (i == 0) activePath.moveTo(x, y) else activePath.lineTo(x, y)
            }
            drawPath(activePath, Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(inactive, start, end, trackHeight, StrokeCap.Round)
            if (style == SeekBarStyle.GLOW && progress > 0f) {
                drawLine(Mint.copy(alpha = .22f), start, activeEnd, 15.dp.toPx(), StrokeCap.Round)
                drawLine(Mint.copy(alpha = .35f), start, activeEnd, 9.dp.toPx(), StrokeCap.Round)
            }
            val brush = if (style == SeekBarStyle.GRADIENT || style == SeekBarStyle.RAINBOW) Brush.horizontalGradient(if (style == SeekBarStyle.RAINBOW) listOf(Color(0xFFFF5D73), Color(0xFFFFC45C), Color(0xFF67E7A5), Color(0xFF55B8FF), Color(0xFFB88BFF)) else listOf(Mint, Color(0xFF8B7CFF), Color(0xFFFF7DB8))) else Brush.horizontalGradient(listOf(Mint, Mint))
            drawLine(brush, start, activeEnd, trackHeight, StrokeCap.Round)
            when (style) {
                SeekBarStyle.CLASSIC -> {
                    drawCircle(Color.White, 6.dp.toPx(), activeEnd)
                    drawCircle(Mint, 4.dp.toPx(), activeEnd)
                }
                SeekBarStyle.THIN -> drawCircle(Mint, 3.dp.toPx(), activeEnd)
                SeekBarStyle.GLOW -> drawCircle(Mint.copy(alpha = .25f), 10.dp.toPx(), activeEnd)
                else -> Unit
            }
        }
    }
}

@Composable
private fun FullPlayer(
    track: Track?, player: ExoPlayer, playing: Boolean, spectrum: List<Float>, spectrumStatus: String, spectrumStyle: SpectrumStyle, metadata: androidx.media3.common.MediaMetadata?,
    hasRecordPermission: Boolean, requestRecordPermission: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit,
    lyricsEnabled: Boolean, lyricsText: String, onLyricsChange: (String) -> Unit, seekBarStyle: SeekBarStyle
) {
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var showDetails by remember { mutableStateOf(false) }
    LaunchedEffect(player, playing, track) {
        while (true) {
            duration = player.duration.takeIf { it > 0 } ?: track?.duration ?: 0L
            position = if (duration > 0) player.currentPosition.toFloat() / duration else 0f
            delay(400)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            Text("NOW PLAYING", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        }
        if (showDetails) {
            val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: track?.uri
            val detailsTrack = track ?: Track(-1L,
                player.currentMediaItem?.mediaMetadata?.title?.toString() ?: metadata?.title?.toString() ?: "Online stream",
                player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: metadata?.artist?.toString() ?: "Unknown artist",
                currentUri ?: Uri.EMPTY, duration.toLong(),
                player.currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: metadata?.albumTitle?.toString() ?: "Unknown album",
                storagePath = currentUri?.path ?: currentUri?.toString().orEmpty())
            TrackDetailsDialog(detailsTrack, onDismiss = { showDetails = false })
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            val coverSize = maxWidth.coerceAtMost(210.dp)
            Box(Modifier.size(coverSize).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xAA42677A), Color(0xAA17263D))))
                .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(28.dp)).padding(9.dp)) {
                AlbumCover(uri = track?.let { if (it.albumId >= 0) Uri.parse("content://media/external/audio/albumart/${it.albumId}") else null } ?: metadata?.artworkUri,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(track?.title ?: player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Belum ada lagu diputar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = { showDetails = true }) { Icon(Icons.Default.Info, "Rincian lagu", tint = Mint) }
        }
        Text(track?.artist ?: metadata?.artist?.toString() ?: "Online stream", color = Muted, fontSize = 14.sp)
        Text(listOfNotNull(track?.album?.takeIf { it != "Unknown album" } ?: metadata?.albumTitle?.toString(), track?.year?.takeIf { it > 0 }?.toString(), track?.trackNumber?.takeIf { it > 0 }?.let { "Track $it" }).joinToString(" • "), color = Muted, fontSize = 11.sp, maxLines = 1)
        track?.composer?.takeIf { it.isNotBlank() }?.let { Text("Composer: $it", color = Muted, fontSize = 10.sp) }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .045f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            SpectrumRenderer(spectrum, spectrumStyle, Modifier.fillMaxWidth().height(64.dp))
        }
        Text(spectrumStatus, color = if (spectrumStatus.contains("LIVE")) Mint else Muted, fontSize = 10.sp)
        if (!hasRecordPermission) TextButton(onClick = requestRecordPermission) { Text("Izinkan akses audio untuk spectrum", color = Mint) }
        Spacer(Modifier.height(10.dp))
        MusicSeekBar(position = position, style = seekBarStyle, onPositionChange = { position = it }, onSeekFinished = { value -> if (duration > 0) player.seekTo((value * duration).toLong()) }, modifier = Modifier.fillMaxWidth().height(28.dp))
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
                OutlinedTextField(value = lyricsText, onValueChange = onLyricsChange, modifier = Modifier.fillMaxWidth().height(76.dp),
                    placeholder = { Text("Tempel atau tulis lirik lagu di sini…", color = Muted) },
                    colors = fieldColors(), shape = RoundedCornerShape(14.dp), minLines = 2, maxLines = 3)
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
        SpectrumStyle.POWERAMP -> SpectrumPoweramp(levels, modifier)
        SpectrumStyle.VU -> SpectrumBarsPremium(levels, modifier, true)
        SpectrumStyle.MIRROR_WAVE -> SpectrumWave(levels.reversed(), modifier)
        SpectrumStyle.SPECTRUM_3D -> SpectrumPoweramp(levels.reversed(), modifier)
        SpectrumStyle.ORBIT -> SpectrumCircular(levels.reversed(), modifier)
        SpectrumStyle.DIGITAL -> SpectrumDots(levels.reversed(), modifier)
        SpectrumStyle.FIRE -> SpectrumBarsPremium(levels, modifier, false)
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
private fun SpectrumPoweramp(levels: List<Float>, modifier: Modifier) {
    val values = levels.mapIndexed { i, v ->
        animateFloatAsState(v.coerceIn(.025f, 1f), tween(85, easing = FastOutSlowInEasing), label = "poweramp-$i").value
    }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 2.2.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val segments = 9
        val segmentGap = 1.5.dp.toPx()
        values.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            val activeSegments = (value * segments).roundToInt().coerceIn(1, segments)
            for (segment in 0 until segments) {
                val segmentHeight = (size.height - segmentGap * (segments - 1)) / segments
                val y = size.height - (segment + 1) * segmentHeight - segment * segmentGap
                val lit = segment < activeSegments
                val color = when {
                    !lit -> Color(0xFF34373D).copy(alpha = .55f)
                    segment >= 7 -> Color(0xFFFFD36A)
                    segment >= 5 -> Color(0xFFFFA52D)
                    else -> Color(0xFFFF7A18)
                }
                drawRoundRect(color, androidx.compose.ui.geometry.Offset(x, y),
                    androidx.compose.ui.geometry.Size(barWidth, segmentHeight),
                    androidx.compose.ui.geometry.CornerRadius(barWidth * .25f, barWidth * .25f))
            }
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
private fun SettingsScreen(
    spectrum: List<Float>, selectedStyle: SpectrumStyle, seekBarStyle: SeekBarStyle, onStyleSelected: (SpectrumStyle) -> Unit, onSeekBarStyleSelected: (SeekBarStyle) -> Unit,
    lyricsEnabled: Boolean, onLyricsEnabled: (Boolean) -> Unit,
    audioQuality: String, videoQuality: String, onAudioQuality: (String) -> Unit, onVideoQuality: (String) -> Unit,
    selectedTheme: String, onThemeSelected: (String) -> Unit,
    selectedLanguage: String, onLanguageSelected: (String) -> Unit,
    selectedVisualStyle: String, appVersion: String, onVisualStyleSelected: (String) -> Unit
) {
    var section by rememberSaveable { mutableStateOf("") }
    fun tx(key: String) = appText(key, selectedLanguage)
    BackHandler(enabled = section.isNotEmpty()) { section = "" }
    val pageTitle = when (section) {
        "theme" -> tx("theme"); "visualizer" -> tx("visualizer"); "seekbar" -> "Progress Bar"; "lyrics" -> tx("lyrics")
        "download" -> tx("downloads"); "credits" -> tx("credits"); "updates" -> "Pembaruan aplikasi"
        "language" -> tx("language"); "appearance" -> tx("appearance"); else -> tx("settings")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (section.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { section = "" }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Column {
                    Text(tx("settings").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(pageTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Text(tx("settings").uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(5.dp))
            Text(tx("settings_sub"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(tx("settings_hint"), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
        }
        @Composable
        fun settingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
            Row(Modifier.fillMaxWidth().shadow(if (activeVisualStyle.value == VisualStyle.THREE_D) 14.dp else 0.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp)).background(visualSurfaceBrush())
                .border(if (activeVisualStyle.value == VisualStyle.GLASS) 1.dp else if (activeVisualStyle.value == VisualStyle.THREE_D) 1.5.dp else 1.dp, visualSurfaceBorderBrush(), RoundedCornerShape(18.dp))
                .clickable(onClick = onClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).shadow(if (activeVisualStyle.value == VisualStyle.THREE_D) 8.dp else 0.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Mint.copy(alpha = .22f)).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Mint, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        when (section) {
            "" -> {
                @Composable fun groupTitle(title: String, subtitle: String) {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
                        Text(title.uppercase(), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                        Text(subtitle, color = Muted, fontSize = 11.sp)
                    }
                }
                groupTitle("PEMUTAR & AUDIO", "Kontrol pemutaran dan efek suara")
                settingsCard(Icons.Default.GraphicEq, tx("visualizer"), "${selectedStyle.title} • ${SpectrumStyle.values().size} gaya spektrum") { section = "visualizer" }
                Spacer(Modifier.height(8.dp))
                settingsCard(Icons.Default.LinearScale, "Progress Bar", "${seekBarStyle.title} • ${SeekBarStyle.values().size} gaya tampilan") { section = "seekbar" }
                Spacer(Modifier.height(8.dp))
                settingsCard(Icons.Default.Lyrics, tx("lyrics"), if (lyricsEnabled) "Panel lirik aktif di Now Playing" else "Panel lirik sedang nonaktif") { section = "lyrics" }

                groupTitle("TAMPILAN", "Tema warna dan karakter antarmuka")
                settingsCard(Icons.Default.Palette, tx("theme"), "$selectedTheme • ${AppPalettes.size} pilihan warna") { section = "theme" }
                Spacer(Modifier.height(8.dp))
                settingsCard(Icons.Default.ViewInAr, tx("appearance"), "$selectedVisualStyle • Glass, 3D, Neon, Aurora, Poweramp") { section = "appearance" }

                groupTitle("KOLEKSI & UNDUHAN", "Preferensi file audio dan video")
                settingsCard(Icons.Default.Download, tx("downloads"), "Audio $audioQuality • Video $videoQuality") { section = "download" }

                groupTitle("APLIKASI", "Bahasa, pembaruan, dan informasi")
                settingsCard(Icons.Default.Translate, tx("language"), if (selectedLanguage == "auto") "Otomatis mengikuti bahasa perangkat" else Locale.forLanguageTag(selectedLanguage).getDisplayName(Locale.getDefault())) { section = "language" }
                Spacer(Modifier.height(8.dp))
                settingsCard(Icons.Default.SystemUpdate, "Pembaruan aplikasi", "Periksa versi terbaru dan unduh APK") { section = "updates" }
                Spacer(Modifier.height(8.dp))
                settingsCard(Icons.Default.Info, tx("credits"), "Informasi proyek, teknologi, dan atribusi") { section = "credits" }
            }
            "theme" -> {
                Text("Pilih salah satu dari ${AppPalettes.size} kombinasi warna. Perubahan langsung diterapkan.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                AppPalettes.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { palette ->
                            val active = palette.name == selectedTheme
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(palette.panel)
                                .border(2.dp, if (active) palette.accent else Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp))
                                .clickable { onThemeSelected(palette.name) }.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(palette.accent))
                                    Spacer(Modifier.width(8.dp))
                                    Text(palette.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    if (active) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.CheckCircle, null, tint = palette.accent, modifier = Modifier.size(16.dp)) }
                                }
                                Spacer(Modifier.height(10.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(palette.accent, palette.muted))))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
            "visualizer" -> {
                Text("Pilih gaya spektrum untuk Now Playing. Pratinjau memakai data audio saat ini.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                SpectrumStyle.values().toList().chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { style ->
                            val active = style == selectedStyle
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (active) Mint.copy(alpha = .11f) else Color.White.copy(alpha = .045f))
                                .border(1.dp, if (active) Mint.copy(alpha = .65f) else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                                .clickable { onStyleSelected(style) }.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(style.title, color = if (active) Mint else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (active) Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(17.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x55202F43)).padding(5.dp)) {
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
                Text("Visualizer membaca FFT dari output audio pemutar, bukan mikrofon.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            "seekbar" -> {
                Text("Pilih bentuk progress bar di bawah visualizer. Semua gaya mendukung sentuhan dan geser untuk mencari posisi lagu.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(14.dp))
                SeekBarStyle.values().toList().chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { style ->
                            val active = style == seekBarStyle
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (active) Mint.copy(alpha = .10f) else Color.White.copy(alpha = .045f))
                                .border(1.dp, if (active) Mint.copy(alpha = .70f) else Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp))
                                .clickable { onSeekBarStyleSelected(style) }.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(style.title, color = if (active) Mint else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (active) Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                SeekBarPreview(style, Modifier.fillMaxWidth().height(20.dp))
                                Spacer(Modifier.height(7.dp))
                                Text(style.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, minLines = 2)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
            "lyrics" -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .055f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Tampilkan lirik", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Tampilkan atau sembunyikan panel lirik di Now Playing.", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = lyricsEnabled, onCheckedChange = onLyricsEnabled)
            }
            "download" -> {
                Text("Preferensi kualitas untuk sumber yang menyediakan beberapa varian. URL file langsung tetap diunduh sesuai kualitas asli sumber.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(18.dp))
                Text("Kualitas audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf("Original", "128", "192", "256", "320").forEach { quality ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { onAudioQuality(quality) }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (quality == "Original") "Original" else "$quality kbps", color = Color.White, modifier = Modifier.weight(1f))
                        Icon(if (audioQuality == quality) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (audioQuality == quality) Mint else Muted)
                    }
                }
                Spacer(Modifier.height(14.dp)); Divider(color = Color.White.copy(alpha = .12f)); Spacer(Modifier.height(14.dp))
                Text("Kualitas video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf("360p", "480p", "720p", "1080p").forEach { quality ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { onVideoQuality(quality) }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(quality, color = Color.White, modifier = Modifier.weight(1f))
                        Icon(if (videoQuality == quality) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (videoQuality == quality) Mint else Muted)
                    }
                }
            }
            "language" -> {
                val locales = remember { SupportedAppLanguages.map { Locale.forLanguageTag(it) } }
                Text(tx("choose_language"), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(visualSurfaceBrush()).clickable { onLanguageSelected("auto") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tx("auto"), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(tx("follow_system"), color = Muted, fontSize = 11.sp)
                    }
                    Icon(if (selectedLanguage == "auto") Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = Mint)
                }
                Spacer(Modifier.height(8.dp))
                locales.forEach { locale ->
                    val tag = locale.toLanguageTag()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onLanguageSelected(tag) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(locale.language.uppercase(), color = Muted, fontSize = 10.sp)
                        if (selectedLanguage == tag) Icon(Icons.Default.Check, null, tint = Mint, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            "appearance" -> {
                Text(tx("appearance_hint"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                VisualStyle.values().forEach { style ->
                    val active = selectedVisualStyle == style.name
                    val cardBrush = when (style) {
                        VisualStyle.GLASS -> Brush.verticalGradient(listOf(Color(0xDDEAF7FF), Color(0x883D6685), Color(0xAA172B40), Color(0xCC091522)))
                        VisualStyle.THREE_D -> Brush.verticalGradient(listOf(Color(0xFF526783), Panel, Color(0xFF070B13)))
                        VisualStyle.NEON -> Brush.linearGradient(listOf(Mint.copy(alpha = .28f), Panel))
                        VisualStyle.AURORA -> Brush.linearGradient(listOf(Color(0xFF315C67), Color(0xFF33274D), Panel))
                        VisualStyle.MINIMAL -> Brush.linearGradient(listOf(Panel, Panel))
                        VisualStyle.POWERAMP -> Brush.verticalGradient(listOf(Color(0xFF282B31), Color(0xFF101114), Color(0xFF050607)))
                    }
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).shadow(if (style == VisualStyle.THREE_D) 18.dp else 0.dp, RoundedCornerShape(20.dp), clip = false).clip(RoundedCornerShape(20.dp))
                        .background(cardBrush).border(if (active) 2.dp else 1.dp, if (style == VisualStyle.GLASS) Brush.verticalGradient(listOf(Color.White.copy(alpha = .82f), Color(0xFFB8D8F0).copy(alpha = .30f), Color(0xFF6F9AB9).copy(alpha = .45f))) else if (active) Brush.linearGradient(listOf(Mint, Mint.copy(alpha = .35f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .06f))), RoundedCornerShape(20.dp))
                        .clickable { onVisualStyleSelected(style.name) }.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(style.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            if (active) Icon(Icons.Default.CheckCircle, null, tint = Mint)
                        }
                        Text(style.description, color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            repeat(3) { index ->
                                Box(Modifier.weight(1f).height(if (style == VisualStyle.THREE_D) 25.dp else 18.dp)
                                    .clip(RoundedCornerShape(if (style == VisualStyle.THREE_D) 5.dp else 12.dp))
                                    .background(if (index == 1) Mint.copy(alpha = .75f) else Color.White.copy(alpha = .10f))
                                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp)))
                            }
                        }
                    }
                }
            }

            "updates" -> {
                val context = LocalContext.current
                var checking by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var updateMessage by remember { mutableStateOf("Versi terpasang: v$appVersion") }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(visualSurfaceBrush())
                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("Versi saat ini", color = Muted, fontSize = 12.sp)
                    Text("v$appVersion", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(updateMessage, color = if (updateInfo != null) Mint else Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (updateInfo != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Versi terbaru: v${updateInfo!!.version}", color = Mint, fontWeight = FontWeight.Bold)
                        Text(updateInfo!!.notes.ifBlank { "Tidak ada catatan rilis." }, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {
                        checking = true
                        updateMessage = "Sedang memeriksa GitHub Releases..."
                        kotlinx.coroutines.MainScope().launch {
                            try {
                                val found = fetchLatestAppUpdate(appVersion)
                                updateInfo = found
                                updateMessage = if (found == null) "Aplikasi sudah menggunakan versi terbaru." else "Pembaruan tersedia."
                            } catch (e: Exception) {
                                updateInfo = null
                                updateMessage = e.message ?: "Tidak dapat memeriksa pembaruan."
                            } finally { checking = false }
                        }
                    }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (checking) "Memeriksa..." else "Periksa pembaruan")
                    }
                    if (updateInfo != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            try {
                                downloadAppUpdate(context, updateInfo!!)
                                updateMessage = "Unduhan dimulai. Buka notifikasi unduhan untuk memasang APK."
                            } catch (e: Exception) {
                                updateMessage = "Gagal memulai unduhan: ${e.message}"
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unduh pembaruan")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Pembaruan diambil dari GitHub Releases resmi proyek. Setelah APK selesai diunduh, buka notifikasi unduhan untuk melanjutkan pemasangan. Android mungkin meminta izin instalasi dari sumber ini.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            "credits" -> {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .045f))
                    .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(18.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    @Composable fun credit(label: String, value: String) {
                        Column {
                            Text(label.uppercase(), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(value, color = Color.White, fontSize = 13.sp)
                        }
                    }
                    credit("Project reference", "ryuzee_project")
                    credit("App & integration", "Froglands")
                    credit("Platform", "Android • Kotlin • Jetpack Compose")
                    credit("Audio engine", "AndroidX Media3 / ExoPlayer")
                    credit("Visualizer & effects", "Android Audio Visualizer API • Equalizer • BassBoost")
                    credit("Acknowledgement", "Terima kasih kepada para developer dan maintainer library open-source yang mendukung proyek ini.")
                }
                Spacer(Modifier.height(10.dp))
                Text("Nama proyek dan library dicantumkan sebagai atribusi; hak cipta masing-masing tetap milik pemiliknya.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
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
