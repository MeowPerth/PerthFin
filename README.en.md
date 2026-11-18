![PerthFin banner](images/perthfin-banner.png)

# PerthFin

[简体中文](README.md) | English

PerthFin is an independent native Android client for [Jellyfin](https://jellyfin.org),
built on top of the open-source [Findroid](https://github.com/jarnedemeulemeester/findroid)
project.

It is an independent derivative, **not a fork of the upstream repository**, and is
**not affiliated with, endorsed by, or sponsored by** the Jellyfin project or the
[Findroid](https://github.com/jarnedemeulemeester/findroid) author. "Jellyfin" is used
descriptively only.

<p align="center">
  <a href="https://github.com/MeowPerth/PerthFin/releases"><img src="https://img.shields.io/github/v/release/MeowPerth/PerthFin?style=flat-square&label=release&color=blue" alt="release"></a>
  <a href="https://github.com/MeowPerth/PerthFin/stargazers"><img src="https://img.shields.io/github/stars/MeowPerth/PerthFin?style=flat-square&label=stars&color=blue" alt="stars"></a>
  <a href="https://github.com/MeowPerth/PerthFin/issues"><img src="https://img.shields.io/github/issues/MeowPerth/PerthFin?style=flat-square&label=issues&color=yellow" alt="issues"></a>
  <a href="https://github.com/MeowPerth/PerthFin/pulls"><img src="https://img.shields.io/github/issues-pr/MeowPerth/PerthFin?style=flat-square&label=pull%20requests&color=yellow" alt="pull requests"></a>
  <a href="https://github.com/MeowPerth/PerthFin/releases"><img src="https://img.shields.io/github/downloads/MeowPerth/PerthFin/total?style=flat-square&label=downloads&color=green" alt="downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="license"></a>
</p>

- Current version: **1.1.2**
- Package: `com.wuhaohu.perthfin`
- License: GPLv3
- Base: [Findroid](https://github.com/jarnedemeulemeester/findroid) 1.2.0 (commit [`3a06420`](https://github.com/jarnedemeulemeester/findroid/commit/3a06420))

> The repository contributors list includes authors from the [Findroid](https://github.com/jarnedemeulemeester/findroid) upstream history. PerthFin changes are maintained by MeowPerth.

## Screenshots

### Phone UI (portrait)

| Home | Library (3 per row) | Items per row | Portrait playback |
|:---:|:---:|:---:|:---:|
| ![Home](images/screenshots/home-v2.jpg) | ![Library](images/screenshots/library-v2.jpg) | ![Items per row](images/screenshots/columns-v2.jpg) | ![Portrait playback](images/screenshots/player-portrait-v2.jpg) |

### Bitrate, casting and episodes

| Bitrate | Cast controls | Episodes and auto-play |
|:---:|:---:|:---:|
| ![Bitrate selection](images/screenshots/quality-v2.jpg) | ![Cast controls](images/screenshots/cast-v2.jpg) | ![Episodes and auto-play](images/screenshots/episodes-v3.jpg) |

## Features

### Library and discovery

- Native Android UI built with Kotlin and Jetpack Compose
- Movies, series, seasons and episodes
- Collections / box sets
- Multiple Jellyfin libraries
- Home sections: suggestions, continue watching, next up and latest
- Search across movies and series
- Favorites for movies, series and episodes
- Item details: overview, cast and crew, media information, chapters and trickplay
- Configurable number of items per row in libraries, search results, favorites and collections

### Playback

- Direct play or HLS transcode based on bitrate and format; presets 40/30/20/10/4/1.5 Mbps
- ExoPlayer / libmpv, picture-in-picture, playback speed, chapters, intro/outro skipping, trickplay
- Episode picker: available from both the local player and cast controls, limited to the current season and shown only for episodic content; movies and other single videos do not show it
- Portrait/landscape playback, quick rotation and a "default to landscape" setting
- Bottom bar: play/pause left, progress in the middle, time right; portrait time sits above the progress end
- Centre play button while paused, hidden during playback
- Double-tap zones split 25%/50%/25%: rewind 5 s, pause/resume, forward 15 s
- Volume, brightness, seek and zoom gestures; volume indicator syncs with the phone's media volume
- Resumes from the last playback position

### DLNA casting

- Discovers UPnP MediaRenderers on the local network and casts the current video
- Uses the current bitrate, audio track and subtitles; transcodes to H.264 + AAC stereo and burns subtitles
- Supports pause/resume, seeking, stopping and re-pushing the stream
- The cast UI supports selecting an episode from the current season and re-pushing it
- Cast controls match the player, with a warning state for errors
- The TV keeps playing after leaving the player; settings live under "Network → DLNA casting"

### Audio and subtitles

- Audio track selection
- Subtitle track selection, including "none"
- Audio and subtitle choices are remembered per item
- Falls back to the preferred audio / subtitle language when no per-item choice exists
- Selected subtitles are burned into the video stream when transcoding
- External and embedded subtitles in direct play
- Subtitle appearance follows the system caption settings

### Accounts, servers and UI

- Multiple servers and multiple users per server
- Jellyfin server discovery
- Password login and Quick Connect
- User avatars in the home header and user selection screen
- App language selection
- Light, dark, AMOLED and dynamic-color themes
- Configurable player backend and gesture behavior
- "Interface → Playback area → Notch screen mode": portrait always avoids the cutout; landscape can toggle safe-area compensation
- Preferred audio / subtitle language settings


## Codec support

Codec support depends on the Android device and the selected player backend.

### ExoPlayer (default)

- Video: H.263, H.264, H.265, VP8, VP9, AV1
- Audio: Vorbis, Opus, FLAC, ALAC, PCM, MP3, AAC, AC-3, E-AC-3, DTS, DTS-HD, TrueHD
  (partly provided by the FFmpeg extension)
- Subtitles: SRT, VTT, SSA/ASS, PGSSUB (SSA/ASS styling support is limited)

### libmpv

- Containers: MKV, MOV, MP4, AVI
- Video: H.264, H.265, H.266, VP8, VP9, AV1
- Audio: Opus, FLAC, MP3, AAC, AC-3, E-AC-3, TrueHD, DTS, DTS-HD
- Subtitles: SRT, VTT, SSA/ASS, DVDSUB
- Software decoding can be forced when hardware decoding has issues

### Transcoding

When the source video bitrate exceeds the selected preset, or a container/video/audio/
subtitle format is not supported by the player, PerthFin asks the server for an HLS
stream using H.264 video and AAC audio. The selected audio and subtitle indices are
passed to the server; selected subtitles are burned into the stream. Otherwise the
source is passed through directly.

## Requirements

- Jellyfin Server 12.0 or newer
- Android 10 (API 29) or newer
- A phone or tablet; Android TV is not supported

## Install

Download the APK for your device ABI from GitHub Releases:

- `perthfin-v1.1.2-libre-arm64-v8a.apk` — most modern phones
- `perthfin-v1.1.2-libre-armeabi-v7a.apk` — older 32-bit devices
- `perthfin-v1.1.2-libre-x86_64.apk` / `x86` — emulators and x86 devices

The APKs are signed release builds and can be installed directly. PerthFin is not
currently distributed through Google Play.

## Building

Requirements: JDK 17, Android SDK platform 37 and build-tools 37.0.0.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew :app:phone:assembleDebug
```

Debug APKs are written to `app/phone/build/outputs/apk/libre/debug/`.

For a release build:

```bash
./gradlew :app:phone:assembleRelease
```

Release APKs are written to `app/phone/build/outputs/apk/libre/release/`.
Configure your own signing key before distributing a release build.

## Versioning and roadmap

The current version is `1.1.2`. Future work focuses on cast compatibility, clearer errors and player interaction.

## License and attribution

PerthFin is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) and [NOTICE](NOTICE).

The original [Findroid](https://github.com/jarnedemeulemeester/findroid) project is
Copyright (C) Jarne Demeulemeester and contributors. PerthFin modifications are
Copyright (C) 2026 MeowPerth.

The upstream [Findroid](https://github.com/jarnedemeulemeester/findroid) project is
not responsible for this repository or its releases.
