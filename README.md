# Aura Browser — All-in-One GeckoView Browser & Media Studio for Android

Aura Browser (`com.aura.browser`) is a modular, privacy-first Android browser powered by [Mozilla GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/), [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3), native [Android Picture-in-Picture](https://developer.android.com/guide/topics/ui/picture-in-picture), and a Custom Glassmorphism [Jetpack Compose](https://developer.android.com/develop/ui/compose) interface.

## Key Capabilities
1. **Mozilla GeckoView Browser (`:feature:browser`, `:feature:tabs`)**: Multi-tab browsing, private browsing sessions, desktop mode, offline HTML fallback pages, and tracker blocking.
2. **YouTube Web & Share Integration (`:feature:youtube`)**: Opens `youtube.com`, `youtu.be`, `youtube.com/shorts`, and `music.youtube.com` links shared from other Android apps, supports standard Google OAuth2 sign-in, and keeps web media active in the background via `suspendMediaWhenInactive(false)` and the built-in `aura-media-enhancer@aura.browser` WebExtension.
3. **Media3 Local Music Player (`:feature:music`, `:core:media-common`)**: Scans local audio via `MediaStore` and Storage Access Framework (SAF), plays in the background via `AuraMediaPlaybackService` (`MediaSessionService`), handles audio focus and headphone disconnects, and provides hardware `Equalizer` and `BassBoost` controls.
4. **Media3 Local Video Cinema (`:feature:video`, `:feature:pip`)**: Plays MP4, MKV, WebM, DASH, and HLS streams with double-tap seek gestures, playback speed control (`0.5x`–`2.0x`), external `.vtt`/`.srt` subtitle loading, automatic resume checkpoints, and native Android Picture-in-Picture (`PictureInPictureParams`).
5. **Firefox WebExtensions Manager (`:feature:extensions`)**: Wraps GeckoView's [WebExtensionController](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html) to manage built-in extensions and install signed Android-compatible `.xpi` add-ons (such as uBlock Origin, Dark Reader, and Privacy Badger) from `addons.mozilla.org`.

## Build Instructions

### Option A: GitHub Actions CI/CD (Recommended Primary Build Environment)
1. Push this repository to GitHub (`main`, `master`, or `develop` branch).
2. GitHub Actions automatically triggers `.github/workflows/android-build.yml`, which provisions **Temurin JDK 17**, **Android SDK Platform 35**, and **Gradle 8.10.2**.
3. The workflow runs `gradle testDebugUnitTest --stacktrace` and `gradle :app:assembleDebug --stacktrace`, then uploads `app/build/outputs/apk/debug/app-debug.apk` as the `aura-browser-debug-apk` artifact.

### Option B: Local Build (Android Studio / Command Line / Termux with Android SDK)
```bash
# Ensure JDK 17 and Android SDK (API 35) are configured via ANDROID_HOME
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug

# Output Debug APK location:
ls -lh app/build/outputs/apk/debug/app-debug.apk

# Install on connected Android device (API 26+):
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
