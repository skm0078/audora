# ADR 2026-10-26-listening-stats-on-the-phone: listening stats as a per-day file, counted by the playback service

**Status:** Accepted (2026-10-26). Ticket CF-8 (competitor coverage, final Kotlin batch, group B).

## Context
CF-8 asks for minutes listened per day and week, books finished and a current streak on a small Stats screen, kept on
the phone only. The ticket sketch said "a small Room table". Three facts shaped the design:
- Listening happens with the screen off and in other apps, so only the playback service sees all of it. The service
  already reports "reading" (on play and on every page change) and "not reading" (pause, stop, end, error, sleep) to
  the unfinished-session marker (P1-07).
- The Room database's version is shared by every branch: two parallel branches that each add a table both write
  schema 6 and cannot merge in either order (ADR 2026-10-25-parallel-prs-merge-cleanly).
- "Books finished" already exists: the library's Finished filter is a book whose place is on its last page.

## Decision
1. **Count in the service.** `SessionPublisher` forwards reading / not reading to a `ListeningBridge`, which feeds a
   pure `:core` `ListeningTally`. Duration comes from `elapsedRealtime` (a clock change never adds time; deep sleep
   with the screen off is counted); the day comes from the wall clock and the phone's zone, split at midnight. Time is
   saved at every page change and at every stop, so a killed process loses one page's worth at most. A stretch over
   24 hours without a report is capped (a lost stop, not a listener).
2. **Store one line per day in a small file**, `filesDir/listening-days.txt` (`ListeningDaysStore`, the same durable
   write and set-aside rules as the quiz progress file), not in Room: no schema bump, no migration, nothing shared
   with other branches. One process-wide `ListeningLog` (Hilt singleton) holds it in memory; the stats sheet reads its
   `StateFlow`. The backup lists the file, so stats move to a new phone.
3. **Keep only minutes per local date.** Never a book, page, title or time of day. Nothing leaves the phone: no
   analytics, no network. The sheet says so and offers Clear stats (asks first).
4. **Derive, don't store, the rest.** This week, the 7-day chart, the current and longest streak (a day counts from
   one minute, `ListeningStatsConfig.streakDayMs`; today not yet listened does not break the streak) are worked out
   by `ListeningSummary` when the sheet opens. Books finished counts the library's Finished books from their places.

## Consequences
- The sheet opens from the library's ⋮ menu ("Listening stats"), next to Quiz me.
- Buffering while a sentence renders counts as listening, as it does for the session marker: the listener is waiting
  on the book, not doing something else.
- Saved audiobooks and studio files played in the in-app player (ADR-068) do not count yet; only books read by the
  playback service do.
- A restore replaces the file and restarts the app, as for the other listed files.
- The privacy page lists the per-day minutes among what stays on the phone.

## Alternatives considered
- **A Room table** (the ticket's sketch): a schema bump that conflicts with any other branch adding a table, for data
  that never joins with the library.
- **DataStore preferences**: `:playback` does not use DataStore today; a file store in `:core` is testable on the JVM
  and matches the quiz progress store.
- **Counting in the UI** (the player screen): misses screen-off and background listening, which is most of it.
