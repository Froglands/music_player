# Changelog

## [1.0.0] - 2026-11-26

### Added
- Local music library with search and folder filters
- Background playback and media notifications
- Direct audio URL streaming
- Now Playing screen with metadata and artwork
- 12-band equalizer and 28-band spectrum visualizer
- Manual lyrics, themes, and visual styles
- Official embedded YouTube playback
- GitHub release update checks
- Last played track and position restoration

### Improved
- Glassmorphism interface and player controls
- Navigation and playback experience



## [1.0.1] - Unreleased

### Added
- Added a searchable song list in Settings > Lyrics.
- Added per-track lyric editing with an explicit Save button.
- Added timestamp marking during playback to synchronize lyric lines.
- Added an adjustable lyric transition speed.

### Changed
- Simplified lyric management: select a song to open its editor; saving closes the editor.
- Hid the mini-player while browsing Settings to keep the settings interface clear.
- Updated the Now Playing layout: moved track information beside the album artwork and removed the lyrics panel heading.
- Displayed lyrics directly with a softer, semi-transparent text style.

### Fixed
- Fixed lyric editor state interfering with the lyrics shown for the currently playing track.
- Fixed lyric lookup to use the active playback item and refresh Now Playing after saving.
- Fixed timed lyrics so the first line remains visible when a track restarts at 00:00.
- Improved LRC timestamp parsing, including fractional seconds and multiple timestamps on one line.

### Build
- Verified the Debug APK workflow successfully on commit `117595dd2edb91b657a0568539d5a7f4fb525a87`.
