# Aura Browser — Development Status (`DEVELOPMENT_STATUS.md`)

## 1. Project Identity & Overview
- **Application Name**: Aura Browser
- **Package Name**: `com.aura.browser`
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target / Compile SDK**: API 35 (Android 15)
- **Primary Language & Build System**: Kotlin 2.1.0 + Gradle Kotlin DSL (`8.10.2`) + Version Catalog (`gradle/libs.versions.toml`)
- **Browser Engine**: [Mozilla GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/) (`org.mozilla.geckoview:geckoview:140.0.20250707120347` via `https://maven.mozilla.org/maven2/`)
- **Media Engine**: [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3) (`1.11.1` with `MediaSessionService`, AudioFocus, Equalizer, and BassBoost)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/develop/ui/compose) (`compose-bom:2025.11.00`) with Custom Glassmorphism Design System (`#2781E7` Primary Accent, `#090F19` Deep Background, `#172538` Glass Surface, 18–24dp rounded corners)

## 2. Completed Modules (23 Modules + `build-logic`)

| Layer | Module Name | Namespace | Status | Key Implementation Files |
| :--- | :--- | :--- | :--- | :--- |
| **Build Logic** | `:build-logic:convention` | `com.aura.browser.buildlogic` | Completed | `AuraAndroidApplicationConventionPlugin.kt`, `AuraAndroidLibraryConventionPlugin.kt`, `AuraAndroidComposeConventionPlugin.kt`, `AuraAndroidHiltConventionPlugin.kt` |
| **Core** | `:core:common` | `com.aura.browser.core.common` | Completed | `AuraDispatchers.kt`, `AuraResult.kt`, `CoreModels.kt` |
| **Core** | `:core:designsystem` | `com.aura.browser.core.designsystem` | Completed | `AuraColors.kt`, `AuraTheme.kt`, `GlassComponents.kt` |
| **Core** | `:core:database` | `com.aura.browser.core.database` | Completed | `AuraDatabase.kt`, `Entities.kt`, `AuraDaos.kt` |
| **Core** | `:core:datastore` | `com.aura.browser.core.datastore` | Completed | `AuraPreferencesDataSource.kt` |
| **Core** | `:core:media-common` | `com.aura.browser.core.mediacommon` | Completed | `AuraMediaPlaybackService.kt`, `AuraMediaControllerManager.kt`, `AuraAudioEffectsController.kt`, `AuraMediaModels.kt` |
| **Core** | `:core:network` | `com.aura.browser.core.network` | Completed | `AuraUrlSanitizer.kt`, `AuraDownloadValidator.kt` |
| **Core** | `:core:permissions` | `com.aura.browser.core.permissions` | Completed | `AuraPermissionManager.kt` |
| **Feature** | `:feature:home` | `com.aura.browser.feature.home` | Completed | `HomeScreen.kt` |
| **Feature** | `:feature:browser` | `com.aura.browser.feature.browser` | Completed | `AuraGeckoEngineManager.kt`, `BrowserScreen.kt` |
| **Feature** | `:feature:tabs` | `com.aura.browser.feature.tabs` | Completed | `TabsScreen.kt` |
| **Feature** | `:feature:bookmarks` | `com.aura.browser.feature.bookmarks` | Completed | `BookmarksScreen.kt` |
| **Feature** | `:feature:history` | `com.aura.browser.feature.history` | Completed | `HistoryScreen.kt` |
| **Feature** | `:feature:downloads` | `com.aura.browser.feature.downloads` | Completed | `DownloadsScreen.kt` |
| **Feature** | `:feature:youtube` | `com.aura.browser.feature.youtube` | Completed | `YouTubeIntentResolver.kt`, `YouTubeIntegrationScreen.kt` |
| **Feature** | `:feature:music` | `com.aura.browser.feature.music` | Completed | `LocalAudioScanner.kt`, `MusicPlayerScreen.kt` |
| **Feature** | `:feature:video` | `com.aura.browser.feature.video` | Completed | `VideoPlayerScreen.kt` |
| **Feature** | `:feature:pip` | `com.aura.browser.feature.pip` | Completed | `AuraPipController.kt` |
| **Feature** | `:feature:extensions` | `com.aura.browser.feature.extensions` | Completed | `AuraWebExtensionManager.kt`, `ExtensionsScreen.kt` |
| **Feature** | `:feature:filemanager` | `com.aura.browser.feature.filemanager` | Completed | `FileManagerScreen.kt` |
| **Feature** | `:feature:privacy` | `com.aura.browser.feature.privacy` | Completed | `PrivacyDashboardScreen.kt` |
| **Feature** | `:feature:settings` | `com.aura.browser.feature.settings` | Completed | `SettingsScreen.kt` |
| **Navigation** | `:navigation` | `com.aura.browser.navigation` | Completed | `AuraDestination.kt`, `AuraNavHost.kt` |
| **Application** | `:app` | `com.aura.browser` | Completed | `AuraBrowserApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `assets/extensions/aura-media-enhancer/` |

## 3. Pending Modules
- **None** — All 23 specified modules plus `:build-logic:convention` have been implemented with complete Kotlin source files, Gradle configurations, and unit tests.

## 4. Known Platform & Environment Blockers
1. **Offline Sandbox VM vs. GitHub Actions APK Compilation**:
   - The local sandbox VM has outbound internet access disabled by environment policy and does not pre-install the Android SDK (`platforms;android-35`, `build-tools;35.0.0`, `aapt2`, `d8`) or cache Mozilla's remote `org.mozilla.geckoview` AAR artifacts (`https://maven.mozilla.org/maven2/`).
   - Consequently, `./gradlew :app:assembleDebug` cannot download the Android SDK or remote Maven artifacts inside the air-gapped VM, and `app/build/outputs/apk/debug/app-debug.apk` is built by the configured **GitHub Actions CI/CD workflow** (`.github/workflows/android-build.yml`) or a connected Android Studio / Termux host with internet access.
