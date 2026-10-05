# YouTube 403 research notes

Sources:
- https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/DownloaderImpl.java
- https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenWebView.kt
- https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenProviderImpl.kt
- https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/assets/po_token.html
- https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide

Findings: NewPipe uses a shared Downloader with Firefox desktop UA, compression, consent SOCS cookie, Origin and Referer. It creates Web BotGuard tokens by loading po_token.html in a WebView, POSTing to /api/jnn/v1/Create and /GenerateIT with x-goog-api-key, then generating a streaming token using visitorData and a per-video player token. NewPipeExtractor exposes YoutubeStreamExtractor.setPoTokenProvider(PoTokenProvider). PO tokens are video/session bound and can expire, so they must be generated at runtime and never bundled or logged.

Implementation decision: integrate a compact runtime WebView PoTokenProvider and wire it to NewPipeExtractor; retain yt-dlp fallback. This is stronger than changing player clients alone, but remains subject to YouTube enforcement changes.
