# A lighter library home and a 1-minute tour (2026-10-28, ADR 2026-10-28-home-and-tour)
- Library: a greeting over the title, the Continue card on one compact row with a round Play, shelves and
  collections in one chip row, and the battery card as a one-line banner.
- Tour: offered once when the library first has a book (Maybe later / Show me around); five stops point at Play, the
  first book, Notes, Made and You; replay from You › Show me around, which search finds too.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-home-and-tour.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** no new dependency or asset; five tiny bounds reporters on layout (a state write only when a target moves),
  one offscreen layer only while a stop shows, one boolean read once at start. APK about 6 KB.
- **Could affect:** the library's top bar, Continue card, chip rows and battery card; the bar, rail and sidebar items
  (a modifier each); the first book tile or row (a modifier); You's first row; What's new (the tour waits for it).
- **Verified in CI:** `GuidedTourTest`, `GuidedTourScreensTest`, `CatalogueScreensTest`, `FoundIndexTest` (the new
  setting's words in all three languages), release journey steps 01 and 24c.

**Real-device checks (the test phone):** install over the old build: What's new shows first, then the tour offer.
Tap Show me around: each stop outlines the right thing and the card does not cover it; rotate on stop 3: it stays on
stop 3; Back goes to stop 2. Turn TalkBack on: each stop reads "Step n of 5" then its words. On You, Show me around
replays it. The Continue card's Play reads on; a tap on the card opens the book. The chip row scrolls to Filters.
