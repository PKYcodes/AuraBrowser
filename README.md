# Aura Browser

A privacy-first Android browser built on [Mozilla GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/),
with a built-in local music and video player. Kotlin, Jetpack Compose, Hilt, Room, DataStore and
[Media3](https://developer.android.com/media/media3).

> **Status:** pre-release. The code has been reviewed and refactored thoroughly but has **not yet been compiled
> or tested on a device** in the environment where it was last edited. Run the build below first and expect to
> fix a few compile errors — see [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md).

## Features

| Area | What you get |
|---|---|
| **Browsing** | Tabs (restored after restart), private tabs that are never written to disk, desktop-site mode, find in page, long-press link/image menu, `target=_blank` / `window.open` support, JavaScript dialogs, file uploads, `<select>` pickers, HTTP auth, crash recovery |
| **Privacy** | HTTPS-only mode, tracking-protection presets, cookie policy, per-site permission prompts (location, notifications, camera, microphone) that you can review and revoke, no analytics, optional "clear on exit" |
| **Downloads** | Real streaming downloads with progress and cancel, SHA-256 checksum, warning before opening executable files, *Save page as PDF* |
| **YouTube** | Share-sheet and deep-link handling, background playback with a notification, optional "hide Shorts" (built-in WebExtension) |
| **Music** | `MediaStore` and folder (SAF) library, background playback service, equalizer and bass boost that persist |
| **Video** | MP4/MKV/WebM/HLS/DASH, subtitles (SRT/VTT/SSA/TTML), speed control, double-tap seek, resume position, fullscreen and Picture-in-Picture |
| **Extensions** | Built-in media extension; install from addons.mozilla.org (see limitations) |

## Build

Requirements: JDK 17, Android SDK platform 35 (`ANDROID_HOME` set), Gradle 8.10+.

```bash
# One-time: generate the Gradle wrapper (the wrapper JAR is not checked in)
gradle wrapper --gradle-version 8.10.2

./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug and release builds ship `arm64-v8a` only (GeckoView is ~70 MB per ABI). For a PC emulator add `-PwithX86=true` to the debug build.

### Release signing

Create a git-ignored `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew :app:assembleRelease`. Without that file the release APK is unsigned.
Pushing a tag such as `v1.0.0` makes CI build and publish a release (signed when the
`ANDROID_KEYSTORE_*` repository secrets are set).

## Project layout

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for module responsibilities, threading and security decisions.

## Contributing

- Format with the settings in `.editorconfig`; keep modules free of dependency cycles (features never depend on each other except through `:feature:browser`).
- Add unit tests for pure logic (URL handling, validators, ratio maths); CI runs `testDebugUnitTest` on every push and pull request.
