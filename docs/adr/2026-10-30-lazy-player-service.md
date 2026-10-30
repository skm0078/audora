# ADR 2026-10-30-lazy-player-service: start the player service when something needs it, not with the app

**Status:** Accepted (2026-10-30). PR3 of the on-phone speed audit (item 2 of the ranked list), approved by the
maintainer. No feature removed; nothing the listener sees or hears changes.

## Context

Every cold start created `AudoraPlaybackService`, the app's largest single piece of start-up work after the first
screen: Hilt's stores, the ExoPlayer, the voice engines, the story brain's reader, the media session and the book
session. Nothing asked for it. The mini player (`MainActivity`, above every tab and the first run) and the app lock's
saver (`AppLockGate`) each connected a `MediaController` as soon as they were shown, and a controller's connection
starts and binds the service (Media3 `MediaSessionService`). Both only show and steer what is already playing, and a
freshly started service has nothing loaded: the mini player draws nothing until a book is in the player
(`nowPlaying` returns null without a position). So the work bought nothing until the reader, the tile, a headset or
the car asked to play.

## Decision

1. **The service says when it is up** (`PlaybackServiceLife.up`, in `:playback`'s contract package): true from the end of
   `AudoraPlaybackService.onCreate` (session built) to the start of `onDestroy`. The service runs in the app's
   process, so a new process starts with it down, as it is. A process with a restore half undone builds no session
   and never reports itself up (ADR-046).
2. **Screens that only show playback join it, never start it** (`rememberConnectedPlaybackPort`): connected while
   visible and while the service is up (`joinWanted`), released otherwise. Once connected, the controller keeps the
   service bound as before, so a paused book stays in the mini player while the app is on screen. The reader's
   connection and this one now share one effect (`ConnectWhileEffect`, `rememberShown` in `PlayerConnection.kt`)
   instead of two copies of the same lifecycle plumbing.
3. **Everything that plays still starts it**: the reader's own controller (`PlayerConnectionEffect`), the quick
   settings tile, headset and media buttons (playback resumption), Android Auto. None of them changes.

## Consequences

- **Speed:** a cold start no longer builds the player, voices and session; the first screen and the library get that
  time. The first book opened after a cold start builds the service instead, while the reader reads its page; the
  reader's voice warm-up (ADR 2026-10-29-speed-quick-wins) already waits for a late connection.
- **Cost:** no new memory, code or dependency; one `StateFlow` in `:playback`. The trade is time moved from launch to
  the first book open, which the test phone judges (PR's phone checks).
- **Behaviour kept:** playing in the background, then opening the app: the service is up, the mini player joins it.
  The service ending (nothing bound, nothing playing) drops the mini player's connection, as a fresh start would.
- **Tests:** `JoinWantedTest` (down on a fresh screen, joins once up, lets go when hidden or when the service ends,
  joins a service started while hidden); release journey step 63 (the first screen of a fresh install leaves the
  service down for three seconds).

## Alternatives considered

- **Start the service a few seconds after launch, when idle.** Keeps the first book open as fast as before but
  spends the same memory and battery on every launch whether or not anything is read. Rejected: the speed audit's
  point was not to do work the listener may never need.
- **Query `ActivityManager` for running services.** Deprecated for third-party apps and racy; the service knows its
  own life better.
