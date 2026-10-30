# The player names its line actions (2026-10-30, ADR 2026-10-30-player-line-actions)
- Under the sentences, the player has Bookmark, Highlight and Share buttons for the line being read, each with a
  label that says its state. The bookmark moved here from beside the title.
- The reader's ⋮ has "Gestures and tips", which shows the tips card again.
- The reader's page/text toggle now says "Text" or "Page" beside its icon.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-player-line-actions.txt`. `:pdf` unchanged. No new
  dependency. New icon `ic_highlight` (Material, Apache-2.0). New strings in `strings_player_line.xml` (three
  languages).
- **Cost:** one row of three buttons, about 56dp less height for the player's sentences on a phone. No new state or
  background work. A few KB of APK.
- **Could affect:** the player's layout (sentence area shorter), the player's title line (no bookmark icon), the
  reader's top bar width (named toggle), the reader's ⋮ (one row more).
- **Verified in CI:** `PlayerLineActionsTest`, `PlayerBookmarkTest`, release journey step 58.

**Real-device checks (the test phone):** play a book and open the player: Bookmark, Highlight and Share sit under the
sentences. Tap Highlight: it says "Highlighted" and the line is coloured; tap again to clear. Tap Bookmark: it says
"Bookmarked". Tap Share: the Share choices open for the line being read. Turn on kids mode: the row is gone. In the
reader, ⋮ › Gestures and tips shows the card; Got it hides it. The top bar's toggle says "Text" on a page and "Page"
on text. Repeat in Hindi and Bengali, and with the largest text size, checking the labels wrap and nothing is cut.
