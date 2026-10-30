# A group opened in a sheet scrolls its options into view (2026-10-30, no ADR)
- Phone test before v0.1.0: on the Voice and speed sheet (the speed chip), opening a group low in the sheet (Sound,
  Voices for characters, Study, Focus sound) put its options below the sheet's edge, so nothing seemed to happen and
  the listener had to find and scroll to them by hand.
- New `ui/theme/OpenInPlace.kt`: `InPlaceGroup` holds a header and, while open, its options as one column. When the
  listener opens it (`InPlaceOpening.opened()` from the header's tap), the next frame brings the whole group into view:
  the header and as many options as fit, the header at the top when the group is taller than the sheet. Keyboard,
  D-pad and Switch Access focus moves to the first option. A group restored open after a rotation, or opened by a
  shortcut, does not move anything.
- TalkBack's focus stays on the header on purpose. That is the standard for a section that expands: the header says
  "expanded", the next swipe reads the first option, and the header is still there to close the group.
- Audit of the same pattern: every section that opens in place now uses it. That is the Voice and speed groups
  (`SpeechGroup`), More options (`MoreOptions`, Voices screen) and the book menu's Save as card (`SaveAsCard`).
- Perf cost: one frame wait and one scroll animation when a group is opened by a tap. Nothing at rest.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-open-in-place.txt`. `:pdf` unchanged. No new dependency.
- **Could affect:** the Voice and speed sheet, the Voices screen's More options, the book menu's Save as card.
- **Verified in CI:** `SpeechSheetGroupsTest.openingAGroupLowDownBringsItsOptionsIntoView` (Study opened from just in
  view shows its first option and keeps its header on screen); release journey step 59 does the same on the installed
  app from the speed chip.

**Real-device checks (the test phone):** open a book, tap the speed chip, scroll until the Study header is the last
thing on screen and tap it: the sheet scrolls up and "Read each sentence" shows under the header. Do the same with
Focus sound. With TalkBack on, double tap a group header: it says "expanded", and one swipe right reads its first
option. Rotate with a group open: the sheet stays where it was.
