# streamtv

A personal Android TV IPTV player. No channels, playlists, logos, or third-party content are bundled. Add your own M3U/M3U8 playlist; an XMLTV guide is optional.

## Current implementation

- Kotlin, Jetpack Compose, Android TV launcher support, landscape UI
- M3U parser with quoted/unquoted metadata attributes and duplicate filtering
- Playlist loading over HTTP(S) off the main thread
- Media3 playback for HLS, DASH, and progressive media
- Channel browsing, categories, and favorites persisted across restarts
- Room-backed channel cache and DataStore-backed playlist/favorites preferences
- Optional XMLTV/XMLTV.GZ loading with a rolling now/next programme window
- Room channel cache and DataStore preferences

## Build

Requires JDK 17, Android SDK platform 34, and Android build tools. From the repository root:

```bash
./gradlew lint testDebugUnitTest assembleRelease
```

GitHub Actions runs the same checks and publishes a signed APK as the `latest` GitHub Release (`stv.apk`) when a build on `main` succeeds.

## Install

Download [stv.apk](https://github.com/walidplayx-debug/streamtv/releases/latest/download/stv.apk) from the latest release and install it on an Android TV device. Enable installation from the browser/file manager if Android asks.

## Playlist

Open streamtv, paste your own M3U or M3U8 URL, and choose **Load channels**. Playlist URLs must be HTTP or HTTPS.

## Signing and updates

Every build is signed with the same key, so a new APK installs over the previous one. By default that is the public key in `keystore/` (see `keystore/README.md`). To sign with a private key instead, configure the repository Actions secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Switching keys requires uninstalling the app once.

Builds of branches other than `main` are published as the `preview` pre-release: [stv-preview.apk](https://github.com/walidplayx-debug/streamtv/releases/download/preview/stv-preview.apk).

## Troubleshooting

- **No channels:** verify the playlist URL in a browser and ensure it returns valid M3U text.
- **Playback fails:** some providers require headers, tokens, or network access not supported by the source URL.
- **Install blocked:** allow installation from the app used to open the APK.
- **Update rejected:** the installed app was signed with a different key; uninstall it once, then install again.

This project is under active development; the repository README and release notes identify the features included in each build.
