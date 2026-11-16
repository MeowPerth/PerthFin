![PerthFin banner](images/perthfin-banner.png)

# PerthFin

PerthFin is an unofficial, rebranded fork of [Findroid](https://github.com/jarnedemeulemeester/findroid),
a native Android client for [Jellyfin](https://jellyfin.org).

It is **not affiliated with, endorsed by, or sponsored by** the Jellyfin project or the Findroid author.
"Jellyfin" is used descriptively only.

## Status

This branch is a rebrand and feature fork. The application id and branding are new, the download and offline features have been removed, and basic
server-side transcoding with a quality preset is available. DLNA is planned.

## Features

- Completely native Android interface (Kotlin, Jetpack Compose)
- Browse and play movies, series, seasons and episodes
- Direct play by default; server-side transcoding with 20/10/4/1.5 Mbps presets
- In-player quality switch (restarts the stream at the current position)
- ExoPlayer and mpv playback backends
- Picture-in-picture, chapters, trickplay, media segment skipping
- Multiple servers and users, Quick Connect login
- Android TV module (experimental, not released)

See the [Findroid README](https://github.com/jarnedemeulemeester/findroid#features) for the
inherited feature matrix and codec support.

## Requirements

- Jellyfin Server 12.0 or newer
- Android 10 (API 29) or newer

## Building

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platform 37, build-tools 37.0.0
./gradlew :app:phone:assembleDebug
```

APKs are written to `app/phone/build/outputs/apk/libre/debug/`.

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

The original Findroid project is Copyright (C) Jarne Demeulemeester and contributors.
PerthFin modifications are Copyright (C) 2026 MeowPerth.
