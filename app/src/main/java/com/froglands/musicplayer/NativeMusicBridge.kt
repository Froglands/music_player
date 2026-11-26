package com.froglands.musicplayer

import android.content.ContentUris
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

class NativeMusicBridge(private val activity: MainActivity, private val web: WebView, private val player: ExoPlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = activity.getSharedPreferences("music_player_preferences", Context.MODE_PRIVATE)
    private var tracks = emptyList<Track>()
    private var files = emptyList<File>()
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var visualizer: Visualizer? = null
    private var levels = MutableList(12) { 0f }
    private var eqEnabled = false
    private var bassEnabled = false
    private var bassGain = 0f
    private var bassFrequency = 80
    private var lyrics = ""
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = pushState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushState()
        override fun onPlaybackStateChanged(playbackState: Int) = pushState()
        override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)
    }

    init {
        player.addListener(listener)
        eqEnabled = prefs.getBoolean("eq_enabled", false)
        bassEnabled = prefs.getBoolean("sub_enabled", false)
        bassGain = prefs.getFloat("sub_gain", 0f)
        bassFrequency = prefs.getInt("sub_frequency", 80)
        for (i in 0..11) levels[i] = prefs.getFloat("eq_" + i, 0f)
        refreshFiles()
    }

    @JavascriptInterface fun requestAudioPermission() { activity.runOnUiThread { activity.requestAudioPermissionFromWeb() } }
    @JavascriptInterface fun requestRecordPermission() { activity.runOnUiThread { activity.requestRecordPermissionFromWeb() } }
    @JavascriptInterface fun tick() { pushState() }
    @JavascriptInterface fun scanTracks() {
        scope.launch {
            tracks = withContext(Dispatchers.IO) { readTracks() }
            sendTracks("")
        }
    }
    @JavascriptInterface fun searchTracks(q: String, folder: String) = sendTracks(q, folder)
    @JavascriptInterface fun playTrack(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val items = tracks.map { t -> MediaItem.Builder().setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build() }
        player.setMediaItems(items, tracks.indexOf(track), 0L)
        player.prepare()
        player.play()
        pushState()
    }
    @JavascriptInterface fun togglePlayback() { activity.runOnUiThread { if (player.isPlaying) player.pause() else player.play(); pushState() } }
    @JavascriptInterface fun next() { activity.runOnUiThread { if (player.hasNextMediaItem()) player.seekToNextMediaItem() } }
    @JavascriptInterface fun previous() { activity.runOnUiThread { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() } }
    @JavascriptInterface fun seekTo(ms: Int) { activity.runOnUiThread { player.seekTo(ms.toLong().coerceAtLeast(0L)) } }
    @JavascriptInterface fun playUrl(url: String) {
        if (!url.startsWith("https://")) { showStatus("URL harus HTTPS."); return }
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(url.substringAfterLast('/').substringBefore('?')).build()).build())
        player.prepare(); player.play()
    }
    @JavascriptInterface fun playOffline(index: Int) {
        val f = files.getOrNull(index) ?: return
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f))); player.prepare(); player.play()
    }
    @JavascriptInterface fun deleteOffline(index: Int) { files.getOrNull(index)?.delete(); refreshFiles() }
    @JavascriptInterface fun setEqBand(index: Int, value: Double) {
        if (index !in 0..11) return
        levels[index] = value.toFloat().coerceIn(-15f, 15f)
        prefs.edit().putFloat("eq_" + index, levels[index]).apply()
        applyEffects()
    }
    @JavascriptInterface fun setEqualizerEnabled(v: Boolean) { eqEnabled=v; prefs.edit().putBoolean("eq_enabled",v).apply(); applyEffects() }
    @JavascriptInterface fun setBassEnabled(v: Boolean) { bassEnabled=v; prefs.edit().putBoolean("sub_enabled",v).apply(); applyEffects() }
    @JavascriptInterface fun setBassGain(v: Double) { bassGain=v.toFloat().coerceIn(0f,6f); prefs.edit().putFloat("sub_gain",bassGain).apply(); applyEffects() }
    @JavascriptInterface fun setBassFrequency(v: Int) { bassFrequency=v.coerceIn(50,160); prefs.edit().putInt("sub_frequency",bassFrequency).apply(); applyEffects() }
    @JavascriptInterface fun saveLyrics(value: String) {
        lyrics=value.take(20000)
        val uri=player.currentMediaItem?.localConfiguration?.uri?.toString() ?: "empty"
        prefs.edit().putString("lyrics_" + uri,lyrics).apply()
    }
    @JavascriptInterface fun setLyricsEnabled(v: Boolean) { prefs.edit().putBoolean("lyrics_enabled",v).apply(); showStatus(if(v) "Lirik aktif" else "Lirik nonaktif") }
    @JavascriptInterface fun setPreference(key: String, value: String) {
        val map=mapOf("language" to "app_language","visual-style" to "visual_style","theme" to "app_theme","spectrum-style" to "spectrum_style","audio-quality" to "download_audio_quality","video-quality" to "download_video_quality")
        map[key]?.let { prefs.edit().putString(it,value).apply() }
    }
    @JavascriptInterface fun showTrackInfo() {
        val m=player.mediaMetadata
        showStatus(listOfNotNull(m.title?.toString(),m.artist?.toString(),m.albumTitle?.toString()).joinToString(" • ").ifBlank{"Tidak ada metadata"})
    }
    @JavascriptInterface fun downloadMedia(url: String) {
        if (!url.startsWith("https://")) { showStatus("URL harus HTTPS."); return }
        scope.launch {
            showStatus("Mengunduh media…")
            try {
                val f=downloadPrivateMedia(activity,url)
                showStatus("Tersimpan: " + f.name)
                refreshFiles()
            } catch(e: Exception) { showStatus(e.message ?: "Download gagal") }
        }
    }
    @JavascriptInterface fun checkUpdates() {
        scope.launch {
            try {
                val update=fetchLatestAppUpdate(BuildConfig.VERSION_NAME)
                if(update==null) showStatus("Aplikasi sudah versi terbaru.")
                else activity.runOnUiThread {
                    android.app.AlertDialog.Builder(activity).setTitle("Pembaruan v" + update.version)
                        .setMessage(update.notes.take(1200)).setNegativeButton("Nanti",null)
                        .setPositiveButton("Unduh") { _,_ -> downloadAppUpdate(activity,update) }.show()
                }
            } catch(e:Exception) { showStatus(e.message ?: "Gagal memeriksa pembaruan") }
        }
    }
    fun pageReady() {
        scanTracks()
        refreshFiles()
        val uri=player.currentMediaItem?.localConfiguration?.uri?.toString() ?: "empty"
        lyrics=prefs.getString("lyrics_" + uri,"") ?: ""
        val values=JSONObject()
            .put("title",player.mediaMetadata.title?.toString() ?: "Pilih lagu")
            .put("artist",player.mediaMetadata.artist?.toString() ?: "")
            .put("playing",player.isPlaying)
        js("window.MusicUI&&window.MusicUI.setNowPlaying(" + values.toString() + ");")
        js("document.getElementById('lyrics').value=" + JSONObject.quote(lyrics) + ";")
        js("document.getElementById('app-version').textContent='Versi " + BuildConfig.VERSION_NAME + "';")
        val style=prefs.getString("visual_style","GLASS")!!.lowercase()
        val theme=prefs.getString("app_theme","Midnight")!!
        js("document.body.dataset.style=" + JSONObject.quote(style) + ";")
        js("document.body.dataset.theme=" + JSONObject.quote(theme) + ";")
        js("document.getElementById('theme').value=" + JSONObject.quote(theme) + ";")
        js("document.getElementById('eq-enabled').checked=" + eqEnabled + ";document.getElementById('bass-enabled').checked=" + bassEnabled + ";")
    }
    fun dispose() { player.removeListener(listener); releaseEffects(); scope.cancel() }
    fun tick() = pushState()

    private fun readTracks(): List<Track> {
        val out=mutableListOf<Track>()
        val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.ALBUM_ARTIST,MediaStore.Audio.Media.YEAR,MediaStore.Audio.Media.TRACK,MediaStore.Audio.Media.COMPOSER,MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.DATA)
        activity.contentResolver.query(collection,projection,MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " > 0",null,MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
            while(c.moveToNext()) {
                fun str(i:Int, fallback:String="")=c.getString(i) ?: fallback
                val id=c.getLong(0)
                out.add(Track(id,str(1,"Unknown title"),str(2,"Unknown artist").takeUnless{it=="<unknown>"} ?: "Unknown artist",ContentUris.withAppendedId(collection,id),c.getLong(3),str(4,"Unknown album"),str(6),c.getLong(5),c.getInt(7),c.getInt(8),str(9),str(11).ifBlank{str(10)}))
            }
        }
        return out
    }
    private fun sendTracks(filter:String, folder:String = "") {
        val arr=JSONArray()
        tracks.forEachIndexed { i,t -> if((t.title.contains(filter,true)||t.artist.contains(filter,true)) && (folder.isBlank() || t.storagePath.substringBeforeLast("/",t.storagePath).contains(folder,true))) arr.put(JSONObject().put("index",i).put("title",t.title).put("artist",t.artist).put("duration",t.duration).put("path",t.storagePath)) }
        js("window.MusicUI&&window.MusicUI.setTracks(" + arr.toString() + ");")
    }
    private fun pushState() {
        val m=player.mediaMetadata
        val obj=JSONObject().put("title",m.title?.toString() ?: "Pilih lagu").put("artist",m.artist?.toString() ?: "").put("playing",player.isPlaying)
        js("window.MusicUI&&window.MusicUI.setNowPlaying(" + obj.toString() + ");window.MusicUI&&window.MusicUI.setProgress(" + player.currentPosition.coerceAtLeast(0) + "," + player.duration.coerceAtLeast(0) + ");")
    }
    private fun refreshFiles() {
        val dir=File(activity.filesDir,"offline_media")
        files=dir.listFiles()?.filter{it.isFile && !it.name.endsWith(".part")}?.sortedByDescending{it.lastModified()} ?: emptyList()
        val arr=JSONArray()
        files.forEachIndexed { i,f -> arr.put(JSONObject().put("index",i).put("name",f.name).put("size",f.length())) }
        js("window.MusicUI&&window.MusicUI.setOfflineFiles(" + arr.toString() + ");")
    }
    private fun attachEffects(id:Int) {
        releaseEffects()
        if(id<=0)return
        try {
            eq=Equalizer(0,id); bass=BassBoost(0,id)
            visualizer=Visualizer(id).apply {
                captureSize=Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object:Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v:Visualizer?,data:ByteArray?,rate:Int) {}
                    override fun onFftDataCapture(v:Visualizer?,data:ByteArray?,rate:Int) {
                        if(data==null || data.size<16)return
                        val count=data.size/2
                        val a=JSONArray()
                        for(i in 0 until 28) {
                            val bin=(1+i*count/28).coerceIn(1,count-1)
                            a.put((ln(1.0+hypot(data[bin*2].toDouble(),data[bin*2+1].toDouble()))/ln(101.0)).coerceIn(.025,1.0))
                        }
                        js("window.MusicUI&&window.MusicUI.setSpectrum(" + a.toString() + ");")
                    }
                },Visualizer.getMaxCaptureRate(),false,true)
                enabled=true
            }
            applyEffects()
            showStatus("EQ dan Visualizer terhubung.")
        } catch(e:Exception) { showStatus("Efek audio tidak didukung: " + e.javaClass.simpleName) }
    }
    private fun applyEffects() {
        try {
            val e=eq ?: return
            val target=floatArrayOf(31f,63f,125f,250f,500f,1000f,2000f,4000f,8000f,10000f,12000f,16000f)
            val range=e.bandLevelRange
            for(b in 0 until e.numberOfBands.toInt()) {
                val f=e.getCenterFreq(b.toShort())/1000f
                val hi=target.indexOfFirst{it>=f}.let{if(it<0)11 else it}; val lo=(hi-1).coerceAtLeast(0)
                val part=if(hi==lo)0f else (ln(f/target[lo])/ln(target[hi]/target[lo])).coerceIn(0f,1f)
                val gain=if(eqEnabled) levels[lo]+(levels[hi]-levels[lo])*part else 0f
                val sub=if(bassEnabled && f<=bassFrequency) bassGain else 0f
                e.setBandLevel(b.toShort(),((gain+sub)*100).toInt().coerceIn(range[0].toInt(),range[1].toInt()).toShort())
            }
            e.enabled=eqEnabled || (bassEnabled && bassGain>0)
            bass?.let { it.setStrength(if(bassEnabled)(bassGain/6f*500).toInt().toShort() else 0); it.enabled=bassEnabled && bassGain>0 }
        } catch(e:Exception) { showStatus("Gagal menerapkan EQ: " + e.javaClass.simpleName) }
    }
    private fun releaseEffects() {
        runCatching{visualizer?.enabled=false}; runCatching{visualizer?.release()}; visualizer=null
        runCatching{eq?.release()}; runCatching{bass?.release()}; eq=null; bass=null
    }
    private fun showStatus(s:String) = js("window.MusicUI&&window.MusicUI.setStatus('eq-status'," + JSONObject.quote(s) + ");")
    private fun js(s:String) { activity.runOnUiThread { if(!activity.isFinishing) web.evaluateJavascript(s,null) } }
}
