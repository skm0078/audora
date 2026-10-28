# The library's search finds actions and settings (2026-10-28, ADR 2026-10-28-action-search)
- Typing in the library's search shows up to five "Actions and settings" rows above the books: "audiobook" offers
  "Save Gora as an audiobook", "dark" the Theme row with "You › Reading and look · now System".
- A setting opens You on its page, scrolled to and tinted, with focus on it; Back returns to the search.
- Hindi and Bengali find rows by their own words and by English ones. None in kids mode.
- Search box hint: "Search books, actions and settings".
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-action-search.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** about 60 short entries in memory per language; each keystroke scans them (well under 1 ms). Nothing runs
  while the box is empty. Three small settings flows are read on the library for the value hints. APK about 6 KB.
- **Not shown (flagged):** a current value for model packs, citations, reconnect and other rows that read their
  state only when You opens; those show where they live without a value.
- **Could affect:** the library's search (books still filter as before), You's page switching and Back, the
  settings rows (each wrapped to be found).
- **Verified in CI:** `ActionSearchTest`, `FoundIndexTest`, `ActionSearchScreensTest` (audiobook row starts the
  export, dark shows Theme with its value above the books, Hindi and Bengali at 200% text, kids mode shows none, a
  found setting opens Advanced focused and tinted), release journey step 24b.

**Real-device checks:** in the library tap search and type "dark": Theme shows with its value; tap it: You opens on
it, tinted; Back: the search is still there. Type "volume": Advanced opens on Volume keys; Back returns to the
search. With TalkBack on, tap a found setting: TalkBack should land on that row. Switch the app to Hindi and type
"थीम", then "dark".
