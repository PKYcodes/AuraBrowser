# Architecture

## Modules

```
:app                      Application, MainActivity (intents, PiP, immersive mode), manifest
:navigation               Navigation graph; screens ask the host to open URLs via a callback
:core:common              Shared models, dispatcher qualifiers
:core:designsystem        Theme, glass components, ImmersiveModeState
:core:database            Room entities/DAOs (tabs, bookmarks, history, downloads, playlists, site permissions)
:core:datastore           User settings (Preferences DataStore)
:core:network             URL sanitizer, download validator, AuraDownloadManager
:core:permissions         Runtime-permission groups and checks
:core:media-common        Media3 playback service, controller manager, equalizer
:feature:browser          GeckoView engine manager, prompt/permission delegates, browser UI, web-media service
:feature:{tabs,bookmarks,history,downloads,youtube,music,video,pip,extensions,filemanager,privacy,settings,home}
```

## Browser engine (`AuraGeckoEngineManager`)

- Owns the single `GeckoRuntime` and one `GeckoSession` per tab; exposes `sessionState`, `tabs`,
  `activeGeckoSession`, `promptQueue`, `contextMenu`, `findState`, `webMedia` and `downloadEvents` as flows.
- **Threading:** all engine callbacks and map mutations run on the main thread (`Dispatchers.Main.immediate`).
  Startup is serialised with a mutex so concurrent callers cannot create duplicate tabs.
- **Persistence:** standard tabs are stored in Room (URL, title, serialised `SessionState`, debounced).
  Private tabs live only in memory and are never persisted.
- **Settings:** the runtime is built from, and kept in sync with, the persisted settings flow
  (tracking protection, cookie behaviour, HTTPS-only).
- **Dialogs and permissions:** `AuraPromptDelegate` and `AuraPermissionDelegate` translate GeckoView requests into
  `BrowserPrompt` objects. `BrowserDialogHost` renders them; the engine never touches the UI.
- **Memory:** background sessions are closed on severe memory pressure and restored lazily from saved state.

## URL handling and security

- `AuraUrlSanitizer` is used for user-entered input only: it blocks script/data/file/content/internal schemes,
  upgrades HTTP to HTTPS (except local/private hosts) and strips tracking parameters without re-encoding the URL.
- In-page navigation is checked by the `NavigationDelegate` (`javascript:`, `vbscript:`, `jar:`, `intent:` are denied;
  `mailto:`/`tel:`/`sms:`/`geo:` open in the matching app after a user gesture).
- Extension installs are limited to HTTPS links on `addons.mozilla.org`.
- Downloads sanitise file names (path traversal, hidden files, length) and warn before opening executables.

## Media

- `AuraMediaPlaybackService` (Media3 `MediaSessionService`) hosts the single ExoPlayer; UI talks to it through
  `AuraMediaControllerManager`, which queues commands until the controller is connected and ticks the position.
- Web audio/video keeps playing in the background through `AuraWebMediaService`, a foreground service driven by
  GeckoView's Media Session API.
- Picture-in-Picture parameters are kept up to date from `MainActivity`; auto-enter honours the user setting and
  applies to video only (never audio-only music).

## Testing

Pure logic is unit-tested: `AuraUrlSanitizerTest`, `AuraDownloadValidatorTest`, `PipAspectRatioTest`,
`AddonUrlPolicyTest`, `YouTubeIntentResolverTest`. Engine and UI code needs instrumented tests on a device.
