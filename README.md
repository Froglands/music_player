# Music Player

**Native Android music player** berbasis Kotlin dan Jetpack Compose, dengan koleksi musik lokal, streaming URL audio langsung yang sah, equalizer, visualizer, dan antarmuka glassmorphism.

## Fitur

- Library musik lokal, pencarian, dan filter folder
- Pemutar audio dengan kontrol play/pause, next, previous, dan seek
- Background playback, media notification, dan kontrol lockscreen
- Streaming URL audio HTTPS langsung yang sah
- Now Playing dengan metadata dan artwork
- Equalizer 12-band, subwoofer/deep bass, dan spectrum visualizer 28-band
- Lirik manual per lagu
- Tema warna dan gaya visual Glass, 3D, Neon, Aurora, dan Minimal
- Unduhan offline hanya untuk sumber yang mengizinkan pengunduhan
- YouTube melalui pemutar/embed resmi; aplikasi tidak mengekstrak atau mengunduh audio YouTube
- Bahasa Indonesia, English, Español, Français, Deutsch, Português, serta mode otomatis
- Pemeriksaan pembaruan dari GitHub Releases

## Teknologi

- Kotlin 2.0
- Jetpack Compose
- AndroidX Media3 ExoPlayer
- Android MediaStore
- Android Visualizer API
- GitHub Actions

## Build APK

Build otomatis dijalankan melalui GitHub Actions pada setiap push ke branch `main`.

1. Buka tab **Actions**.
2. Pilih workflow **Android APK**.
3. Setelah build berhasil, unduh artifact `MusicPlayer-debug-apk`.

Artifact debug cocok untuk pengujian. Untuk rilis publik dan pembaruan dalam aplikasi, gunakan APK release yang ditandatangani dengan keystore permanen.

## Rilis dan pembaruan

Riwayat perubahan tersedia di [CHANGELOG.md](CHANGELOG.md). Workflow menerbitkan GitHub Release saat tag versi `v*` didorong, setelah repository memiliki secrets signing berikut:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Gunakan keystore yang sama untuk semua versi agar Android dapat memasang pembaruan tanpa menghapus aplikasi sebelumnya. Jangan membagikan file keystore atau kata sandinya.

## Catatan hak cipta

Streaming online hanya ditujukan untuk URL audio langsung atau layanan/API yang mengizinkan pemutaran. Pengunduhan hanya tersedia untuk sumber yang memberikan izin. Aplikasi tidak mengunduh atau mendistribusikan ulang lagu berhak cipta tanpa izin.

## Status

Pengembangan aktif. Lihat [CHANGELOG.md](CHANGELOG.md) untuk daftar fitur target rilis.
