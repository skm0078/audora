# ADR 2026-10-28-lock-screen-saver: the app lock shows a light screen saver

**Status:** Accepted (2026-10-28). Builds on ADR-061 (app lock). Follows the "App lock: breathing logo" board on the
design canvas, which the maintainer asked for ("a good screen saver, a very light animation of the logo, not heavy")
and approved with the rest of the "Your listening" row.

## Context

The app lock covered the library with a plain page: a title and an Unlock button. People who lock the app often leave
it reading with the phone on a table, so the lock is what they look at. It should feel like the app's own screen,
stay private and cost next to nothing.

## Decision

- **What it shows:** the time (the phone's 12 or 24 hour format) and the day in the app's language; the logo breathing
  inside two rings that grow and fade; while a book plays, seven soft bars and "Still reading to you", with a Pause
  button; Unlock; and a line saying books and notes stay hidden. Never a book's title, cover or text.
- **Colours:** its own night palette (deep forest green, `LockSaverColors`), the same in light and dark themes: a
  saver is looked at in the dark. Ink on the night colour is 16:1, the softer text 7:1.
- **Motion budget:** every animated value is read in `graphicsLayer`, so a frame moves layers only (no recomposition,
  layout or redraw). The clock wakes once a minute while the screen is in sight. Nothing moves, and no animation
  clock runs, when Android's "Remove animations" is on or battery saver is on (`rememberDecorativeMotion`, shared in
  `ui/theme/Motion.kt` for later decoration).
- **Playback:** the saver has its own player controller, connected only while it is in sight, so the lock never
  reaches into the screens it hides. The library's mini player already keeps the service bound.
- **The prompt goes with the lock:** `DeviceUnlock.ask` returns its cancellation signal, and the lock cancels it when
  it leaves, so a prompt cannot sit over an app that opened another way.
- **Logo asset:** the store master cut to 600 px tall (`drawable-nodpi/lock_saver_logo.webp`, 36 KB), because the
  launcher's 108 dp copy blurs at the saver's size.

## Alternatives

- **Lottie or an animated vector:** a new dependency or a hand-made animation for a few seconds of breathing. Rejected.
- **Showing the book being read:** rejected; the lock exists to hide the library.
- **Follow the app theme:** a cream or sage saver at night is a torch. Rejected for the fixed night palette.

## Consequences

- About 45 KB more APK (36 KB logo, the rest code and strings). No new dependency.
- While the saver is in sight and moving, the screen draws every frame (10 small layers). It stops with the screen
  off, with the app out of sight, with "Remove animations" and with battery saver.
- Release journey step 31 sets a PIN on the emulator, turns the lock on, checks the saver and that Unlock asks for
  the phone's lock, then clears the PIN.
