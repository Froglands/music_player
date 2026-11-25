# Music Player

**Native Android music player** dengan koleksi offline, streaming online yang legal, dan antarmuka glassmorphism.

Music Player dibangun dengan Kotlin dan Jetpack Compose. Fokus awal proyek adalah pemutaran musik lokal yang stabil, kontrol pemutar yang nyaman, dan fondasi untuk fitur audio lanjutan.

## Fitur

- Library musik lokal dari penyimpanan perangkat
- Pemutar audio dengan play/pause, next, previous, dan seek
- Streaming dari URL audio langsung yang sah
- Antarmuka gelap bergaya glassmorphism
- Pencarian lagu lokal
- Equalizer dan audio effects sebagai tahap pengembangan berikutnya
- Background playback dan media notification sebagai tahap pengembangan berikutnya

## Teknologi

- Kotlin 2.0
- Jetpack Compose
- AndroidX Media3 ExoPlayer
- Android MediaStore
- GitHub Actions

## Build APK

Build otomatis dijalankan melalui GitHub Actions pada push ke branch `main`. Buka tab **Actions**, pilih workflow **Android APK**, lalu unduh artifact `MusicPlayer-debug-apk` jika build berhasil.

## Catatan streaming dan hak cipta

Streaming online hanya ditujukan untuk URL audio langsung atau layanan/API yang mengizinkan pemutaran. Aplikasi ini tidak mengunduh atau mendistribusikan ulang lagu berhak cipta tanpa izin.

## Status

🚧 Pengembangan aktif. Fitur ditandai selesai setelah implementasi dan pengujian build.
