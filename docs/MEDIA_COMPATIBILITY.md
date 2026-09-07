# Misskey attachment and media compatibility

This document records Midroid's attachment-viewing boundary against the current Misskey frontend.

## Inline-preview path

Misskey previews browser-safe image, video, and audio MIME types inside `MkMediaList` / `MkLightbox`.
Midroid does not intercept normal media requests, so HTTP range requests, buffering, seeking, and decoding normally stay in Android System WebView.

### Audio expected to preview

- `audio/mpeg`
- `audio/mp4`
- `audio/x-m4a`
- `audio/aac`
- `audio/ogg`
- `audio/opus`
- `audio/webm`
- `audio/flac`, `audio/x-flac`
- `audio/wav`, `audio/vnd.wave`

Actual decoding remains dependent on the installed Android System WebView and device codec stack.
A container/MIME being allowed by Misskey does not guarantee every codec profile inside that container is decodable on every Android device.

### Android native audio fallback

Midroid installs a narrow fallback control only while the Misskey `#pswp` lightbox is open and the currently active lightbox item contains an audio element. The control is shown as `Androidで再生` on Japanese devices and `Play with Android` otherwise. Audio elements elsewhere in the timeline, hidden lightbox items, image/video items, and background DOM must not cause the floating fallback control to appear.

The fallback deliberately does not replace Misskey's normal player automatically. Misskey's audio visualizer can intentionally retry media after an initial CORS failure, so reacting to the first HTML media error would create false fallbacks. The user explicitly chooses the Android path when WebView playback is not working.

The fallback passes the selected source through the private `midroid-audio://play` route and validates it before native playback:

- source URL must use HTTPS,
- a network host must be present,
- embedded URL credentials are rejected,
- malformed private-scheme requests are consumed rather than sent to another application.

The in-app native player uses Android `MediaPlayer` with media audio attributes and audio-focus handling. It forwards only the WebView user agent. WebView Cookie and full-page Referer headers are intentionally excluded from redirect-capable native media requests so authenticated browser credentials cannot follow a redirect to another origin. Session-protected media that requires those headers may therefore need to remain on the WebView path.

Native fallback playback is paused when Midroid moves to the background and is released when the player is closed or the activity is destroyed.

### Video expected to preview

- `video/mp4`
- `video/x-m4v`
- `video/quicktime`
- `video/3gpp`, `video/3gpp2`
- `video/mpeg`
- `video/webm`
- `video/ogg`

Actual codec support is device/WebView-dependent.

## Eco media policy

Midroid must not set `WebSettings.mediaPlaybackRequiresUserGesture=true`.
Misskey opens its lightbox asynchronously and can call `HTMLMediaElement.play()` after the original pointer event task has ended. Android WebView's gesture gate can therefore reject legitimate user-initiated Misskey audio/video playback.

Eco mode instead saves power by:

- preferring 60 Hz,
- reducing animation/transition work,
- stopping `audio[autoplay]` and `video[autoplay]`, and
- pausing all WebView media plus any Midroid native fallback audio when the activity moves to the background.

This preserves explicit Misskey media playback while keeping background/autoplay work conservative.

## Non-previewable attachments

Misskey renders other files with `MkMediaBanner` as a download link rather than an inline viewer. This includes, depending on MIME, PDF, Office documents, plain/binary documents, archives, executables, and other arbitrary Drive files.

Midroid's `DownloadListener` routes HTTPS downloads to Android `DownloadManager`, forwarding MIME type and user agent while intentionally excluding WebView Cookie/Referer credentials from the redirect-capable system download client. The system download notification is then responsible for handing the completed file to an installed viewer.

Midroid intentionally does not render arbitrary downloaded documents inside its privileged/login WebView.

## Security boundaries

- Main Misskey instance must be HTTPS.
- Mixed HTTP content is blocked.
- WebView filesystem access is disabled.
- Third-party cookies are disabled.
- Camera/microphone Web permissions are denied in the MVP; this does not affect playback of already-attached media.
- Native audio fallback accepts only validated HTTPS media URLs.
- Redirect-capable native media/download clients do not receive WebView Cookie or full-page Referer credentials.
- Arbitrary files are not loaded into a custom in-app document renderer.

## Known edge cases

- `application/ogg` is in Misskey's browser-safe MIME list but current `isPreviewable()` only promotes MIME strings starting with `image`, `video`, or `audio`; it can therefore fall back to the download banner.
- A browser-safe container may contain a codec/profile unsupported by the installed Android System WebView. Android `MediaPlayer` support can also differ by device/codec stack.
- Cleartext (`http://`) attachment resources are intentionally not loaded inside Midroid.
- Session-protected media/downloads that require browser cookies may not work through the native/system fallback because those credentials are intentionally not forwarded across redirect-capable clients.
- Remote/federated media that depends on third-party cookies can fail; normal public Misskey media should not require them.
- Android camera/microphone capture from web content remains intentionally unsupported in the MVP.

## Manual regression matrix

Test at least the following on a real device after media-policy changes:

1. MP3 audio, play/pause/seek in Eco, Balanced, Performance.
2. M4A/AAC audio.
3. Ogg/Opus audio.
4. FLAC and WAV audio.
5. For an audio file that fails in WebView, choose `Androidで再生`; verify prepare/play/pause/seek.
6. Verify the floating `Androidで再生` control is absent on a normal timeline even when other notes contain audio.
7. Verify an image/video lightbox does not show the native-audio fallback after previously visiting an audio item.
8. Verify an active audio lightbox shows the fallback, including after sensitive media is revealed and after moving between mixed gallery items.
9. Verify session-protected audio remains on the WebView path if native playback cannot proceed without browser credentials.
10. MP4/H.264 video, play/pause/seek.
11. WebM video.
12. A federated remote-instance audio/video attachment.
13. PDF download and open from the Android download notification.
14. ZIP or other arbitrary attachment download.
15. Background Midroid during WebView and native fallback playback; verify media pauses.
16. Return to foreground; verify playback does not restart unexpectedly.
