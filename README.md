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
gradle lint testDebugUnitTest assembleRelease
```

GitHub Actions runs the same checks and publishes a signed APK as the `latest` GitHub Release when a build on `main` succeeds.

## Install

Download `streamtv.apk` from the latest release and install it on an Android TV device. Enable installation from the browser/file manager if Android asks.

## Playlist

Open streamtv, paste your own M3U or M3U8 URL, and choose **Load channels**. Playlist URLs must be HTTP or HTTPS.

## Signing and updates

For a stable update path, configure the repository Actions secrets:
- `KEYSTORE_BASE64`: base64-encoded JKS/PKCS12 keystore
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

If these secrets are absent, CI generates a temporary signing key. That APK is installable, but a later build will use a different key; Android will reject it as an in-place update. Uninstall the old build first, or configure a permanent keystore before installing.

## Troubleshooting

- **No channels:** verify the playlist URL in a browser and ensure it returns valid M3U text.
- **Playback fails:** some providers require headers, tokens, or network access not supported by the source URL.
- **Install blocked:** allow installation from the app used to open the APK.
- **Update rejected:** install with the same signing key, or uninstall the previous app first.

This project is under active development; the repository README and release notes identify the features included in each build.
