# ADR 2026-10-28-action-search: the library's search also finds actions and settings

**Status:** Accepted (2026-10-28). Fifth step of the design pass before v0.1.0, after ADR 2026-10-28-tablet-panes.
Follows the search mockup ("Search that finds actions and settings"), which the maintainer approved.

## Context

The design doc asks that search find what the app can do and what can be changed, not only books: "audiobook" should
offer saving the book being read as an audiobook, "dark" the theme. Settings sit on three pages of You, and the
library's tools behind its ⋮, so someone who does not know where a thing lives had no way to ask for it.

## Decision

- **One small list, kept on the phone.** `FoundIndex` makes about 60 entries from the app's own strings: every
  setting on You, Privacy and Advanced (`SettingKey`, which also records its page and group), the places and screens
  (Voices, Storage, Voices & licences, Made, Jobs, Notes, You), the library's ⋮ tools, and the book menu's choices for
  one book. Each entry has its name, other words people type (`found_terms_*`: "dark" for the theme) and the English
  name and words, so English works in Hindi and Bengali too. `ActionSearch` (in `:core`, pure JVM) folds text the way
  the library's book search does and ranks a whole word of the name highest, then a prefix, then the extra words.
- **Above the books, at most five rows.** Under "Actions and settings", each with where it lives ("You › Reading and
  look") and, for the switches and choices the shell already reads, its value ("· now System"). The books follow
  under "Books"; with none, the line says no book title matched.
- **Book actions name a book.** The book is the first one with a title word starting with a typed word ("gora
  audiobook"), else the one read last. Its title only narrows a match: "gora" alone leaves the books to the grid.
  A book whose file is lost offers nothing, as its menu does; Mark as finished shows only while it applies.
- **A tap does what its own button does,** through the same `LibraryActions` call. A setting opens You on its page,
  scrolls its row into view, moves focus to it and tints it for 1.5 s; Back then returns to the search, its words
  still there, even from Privacy or Advanced.
- **Kids mode offers none:** the book menu and You are hidden there already.

## Alternatives

- **A full-text index (SQLite FTS) of every string:** finds help text too, but costs a database and a build step for
  60 short names that a linear scan covers in well under a millisecond.
- **A separate search screen for settings (as on Android's own Settings):** one more place to learn; the library's
  box is where people already type.
- **Current values for every setting:** the model packs, citations and the rest read their state only when You opens.
  Reading them on every library visit for a hint line is not worth it; those rows show where they live without a
  value. Flagged to the maintainer.

## Consequences

- Cost on the device: no new dependency, no asset. The list is made once per language, book named and value change
  (a few KB in memory); each keystroke scans it. Nothing is composed while the search box is empty. The library now
  also collects the keep-screen-on, volume-keys and privacy settings it shows values for (three small DataStore
  flows). APK about 6 KB (strings in three languages).
- `LibraryListState.Loaded.latest`: the book read last, kept while searching. `TabsRoute` moved out of
  `MainActivity` to keep the activity under detekt's size limit.
- Tests: `ActionSearchTest` (core), `FoundIndexTest` (JVM, reads the real strings in all three languages),
  `ActionSearchScreensTest` on the emulator with screenshots, release journey step 24b.