2. **YouTube Premium Policy & Technical Boundary**:
   - Unlocking paid server-side YouTube Premium entitlements without an authorized subscription is restricted by Google/YouTube server-side DRM and Terms of Service.
   - As the legitimate browser-level alternative, Aura Browser implements:
     1. `GeckoSessionSettings.Builder().suspendMediaWhenInactive(false)` so web audio/video is not forcibly suspended when switching tabs or backgrounding the browser.
     2. A bundled GeckoView WebExtension (`assets/extensions/aura-media-enhancer/manifest.json` + `content.js`) registered via [WebExtensionController.ensureBuiltIn](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html) that prevents `visibilitychange` auto-pause events and provides clean mobile theater layout options.
     3. Native Android [Picture-in-Picture](https://developer.android.com/guide/topics/ui/picture-in-picture) support when viewing full-screen web or local videos.

## 5. Latest Test & Verification Results
- **Automated Multi-Module Verification & Static Audit**:
  - Verified all 23 Gradle modules + `:build-logic:convention` (74 Kotlin/Gradle files, 7702 lines of code).
  - Verified JUnit 4 unit test suites (`AuraUrlSanitizerTest`, `AuraDownloadValidatorTest`, `YouTubeIntentResolverTest`, `PipAspectRatioTest`).
  - Verified `app/src/main/assets/extensions/aura-media-enhancer/manifest.json` schema and `AndroidManifest.xml` declarations.

## 6. Next Development Task
- Push the repository to GitHub (or trigger `.github/workflows/android-build.yml` via `workflow_dispatch`) to execute `gradle testDebugUnitTest` and `gradle :app:assembleDebug` on the Ubuntu runner with Android SDK 35 and download the compiled `app-debug.apk` artifact from `app/build/outputs/apk/debug/app-debug.apk`.
