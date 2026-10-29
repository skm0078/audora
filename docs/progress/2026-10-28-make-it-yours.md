# Make it yours: a library home the listener arranges (2026-10-28, ADR 2026-10-28-make-it-yours)
- Library ⋮ › Make it yours (search finds it too): a name for the greeting, covers or list, the home's parts turned
  on or off and moved up or down, and the Continue card's cover colour. Saved as each choice is made.
- Continue card: a soft colour from the cover (label and bar kept at AA contrast), "14 min left at your speed" and
  "Last heard: “…”".
- Your line of the day: one of your highlights, the same all day; a tap opens the book there. Recently added: a row of
  the 12 newest covers, off by default.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-make-it-yours.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** no new dependency; one 1×1 cover scale off the main thread per Continue book, a small file written at most
  every 30 s while playing, one preferences read on opening the library. APK about 15 KB.
- **Could affect:** the library's greeting, chip row, Continue card and what sits above the books; the playback
  service's per-sentence hook (one more in-memory update); the reader (one write when its time-left estimate
  changes).
- **Verified in CI:** `HomeLayoutTest`, `LineOfTheDayTest`, `BookGlancesTest`, `LibraryHomeTest`,
  `MakeItYoursScreenTest`, release journey step 44.

**Real-device checks (the test phone):** open ⋮ › Make it yours, type a name: the greeting shows it at once. Turn
Recently added on and move it to the top: it shows above Continue. Turn shelves and collections off: the chip row
goes. Play a book for a minute, go back to the library: the card says "Last heard" with the sentence and the time
left; kill the app and reopen: both are still there. Highlight a line, reopen the library: the line of the day shows
it; tap it: the book opens at that sentence. Try a pale and a dark cover in light and dark mode: the label stays
readable. TalkBack: each switch says its part, each move button says "Move Recently added up".
