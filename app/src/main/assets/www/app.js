(() => {
  const $ = (id) => document.getElementById(id);
  const views = ["library","online","offline","player","equalizer","settings"];
  function navigate(name) {
    views.forEach(v => $("view-"+v).classList.toggle("active", v === name));
    document.querySelectorAll(".tab").forEach(tab => tab.classList.toggle("active", tab.dataset.view === name));
  }
  document.querySelectorAll(".tab").forEach(tab => tab.addEventListener("click", () => navigate(tab.dataset.view)));
  $("open-settings").addEventListener("click", () => navigate("settings"));
  $("mini-player").addEventListener("click", (event) => { if (!event.target.closest("button")) navigate("player"); });
  const bands = [31,62,125,250,500,1000,2000,4000,8000,12000,14000,16000];
  $("eq-bands").innerHTML = bands.map((hz,i) => '<label class="eq-band"><output id="eq-value-'+i+'">0.0</output><input type="range" min="-15" max="15" step=".1" value="0" data-band="'+i+'"><span>'+hz+'Hz</span></label>').join("");
  $("eq-bands").querySelectorAll("input").forEach(input => input.addEventListener("input", () => $("eq-value-"+input.dataset.band).textContent = Number(input.value).toFixed(1)));
  $("bass-gain").addEventListener("input", e => $("bass-gain-value").textContent = Number(e.target.value).toFixed(1)+" dB");
  $("bass-frequency").addEventListener("input", e => $("bass-frequency-value").textContent = e.target.value+" Hz");
  $("visual-style").addEventListener("change", e => document.body.dataset.style = e.target.value);
  $("theme").addEventListener("change", e => document.body.dataset.theme = e.target.value);
  $("spectrum-style").addEventListener("change", e => document.body.dataset.spectrum = e.target.value);
  $("search").addEventListener("input", e => window.MusicNative?.searchTracks?.(e.target.value));
  $("play-url").addEventListener("click", () => window.MusicNative?.playUrl?.($("stream-url").value.trim()));
  $("request-audio")?.addEventListener("click", () => window.MusicNative?.requestAudioPermission?.());
  $("request-record")?.addEventListener("click", () => window.MusicNative?.requestRecordPermission?.());
  $("toggle-play").addEventListener("click", () => window.MusicNative?.togglePlayback?.());
  $("mini-toggle").addEventListener("click", () => window.MusicNative?.togglePlayback?.());
  $("previous").addEventListener("click", () => window.MusicNative?.previous?.());
  $("next").addEventListener("click", () => window.MusicNative?.next?.());
  $("seek").addEventListener("change", e => window.MusicNative?.seekTo?.(Number(e.target.value)));
  $("lyrics").addEventListener("change", e => window.MusicNative?.saveLyrics?.(e.target.value));
  $("track-info").addEventListener("click", () => window.MusicNative?.showTrackInfo?.());
  $("check-updates").addEventListener("click", () => window.MusicNative?.checkUpdates?.());
  $("eq-enabled").addEventListener("change", e => window.MusicNative?.setEqualizerEnabled?.(e.target.checked));
  $("bass-enabled").addEventListener("change", e => window.MusicNative?.setBassEnabled?.(e.target.checked));
  $("bass-gain").addEventListener("change", e => window.MusicNative?.setBassGain?.(Number(e.target.value)));
  $("bass-frequency").addEventListener("change", e => window.MusicNative?.setBassFrequency?.(Number(e.target.value)));
  $("lyrics-enabled").addEventListener("change", e => window.MusicNative?.setLyricsEnabled?.(e.target.checked));
  ["language","visual-style","theme","spectrum-style","audio-quality","video-quality"].forEach(id => $(id).addEventListener("change", e => window.MusicNative?.setPreference?.(id,e.target.value)));
  $("eq-bands").addEventListener("change", e => { if(e.target.matches("[data-band]")) window.MusicNative?.setEqBand?.(Number(e.target.dataset.band),Number(e.target.value)); });
  // CSS-native select sheet: avoids Android WebView's bright platform popup.
  document.querySelectorAll(".setting-row select").forEach(select => {
    const wrap = document.createElement("div");
    wrap.className = "select-wrap";
    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "select-trigger";
    trigger.textContent = select.selectedOptions[0]?.textContent || "";
    const sync = () => { trigger.textContent = select.selectedOptions[0]?.textContent || ""; };
    select.after(wrap);
    wrap.appendChild(trigger);
    select.classList.add("select-source");
    const open = () => {
      document.querySelector(".select-sheet-backdrop")?.remove();
      const backdrop = document.createElement("div");
      backdrop.className = "select-sheet-backdrop";
      const sheet = document.createElement("div");
      sheet.className = "select-sheet";
      const heading = document.createElement("div");
      heading.className = "select-sheet-title";
      heading.textContent = select.closest(".setting-row")?.querySelector("strong")?.textContent || "Pilih";
      sheet.appendChild(heading);
      [...select.options].forEach(option => {
        const item = document.createElement("button");
        item.type = "button";
        item.className = "select-option" + (option.value === select.value ? " selected" : "");
        item.textContent = option.textContent;
        const mark = document.createElement("span");
        mark.textContent = option.value === select.value ? "●" : "○";
        item.appendChild(mark);
        item.addEventListener("click", () => {
          select.value = option.value;
          select.dispatchEvent(new Event("change", { bubbles: true }));
          sync();
          backdrop.remove();
        });
        sheet.appendChild(item);
      });
      backdrop.appendChild(sheet);
      backdrop.addEventListener("click", e => { if (e.target === backdrop) backdrop.remove(); });
      document.body.appendChild(backdrop);
    };
    trigger.addEventListener("click", open);
    select.addEventListener("change", sync);
    wrap.appendChild(select);
  });
  window.MusicUI = {
    navigate,
    setTracks(items) {
      $("track-count").textContent = items.length+" TRACKS";
      $("track-list").innerHTML = items.length ? items.map(t => '<button class="setting-row track-item" data-index="'+t.index+'"><span><strong>'+escapeHtml(t.title)+'</strong><small>'+escapeHtml(t.artist)+'</small></span><span class="muted">▶</span></button>').join("") : '<div class="empty-state"><strong>Belum ada lagu ditemukan</strong><p>Periksa izin audio atau ubah pencarian.</p><button class="outline-button" id="request-audio">Izinkan akses audio</button></div>';
      $("track-list").querySelectorAll("[data-index]").forEach(button => button.addEventListener("click", () => window.MusicNative?.playTrack?.(Number(button.dataset.index))));
      const paths=[...new Set(items.map(t => (t.path||"").split("/").slice(0,-1).join("/") || "Lokasi tidak diketahui"))];
      $("folder-filters").innerHTML='<button class="chip selected" data-folder="all">Semua lokasi</button>'+paths.map(p=>'<button class="chip" data-folder="'+escapeHtml(p)+'">'+escapeHtml(p.split("/").pop()||p)+'</button>').join("");
      $("folder-filters").querySelectorAll("[data-folder]").forEach(b=>b.addEventListener("click",()=>{document.querySelectorAll("#folder-filters .chip").forEach(x=>x.classList.toggle("selected",x===b));const q=$("search").value;window.MusicNative?.searchTracks?.(q,b.dataset.folder==="all"?"":b.dataset.folder)}));
    },
    setNowPlaying(track) {
      $("now-title").textContent = track.title || "Unknown title";
      $("now-artist").textContent = track.artist || "Unknown artist";
      $("mini-title").textContent = track.title || "Unknown title";
      $("mini-artist").textContent = track.artist || "";
      $("toggle-play").textContent = track.playing ? "Ⅱ" : "▶";
      $("mini-toggle").textContent = track.playing ? "Ⅱ" : "▶";
    },
    setProgress(position,duration) {
      $("position").textContent = formatTime(position);
      $("duration").textContent = formatTime(duration);
      $("seek").max = Math.max(1,duration);
      $("seek").value = Math.min(position,duration);
    },
    setSpectrum(levels) {
      $("spectrum").innerHTML = levels.map(level => '<i style="height:'+Math.max(3,Math.round(level*100))+'%"></i>').join("");
    },
    setOfflineFiles(items) {
      $("offline-list").innerHTML = items.length ? items.map((f,i) => '<button class="setting-row" data-file="'+i+'"><span><strong>'+escapeHtml(f.name)+'</strong><small>Media tersimpan</small></span><span class="muted">▶</span></button>').join("") : '<div class="empty-state"><strong>Belum ada unduhan</strong><p>Media yang diunduh akan tampil di sini.</p></div>';
      $("offline-list").querySelectorAll("[data-file]").forEach(b => b.addEventListener("click", () => window.MusicNative?.playOffline?.(Number(b.dataset.file))));
      $("offline-list").querySelectorAll("[data-delete]").forEach(b => b.addEventListener("click", e => {e.stopPropagation();window.MusicNative?.deleteOffline?.(Number(b.dataset.delete));}));
    },
    setStatus(id,value) { if($(id)) $(id).textContent = value; }
  };
  function escapeHtml(value) { return String(value ?? "").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c])); }
  function formatTime(ms) { const s=Math.floor(Math.max(0,ms)/1000); return Math.floor(s/60)+":"+String(s%60).padStart(2,"0"); }
  window.MusicUI.setSpectrum(Array(28).fill(.03));
  window.addEventListener("load",()=>window.MusicNative?.scanTracks?.());
  setInterval(()=>window.MusicNative?.tick?.(),500);
})();