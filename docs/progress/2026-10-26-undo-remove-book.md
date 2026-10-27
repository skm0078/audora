# Undo after removing a book (2026-10-26, CF-3)
- Removing a book hides it at once and shows "Removed "<title>"" with **Undo** for 8 seconds (longer when TalkBack or
  Android's "Time to take action" asks for it). Nothing is deleted during that window: the row, its place, notes,
  collections, cover and the file grant all stay, so Undo only shows the book again. When the window ends (plus one
  second, so an Undo tapped as the snackbar closes still counts) the removal runs as before.
- If the app's screen closes inside the window, the removal runs then (the listener asked for it). If the process is
  killed inside the window, the book stays in the library: losing nothing beats a removal the listener could not undo.
- A removal that fails shows the book again with the usual error snackbar.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-undo-remove-book.txt`. No `:pdf` change. No new
  dependency.
- **Could affect:** the library list and its empty state, the Continue reading card, cover pruning, the removal
  dialog, other library snackbars (one shows at a time; a new message replaces the Undo snackbar, and that removal
  still runs at the end of its window).
- **Verified in CI:** `LibraryControllerUndoTest` (hidden at once and deleted only after the window; Undo keeps the
  book and its file grant; a late Undo inside the grace second; the screen closing still removes; a failed removal
  shows the book again; two removals undone one at a time; a second confirm changes nothing),
  `RemovalUndoScreenTest` (Undo on the snackbar reaches the controller; the snackbar closes by itself; screenshot
  `library-removed-undo.png` in the "player-screens" artifact), `ReleaseJourneyTest`.

**Real-device checks (the test phone):** remove a book you have listened to, tap Undo: it is back with the same
progress bar and Continue reading. Remove it again and wait about 10 seconds: it stays gone after leaving and reopening
the app. With TalkBack on, the Undo button is read out and stays long enough to reach it.
