# Development status

Last reviewed: November 2026.

## Verification

- **Compiled:** no. The environment used for the latest refactor had no Android SDK or network access.
  Kotlin was written against GeckoView 140 and Media3 1.5 APIs from knowledge of their public docs; expect a small
  number of signature mismatches on the first build.
- **Unit tests:** written for URL handling, download validation, PiP ratio maths, add-on URL policy and YouTube link
  parsing. Not yet run.
- **On-device testing:** not done.

## Verify these on first build (highest compile-error risk)

1. `AuraPromptDelegate` — GeckoView `PromptDelegate` prompt classes (`ChoicePrompt`, `FilePrompt`, `AuthPrompt`).
2. `AuraPermissionDelegate` — `onMediaPermissionRequest` array parameter types and `MediaCallback.grant`.
3. `AuraGeckoEngineManager` — `ContentDelegate.onContextMenu`, `onKill`, `MediaSession.Delegate`,
   `GeckoSession.saveAsPdf()` and `finder`, `WebExtension.MessageDelegate`.
4. Compose BOM 2025.02.00 with AGP 8.7.3 / compileSdk 35 (chosen to be mutually compatible).

## Known limitations

- **Extension installation from AMO** needs a `WebExtensionController.PromptDelegate`; its signature differs between
  GeckoView versions, so it is not wired yet. The built-in extension and enable/disable/uninstall work without it.
- **Date, time and colour pickers** on web forms are not handled (the engine dismisses them).
- **Downloads** cannot be paused/resumed (they stream from the engine); cancelled or failed downloads must be restarted.
- **Room migrations:** the database still uses destructive migration; replace it with real migrations before release.
- **Gradle wrapper JAR** is not checked in: run `gradle wrapper --gradle-version 8.10.2` once.
- **Hide Shorts** relies on YouTube's current markup and may need selector updates.

## Roadmap

1. First green build and device smoke test.
2. Extension install prompt, date/time pickers.
3. Instrumented UI tests for tab lifecycle, downloads and permission prompts.
4. Room migrations, ProGuard rule tightening, accessibility pass.
