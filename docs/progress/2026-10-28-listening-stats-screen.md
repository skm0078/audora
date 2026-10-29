# A full listening stats screen and a stats share card (2026-10-28, ADR 2026-10-28-listening-stats-screen)
- "Listening stats" is now a full screen, "Your listening": Week / Month / Year / All time; the total with a weekly
  goal ring and the change from last time; the period's bars; streak and best (one missed day a week forgiven), books
  finished in the period and words heard ("about 1 novel"); languages; a calendar of the last 20 weeks and days
  listened this year; the most listened books with covers; when in the day; six milestones; Clear.
- Share opens the stats card: Month, Week, Year or Streak; five colours; story or square; Save picture (to
  Pictures/ListenLock) or Share. The ListenLock mark is always on.
- Listening is now kept by hour, book and language with the words heard (still only on the phone; the privacy page says
  so). Older stats load as they were.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-listening-stats-screen.txt`. `:pdf` unchanged. No new
  dependency.
- **Could affect:** the playback service's listening count (`ListeningBridge`, `SessionPublisher`), backups of
  `listening-days.txt`, the share card frame (its capture helper is now shared).
- **Verified in CI:** `:core` tests for the store (version 1 to 2), tally (hours, books, languages, words), report,
  streaks, calendar, milestones and word counts; `ListeningBridgeTest`; `StatsAllPeriodsTest`;
  `ListeningStatsScreenTest` (tabs, goal, Clear, empty, large Bengali, every card kind and colour, with
  screenshots); release journey step 30 (opened from search, Month, Save picture lands in the phone's pictures, Share
  writes a 1080 x 1920 card and opens the share sheet).

**Real-device checks (the test phone):** listen to a book for a few minutes in two languages, open Listening stats:
the week's bar for today, the languages split and the book under Most listened should match. Change the goal and see
the ring move. Tap Share, try Streak and Paper, Save picture and find it in the gallery under ListenLock.
