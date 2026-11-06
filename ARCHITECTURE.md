# Aura Browser — System & Module Architecture (`ARCHITECTURE.md`)

## 1. Architectural Principles
Aura Browser follows Google's official [Modern Android App Architecture](https://developer.android.com/develop/ui/compose) and Mozilla's [GeckoView Embedding Architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html):
- **Strict Multi-Module Separation**: Divided into `:build-logic`, 7 `:core:*` modules, 14 `:feature:*` modules, `:navigation`, and `:app`.
- **Single-Process `GeckoRuntime` Singleton**: Per [Getting Started with GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html), `GeckoRuntime` can only be instantiated once per process. `AuraGeckoEngineManager` (`@Singleton`) binds a single `GeckoRuntime` to the application context and manages a `ConcurrentHashMap<String, GeckoSession>` keyed by tab ID.
- **Lifecycle-Safe `GeckoSession` Management**: Active tabs receive `GeckoSession.PRIORITY_HIGH`, background tabs drop to `GeckoSession.PRIORITY_DEFAULT`, and closing a tab explicitly calls `session.setActive(false)` and `session.close()` before removing it from Room (`TabDao`).
- **Decoupled Background Media Service**: Local audio and video playback run through `AuraMediaPlaybackService` extending AndroidX Media3's [MediaSessionService](https://developer.android.com/media/media3), connected to Compose screens via `AuraMediaControllerManager` (`MediaController`).

## 2. Dependency Graph Overview
```
:app
 ├── :navigation
 │    └── :feature:{home,browser,tabs,bookmarks,history,downloads,youtube,music,video,pip,extensions,filemanager,privacy,settings}
 ├── :feature:browser
 │    ├── org.mozilla.geckoview:geckoview:140.0.20250707120347
 │    └── :core:{common,designsystem,database,datastore,network,permissions}
 ├── :feature:music & :feature:video
 │    ├── :core:media-common (androidx.media3:media3-{exoplayer,session,ui,ui-compose}:1.11.1)
 │    └── :feature:pip (Android PictureInPictureParams + RemoteAction Receiver)
 └── :feature:extensions
      └── Mozilla WebExtensionController (install, ensureBuiltIn, enable, disable, uninstall)
```

## 3. Security & Privacy Architecture
- **Safe URL & Scheme Filtering (`AuraUrlSanitizer`)**: Blocks `javascript:`, `vbscript:`, `data:`, `jar:`, and `intent:` URI injection in the omnibox and inside `GeckoSession.NavigationDelegate.onLoadRequest`.
- **HTTPS-First & Tracking Parameter Removal**: Automatically upgrades `http://` navigations to `https://` (excluding localhost/LAN) and strips `utm_*`, `fbclid`, `gclid`, `mc_cid`, `msclkid`, and `igshid` parameters.
- **Download Validation (`AuraDownloadValidator`)**: Sanitizes path traversal sequences (`../`), computes SHA-256 checksums, and flags executable packages (`.apk`, `.dex`, `.sh`, `.so`) before opening.
- **Private Browsing Isolation**: Private tabs set `GeckoSessionSettings.Builder().usePrivateMode(true)`, bypass `HistoryDao.recordVisit()`, and purge session caches via `StorageController.ClearFlags.ALL_CACHES` when burned.
