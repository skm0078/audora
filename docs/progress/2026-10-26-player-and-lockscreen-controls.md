# Lock screen buttons, one-tap bookmark, resume on reconnect, widget time left (2026-10-26, ADR 2026-10-26-player-and-lockscreen-controls)
- CF-1: the notification and lock screen show previous sentence, play/pause, next sentence and bookmark. Previous
  always goes back a whole sentence. A bookmark from there (or a headset double press) buzzes and shows the bookmark
  filled while that sentence plays.
- CF-2: the player has a bookmark button at the end of the title row: one tap saves the sentence being read and shows
  "Bookmarked" with Add note and Undo. A sentence that already has a bookmark shows it filled; a tap opens its note.
- CF-4: Settings → Listening → "Resume when headphones reconnect" (off by default). Headphones or a Bluetooth speaker
  that come back within 30 minutes of disconnecting resume reading; after 10 minutes only with Background reading
  allowed (Android refuses otherwise).
- CF-7: the home-screen widget shows "About 2 h 10 min left · page 12 of 340" once the book has been opened in the
  reader, and counts down while listening with the screen off.
- "What's new" entries: `2026-10-26-lock-screen-buttons`, `2026-10-26-one-tap-bookmark`,
  `2026-10-26-resume-on-reconnect`, `2026-10-26-widget-time-left`. No `:pdf` change. No new dependency.
- **Could affect:** the media notification and lock screen controls, headset bookmarks (same code path now), the
  reader's "Bookmark here" (shares `sentenceHere`), the player's title row, the widget's second line, Settings →
  Listening.
- **Verified in CI:** `ReconnectResumeTest`, `ListeningSpansTest`, `BookmarkedSentenceTest`, `WidgetTimeLeftTest`,
  `PlayerBookmarkNotesTest`, device tests `LockScreenButtonsTest`, `PlayerBookmarkTest` and
  `WidgetTimeLeftScreenTest` (screenshots `player-bookmark-button`, `player-bookmark-notice`, `widget-time-left`,
  `widget-no-time-left` in the "player-screens" artifact), and `ReleaseJourneyTest` step 05b (the lock screen's
  buttons go back, on and bookmark through a controller, and never start a paused book).

**Real-device checks (the test phone):**
1. Play a book, lock the phone: the lock screen shows previous, play/pause, next and a bookmark. Previous goes back one
   sentence even mid-sentence; next goes on one. Tap bookmark: a short buzz, the icon fills, and empties on the next
   sentence. The bookmark is in the reader's Bookmarks list.
2. Pull down the notification and expand it: the same four buttons.
3. In the full player, tap the bookmark beside the title: "Bookmarked" with Add note and Undo. Undo removes it; Add
   note opens the note. Tap the button again on a bookmarked sentence: its note opens, no second bookmark.
4. Settings → Listening → turn on "Resume when headphones reconnect". Play with Bluetooth earbuds, take them out
   (reading pauses), put them back within a minute: reading goes on. Pause by hand, take them out and back: it stays
   paused. With the switch off: it stays paused.
5. Add the widget to the home screen, listen a few minutes with the screen off: its second line says about how long
   is left, and it is a few minutes less after a pause.
