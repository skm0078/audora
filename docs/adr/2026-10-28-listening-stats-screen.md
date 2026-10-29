# ADR 2026-10-28-listening-stats-screen: listening kept by hour, book and language, for a full stats screen and card

**Status:** Accepted (2026-10-28). Changes decision 3 of ADR 2026-10-26-listening-stats-on-the-phone ("minutes per
day only"); the rest of that ADR stands.

## Context
The listener found the CF-8 sheet too basic. The agreed mockups (canvas row "Your listening": Stats-top, Stats-more,
Stats-share) ask for Week / Month / Year / All time, a weekly goal ring, the period's bars, a streak with its best,
books finished in the period, words heard, the languages listened in, a calendar of days listened, the most listened
books, when in the day one listens, milestones, and a share card. Minutes per day cannot answer "which book", "which
language", "when in the day" or "how many words".

## Decision
1. **Keep listening by slot**: local day, local hour, the book's library id, the voice language of the sentences
   heard, milliseconds and words (`ListeningSlot`, `Heard`). Never a page, a title or any text. The file keeps its name
   (`listening-days.txt`, already in the backup) with a version 2 header; version 1 files load as day-only slots,
   which count in every total and streak and nowhere that needs the missing part, and are rewritten as version 2 on
   the next save.
2. **Count in the service as before.** `ListeningTally` splits time at each hour's turn (it split at midnight), gives
   time to the book and language heard before each change, and counts a sentence's words when it starts
   (`WordCount`: space-separated pieces with a letter or digit, which works for English, Hindi and Bengali). Saving
   stays at each page change and stop; sentences only add up in memory between.
3. **Derive everything else** in `:core`, pure and tested: `ListeningReport` (a period's totals, the same number of
   days of the period before, bars, languages, top books, hours, peak window, days listened), `ListeningStreaks`
   (with one forgiven rest day a week between two days listened, the design doc's gentle habit), `ListeningCalendar`
   and `ListeningMilestones` (worked out from all time, so nothing about badges is stored).
4. **The screen is full screen** (a dialog over the library, opened from the same menu item and from search), with the
   share card as its second page. The card reuses `ShareCardFrame` (the ListenLock mark is always on), `CardRecorder`,
   `ShareCardFiles` and the share sheet; "Save picture" adds a PNG to Pictures/ListenLock through MediaStore, which
   needs no permission on Android 10 and later.
5. **The weekly goal** is one number in its own SharedPreferences (default 5 hours, "No goal" possible), shared out
   over a month (days / 7) and a year for their rings. It is not in the backup.

## Consequences
- The privacy page now says the stats keep the hour, the book and the language, never the page or the words.
- The file grows with listening: about 45 bytes a slot, one slot per hour, book and language. Two hours a day is about
  33 KB a year, rewritten whole on a background thread at each page change. Old slots are not folded into days yet;
  if the file passes about 200 KB that is the lighter option (keep hours for the last year only).
- Rest days are always on (the design doc suggested a switch to turn them off); the screen says when one was used.
- Languages are the app's three voice languages (`Lang`); a fourth needs only a new `Lang`.

## Alternatives considered
- **A Room table**: the reasons of the earlier ADR hold (a schema bump shared with every branch, for data that never
  joins with the library).
- **Storing per sentence**: exact, but tens of thousands of lines a year for no question the screen asks.
- **Counting words from the page text when a page is reported**: counts pages skipped or half heard.
