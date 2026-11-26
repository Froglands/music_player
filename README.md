# Music Player

A native Android music player built with Kotlin and Jetpack Compose.

## Features

- Local music library with search and folder filters
- Background playback and media controls
- Streaming from supported direct audio URLs
- Now Playing with track metadata and artwork
- 12-band equalizer and 28-band spectrum visualizer
- Manual lyrics, themes, and visual styles
- YouTube playback through the official embedded player
- GitHub release update checks

## Tech Stack

Kotlin · Jetpack Compose · AndroidX Media3 · Android MediaStore

## Build

Pushes to `main` trigger the **Android APK** workflow in GitHub Actions. Download the `MusicPlayer-debug-apk` artifact after a successful run.

For signed releases, push a version tag (for example, `v1.0.1`). Configure these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the same signing keystore for future app updates. Never share the keystore or its passwords.

## License

See the repository license, if provided. Use audio sources in accordance with their terms and copyright permissions.
