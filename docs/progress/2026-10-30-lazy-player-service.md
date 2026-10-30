# Player service only when needed (2026-10-30, ADR 2026-10-30-lazy-player-service)
- A cold start no longer starts the playback service. The mini player and the app lock's saver join it once
  something else started it: the reader, the tile, a headset, the car (`PlaybackServiceLife`, `joinWanted` in
  `ConnectedPlaybackPort.kt`).
- `AudoraPlaybackService.onCreate` hands playback resumption's setup to `playbackResumption()` (same code, kept under
  detekt's method length).
- **Could affect:** the mini player after playing in the background, the lock screen's saver while reading, the first
  book opened after launch (it now starts the service).
- **Verified in CI:** `JoinWantedTest`, release journey step 59 and every playback step after it (03 to 05b, 56, the
  media controls), the existing reader and lock tests.

**Cost:** time moves from every launch to the first book opened after it; no memory, code or dependency added.

**Real-device checks (the test phone):** cold start the app (swipe it away first): the library should appear a
little sooner. Open a book and press Play: it plays as before. Play a book, leave the app, come back: the mini player
shows it. Turn on the app lock, play, lock: the saver shows the book is being read and Pause works.
