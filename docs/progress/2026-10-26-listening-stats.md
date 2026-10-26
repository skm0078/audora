# Listening stats and streaks, kept on the phone (2026-10-26, ADR 2026-10-26-listening-stats-on-the-phone)
- Library ⋮ → **Listening stats** opens a sheet: today, this week (from the phone's first day of the week), current
  streak, longest streak, books finished, all-time total, and the last 7 days as bars (TalkBack reads each day's name
  and time). Nothing listened yet shows how it works. **Clear stats** asks first.
- The playback service counts the time (screen off and in other apps included), by local day, split at midnight,
  saved at every page change and every stop. Only minutes per day are kept, in `filesDir/listening-days.txt`; never
  a book or time of day, and nothing leaves the phone. The backup includes the file; the privacy page and the backup
  explanation say so.
- A day keeps the streak from one minute of listening; today not yet listened does not break it. Books finished are
  the library's Finished books.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-listening-stats.txt`. No `:pdf` change. No new
  dependency. No Room schema change.
- **Could affect:** the playback service's reading reports (`SessionPublisher`), backup and restore, the library's
  ⋮ menu.
- **Verified in CI:** `ListeningTallyTest` (counts only while reading; a clock change adds nothing; midnight split;
  long gaps capped), `ListeningSummaryTest` (today, week by first day, chart, streak table), `ListeningLogTest`
  (adds up, survives a new process, clear, foreign file set aside or never overwritten, bad lines dropped),
  `ListeningBridgeTest` (page changes and stops save to the file), `FinishedCountTest`, `ListeningStatsSheetTest`
  (numbers, empty state, Clear asks first; screenshots `listening-stats.png` and `listening-stats-empty.png` in the
  "player-screens" artifact), `BackupArchiveTest`, `ReleaseJourneyTest`.

**Real-device checks (the test phone):** play a book for a few minutes with the screen off, then open Library ⋮ →
Listening stats: today shows those minutes and the streak is 1 day. Make a backup and check Clear stats empties the
sheet after asking.
