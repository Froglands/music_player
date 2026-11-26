package com.froglands.musicplayer

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class AppUpdateInfo(val version: String, val notes: String, val apkUrl: String)

internal suspend fun fetchLatestAppUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.github.com/repos/Froglands/music_player/releases/latest").openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000; readTimeout = 12000; requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Froglands-MusicPlayer-Android")
    }
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("Gagal memeriksa pembaruan (HTTP ${connection.responseCode}).")
        val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name").removePrefix("v").trim()
        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apk = ""
        for (i in 0 until assets.length()) { val a=assets.getJSONObject(i); if(a.optString("name").endsWith(".apk",true)){apk=a.optString("browser_download_url");break} }
        if (apk.isBlank()) return@withContext null
        val latest=tag.split(".").map{it.toIntOrNull()?:0}; val current=currentVersion.split(".").map{it.toIntOrNull()?:0}
        val cmp=(0 until maxOf(latest.size,current.size)).firstNotNullOfOrNull { i -> (latest.getOrElse(i){0}).compareTo(current.getOrElse(i){0}).takeIf{it!=0} } ?: 0
        if(cmp<=0) null else AppUpdateInfo(tag,json.optString("body","Pembaruan tersedia."),apk)
    } finally { connection.disconnect() }
}

internal fun downloadAppUpdate(context: Context, update: AppUpdateInfo) {
    val request=android.app.DownloadManager.Request(Uri.parse(update.apkUrl))
        .setTitle("Music Player v${update.version}").setDescription("Mengunduh pembaruan aplikasi")
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,"MusicPlayer-${update.version}.apk")
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
}

