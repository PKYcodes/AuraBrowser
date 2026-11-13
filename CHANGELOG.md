# Changelog

## Unreleased

### Fixed
- URLs were corrupted (`%` re-encoded) when tracking parameters were stripped; this broke OAuth redirects.
- Downloads were fake database rows; they now stream to storage with progress, cancel and checksum.
- Private tabs were written to disk and could be restored after restart; they are now memory-only.
- Extension installer accepted any URL ending in `.xpi`; now HTTPS addons.mozilla.org only.
- Media and notification permissions were never requested; music/video libraries were empty on Android 13+.
- Returning to the browser tab reloaded a stale URL; links shared while running now open correctly.
- System Back now navigates web history, exits fullscreen and closes the find bar.
- Saved privacy settings (tracking protection, cookie policy, HTTPS-only) now apply at startup and live.
- History saved the previous page's title; closing a background tab no longer switches tabs.
- Audio-only playback no longer triggers Picture-in-Picture; auto-PiP honours its setting.
- SRT subtitles were loaded with a WebVTT MIME type.
- Error page no longer injects the failed URL unescaped.

### Added
- JavaScript dialogs, file picker, `<select>` popups, HTTP auth, pop-up blocking.
- `target=_blank`/`window.open` tabs, long-press link/image menu, find in page, save page as PDF.
- Site permission prompts with remember/revoke; geolocation, notifications, camera and microphone support.
- Crash/kill recovery, background-tab memory release, tab state restore.
- Background web playback service with notification controls; YouTube background-play and hide-Shorts settings.
- Real fullscreen for local video and web video; PiP aspect ratio from the video.
- Adaptive app icon, release signing support, tag-based release CI.

### Changed
- Removed non-functional settings (theater mode); UI copy rewritten.
- CI no longer publishes a debug APK as a release on every push and no longer ignores test failures.
- Compose BOM pinned to a version compatible with AGP 8.7 / compileSdk 35.
