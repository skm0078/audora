# The app lock shows a screen saver: the time, the logo breathing, Pause while it reads (2026-10-28, ADR 2026-10-28-lock-screen-saver)
- The locked screen is now a night-coloured screen saver: clock and day, the logo breathing inside two fading rings,
  "Still reading to you" with moving bars and a Pause button while a book plays, and Unlock. No book is ever named.
  Motion stops with "Remove animations" or battery saver. The Unlock prompt is taken away when the lock goes.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-lock-screen-saver.txt`. `:pdf` unchanged. No new
  dependency. APK about 45 KB bigger (36 KB of it the sharp logo).
- **Could affect:** the app lock and its prompt (`AppLockGate`, `DeviceUnlock`), "Hide app content".
- **Verified in CI:** `LockClockTest` (minute wake-ups), `LockSaverTest` (Pause only while reading, Unlock always,
  Bengali at double text size and sideways reach Unlock; screenshots), release journey step 31 (CI's emulator has no
  secure lock screen, so it checks that the lock opens without one; the PIN path is a phone check).

**Real-device checks (the test phone):** turn on App lock, play a book, leave the app for 30 s and come back: the
saver shows over the prompt after you cancel it, the bars move, Pause stops the voice and the bars go. Turn on battery
saver: everything holds still. Check that Unlock with fingerprint and with PIN opens the library where you left it.
