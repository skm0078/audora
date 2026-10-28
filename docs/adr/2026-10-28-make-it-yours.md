# ADR 2026-10-28-make-it-yours: a library home the listener arranges, with the book's own words on it

**Status:** Accepted (2026-10-28). Follows ADR 2026-10-28-home-and-tour (the lighter home) and the "Home, yours" and
"Make it yours" boards on the design canvas, which the maintainer approved. First of two personal-touch PRs; the
second adds a shelf's colour and emoji, the remembered voice and the opening line.

## Context

The lighter home is the same for everyone. Listeners asked for the library to feel like theirs: their name in the
greeting, what sits above the books and in what order, and a Continue card that says more than a page number. All of
it has to stay on the phone, work offline, cost little on a mid-range phone and read well with TalkBack.

## Decision

- **"Make it yours" sheet,** from the library's ⋮ menu (last entry, so the other menu rows keep their places) and from
  search. Every change is saved as it is made; there is no Save to forget. It holds:
  - the name the greeting uses ("Good evening, Ria"), up to 24 characters, trimmed, one line;
  - covers or list (the library's own choice, the same control as the sort sheet);
  - the home's parts, each with a switch and one move button (up; down for the top one). Up and down buttons rather
    than drag, so TalkBack and switch access can reorder them. Shelves and collections stay pinned above the books,
    so they can be turned off but not moved;
  - "Colour the Continue card from the cover", on by default.
- **Parts above the books,** only on the whole library (a search, shelf or filter puts the books first, as the Continue
  card always did): Continue listening, Your line of the day (on), Recently added (off by default, keeping the light
  home light). A part with nothing to show is left out.
- **Continue card:** a soft tint from the cover (16 % of its average colour over the card's usual colour; the label
  and bar move toward the text colour until they reach WCAG AA 4.5:1 on it, so a pale or dark cover never leaves
  them unreadable), "14 min left at your speed" when the reader has estimated it, and "Last heard: “…”", two lines
  at most.
- **Where the data comes from:** the playback service writes each started sentence through `ResumeBridge` into one
  `BookGlances`; the reader writes its time-left estimate with the pages left then. The library scales that estimate
  by the pages read since, so a book played on from the library still counts down. Changes stay in memory and the
  file (`book-glances.txt`, a header and one TSV line per book, written durably) is written at most every 30 s and on
  pause, stop or the service going away. It keeps the 60 books read last, forgets removed books, is unreadable-safe
  and is not in the backup: the next listen fills it again.
- **Line of the day:** one of the listener's highlights, from a book still in the library, picked by the day alone
  (sorted by id, then a prime stride), so it does not change while they look and needs nothing stored. A tap opens
  the book at that sentence. Never on the lock screen or in a notification.
- **Home choices** live in their own Preferences DataStore (`library_home`), one line `order|hidden|tint|name`; a line
  from another build keeps the known parts in order and adds new ones at the end. The library waits the few
  milliseconds the read takes, as it already does for covers or list, so parts never jump into place.

## Alternatives

- **A colour picker for the app** (the earlier Customise mockup): left for Premium/cloud, as the maintainer decided.
- **Drag to reorder:** nicer by touch, but needs custom accessibility actions to reach TalkBack; up and down buttons
  give every listener the same way.
- **Last line from the resume anchor:** the anchor holds a fingerprint, not the text, so the line would need the book
  re-read on every library open. A small file written by the service costs far less.
- **Tint the mini player too:** the mockup showed it. Left out of this PR to keep it to the library and flagged to the
  maintainer; `rememberCoverTint` is ready for it, so it is a small follow-up if wanted.
- **A "This week" goal part:** waits for the listening stats PR, which owns the goal.

## Consequences

- Cost on the device: no new dependency or asset (one 1 KB icon). One 1×1 filtered scale of a cover on a background
  thread when the Continue card's book or cover changes (a hardware bitmap is copied once to read it), a map update
  per sentence in memory, a few-KB file write at most every 30 s while playing, one DataStore read on opening the
  library. APK about 15 KB (estimate).
- Strings: new `strings_make_it_yours.xml` in English, Hindi and Bengali.
- Tests: `HomeLayoutTest`, `LineOfTheDayTest`, `BookGlancesTest` (JVM, `:core`); `LibraryHomeTest` (JVM: parts in
  order, search puts books first, line of the day only for kept books, tint contrast in both themes);
  `MakeItYoursScreenTest` on the emulator; release journey step 44.
