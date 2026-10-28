# Long jobs share one way to run in the background (2026-10-28, no ADR)
- Refactor, no behaviour change. The five long-job services (audiobook export, accessible and talking copies, studio
  mode, audio clips, quote videos) each carried their own copy of the foreground type, the wake lock set-up and the
  "leave the foreground" steps. They now share `playback/jobs/ForegroundJobs.kt`: `ForegroundJobs.type()`,
  `jobWakeLock(tag)`, `releaseIfHeld()` and `leaveForeground(runningNoticeId)`.
- The clip and quote-video wake locks are now not reference counted, like the other three. Each takes it once and
  releases it once, so nothing changes; it only removes the one way a later second `acquire` could have leaked a hold.
- "What's new" entry: none. `:pdf` changed: no. New dependency: no. Performance cost: none (same calls, one less
  object per job type in the code, nothing at run time).
- **Could affect:** audiobook export, Make an accessible/talking copy, studio mode, Share as audio clip, quote video:
  starting, cancelling, Android 15's time limit, and the running notification going away when each ends.
- **Verified in CI:** ktlintCheck, detekt, unit tests, assemble, and the release journey test (export, clip and copy
  steps).

**Real-device checks (the test phone):** start an audiobook export and cancel it; make a short audio clip; make a
quote video. Each running notification disappears when the job ends, and the screen can be off while it runs.