data class Track(
    val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long,
    val album: String = "Unknown album", val albumArtist: String = "", val albumId: Long = -1L,
    val year: Int = 0, val trackNumber: Int = 0, val composer: String = "", val storagePath: String = ""
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: PlayerNotificationManager
    private var nativeBridge: NativeMusicBridge? = null
    private var webView: WebView? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) nativeBridge?.scanTracks()
    }
    private val recordPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestAudioPermissionFromWeb() {
        val p=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        audioPermission.launch(p)
    }
    fun requestRecordPermissionFromWeb() { recordPermission.launch(Manifest.permission.RECORD_AUDIO) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player=ExoPlayer.Builder(this).build()
        mediaSession=MediaSession.Builder(this,player).build()
        notificationManager=PlayerNotificationManager.Builder(this,1001,"music_playback")
            .setChannelNameResourceId(android.R.string.untitled)
            .setMediaDescriptionAdapter(object:PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(p:Player):CharSequence =
                    p.mediaMetadata.title ?: p.currentMediaItem?.localConfiguration?.uri?.lastPathSegment ?: "Music Player"
                override fun getCurrentContentText(p:Player):CharSequence? = p.mediaMetadata.artist ?: p.mediaMetadata.albumTitle ?: "Now playing"
                override fun createCurrentContentIntent(p:Player):PendingIntent? {
                    val intent=Intent(this@MainActivity,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP}
                    return PendingIntent.getActivity(this@MainActivity,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
                override fun getCurrentLargeIcon(p:Player,callback:PlayerNotificationManager.BitmapCallback):android.graphics.Bitmap? {
                    val bytes=p.mediaMetadata.artworkData ?: return null
                    return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                }
            }).setSmallIconResourceId(android.R.drawable.ic_media_play).build()
        notificationManager.setMediaSessionToken(mediaSession.platformToken)
        notificationManager.setUseNextAction(true); notificationManager.setUsePreviousAction(true)
        notificationManager.setUsePlayPauseActions(true); notificationManager.setPlayer(player)

        val audioP=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if(ContextCompat.checkSelfPermission(this,audioP)!=PackageManager.PERMISSION_GRANTED) audioPermission.launch(audioP)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val view=WebView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled=true; settings.domStorageEnabled=true
            settings.allowFileAccess=true; settings.allowContentAccess=false
            settings.allowFileAccessFromFileURLs=false; settings.allowUniversalAccessFromFileURLs=false
            settings.mediaPlaybackRequiresUserGesture=true
            webViewClient=object:WebViewClient(){
                override fun onPageFinished(v:WebView?,url:String?){super.onPageFinished(v,url);nativeBridge?.pageReady()}
            }
            webChromeClient=WebChromeClient()
        }
        webView=view
        nativeBridge=NativeMusicBridge(this,view,player)
        view.addJavascriptInterface(nativeBridge!!,"MusicNative")
        setContentView(view)
        view.loadUrl("file:///android_asset/www/index.html")
    }

    internal suspend fun queryTracks(): List<Track> = withContext(Dispatchers.IO) {
        val out=mutableListOf<Track>()
        val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,MediaStore.Audio.Media.YEAR,MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.COMPOSER,MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE)
        val selection="${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        contentResolver.query(collection,projection,selection,null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->
            while(c.moveToNext()){
                val id=c.getLong(0); val duration=c.getLong(3); val mime=c.getString(12).orEmpty()
                if(duration<=0 || (mime.isNotBlank() && !mime.startsWith("audio/"))) continue
                val abs=c.getString(11).orEmpty(); val rel=c.getString(10).orEmpty()
                out+=Track(id,c.getString(1)?: "Unknown title",(c.getString(2)?: "Unknown artist").takeUnless{it=="<unknown>"}?: "Unknown artist",
                    ContentUris.withAppendedId(collection,id),duration,c.getString(4)?: "Unknown album",c.getString(6).orEmpty(),
                    c.getLong(5),c.getInt(7),c.getInt(8),c.getString(9).orEmpty(),abs.ifBlank{rel})
            }
        }
        out
    }

    override fun onPause(){
        if(::player.isInitialized){
            val uri=player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            if(uri.isNotBlank()) getSharedPreferences("music_player_preferences",Context.MODE_PRIVATE).edit()
                .putString("last_played_uri",uri).putLong("last_played_position",player.currentPosition.coerceAtLeast(0L))
                .putString("last_played_title",player.mediaMetadata.title?.toString().orEmpty())
                .putString("last_played_artist",player.mediaMetadata.artist?.toString().orEmpty()).apply()
        }
        super.onPause()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed(){
        webView?.evaluateJavascript("window.MusicUI&&window.MusicUI.goBack&&window.MusicUI.goBack()",null)
        super.onBackPressed()
    }
    override fun onDestroy(){
        nativeBridge?.dispose(); nativeBridge=null
        webView?.apply{removeJavascriptInterface("MusicNative");stopLoading();loadUrl("about:blank");destroy()};webView=null
        notificationManager.setPlayer(null);mediaSession.release();player.release()
        super.onDestroy()
    }
}

internal suspend fun downloadPrivateMedia(context:Context,url:String):File = withContext(Dispatchers.IO) {
    require(url.startsWith("https://",true)){"URL harus HTTPS."}
    val connection=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=30000;instanceFollowRedirects=true}
    try{
        if(connection.responseCode !in 200..299) throw IllegalStateException("Download gagal (HTTP ${connection.responseCode}).")
        val dir=File(context.filesDir,"offline_media").apply{mkdirs()}
        val raw=connection.getHeaderField("Content-Disposition")?.substringAfter("filename=")?.trim('"')
            ?: URL(url).path.substringAfterLast('/').ifBlank{"audio_${System.currentTimeMillis()}.bin"}
        val safe=raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"),"_").take(100).ifBlank{"download.bin"}
        val target=File(dir,safe);val temp=File(dir,safe+".part")
        connection.inputStream.use{input->temp.outputStream().use{output->input.copyTo(output)}}
        if(!temp.renameTo(target)){temp.copyTo(target,true);temp.delete()}
        target
    }finally{connection.disconnect()}
}

fun formatTime(ms:Long):String{
    val s=(ms.coerceAtLeast(0L)/1000L).toInt()
    return String.format(Locale.US,"%d:%02d",s/60,s%60)
}
