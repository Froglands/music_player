# Music Player

**A native Android music player** built with Kotlin and Jetpack Compose, featuring a local music library, supported direct audio URL streaming, an equalizer, a visualizer, and a glassmorphism interface.

## Features

- Local music library with search and folder filters
- Audio player controls: play/pause, next, previous, and seek
- Background playback, media notifications, and lock-screen controls
- Supported direct HTTPS audio URL streaming
- Now Playing screen with metadata and artwork
- 12-band equalizer, subwoofer/deep bass, and 28-band spectrum visualizer
- Manual lyrics for each song
- Color themes and visual styles: Glass, 3D, Neon, Aurora, and Minimal
- Offline downloads only from sources that permit downloading
- YouTube playback through the official player/embed; the app does not extract or download YouTube audio
- Indonesian, English, Spanish, French, German, and Portuguese, plus automatic language mode
- Update checks through GitHub Releases

## Technology

- Kotlin 2.0
- Jetpack Compose
- AndroidX Media3 ExoPlayer
- Android MediaStore
- Android Visualizer API
- GitHub Actions

## Build APK

Automated builds run through GitHub Actions whenever code is pushed to the `main` branch.

1. Open the **Actions** tab.
2. Select the **Android APK** workflow.
3. After the build succeeds, download the `MusicPlayer-debug-apk` artifact.

The debug artifact is intended for testing. For public distribution and in-app updates, use a release APK signed with a permanent keystore.

## Releases and Updates

See [CHANGELOG.md](CHANGELOG.md) for the change history. The workflow publishes a GitHub Release when a version tag matching `v*` is pushed, provided the repository contains these signing secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Use the same keystore for every version so Android can install updates without requiring the app to be uninstalled first. Never share the keystore file or its passwords.

## Copyright and Usage

Online playback is intended for direct audio URLs or services/APIs that permit playback. Offline downloads are available only from sources that grant permission. The app does not download or redistribute copyrighted music without authorization.

## Project Status

Actively developed. See [CHANGELOG.md](CHANGELOG.md) for released features and changes.
