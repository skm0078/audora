# ADR 2026-10-29-personal-touches-2: the second set of personal touches

**Status:** Accepted (2026-10-29). Follows ADR 2026-10-28-make-it-yours; built from the "Personal touches, part 2"
boards on the design canvas (row at y=13300), which the maintainer approved.

## Context

The design doc's touches table left seven touches after "Make it yours": a shelf's colour and emoji (9), the book
remembering its voice (8), the opening line (14), words looked up (16), smart shelves (17), "Previously on" (19) and
the home's "This week" (7, which waited for the stats screen's goal, #95). Each is small; together they make the app
feel like the listener's own. Every one stays on the phone, works offline and adds no dependency.

## Decision

- **A shelf's colour and emoji (9).** Six fixed colours (Clay, Saffron, Forest, Teal, Indigo, Plum; each 3:1 on both
  themes) and five emoji or none, chosen on an Edit page inside the Collections sheet. Kept in `shelf-looks.txt` (in
  the backup). One `ShelfBadge` draws it on the chips, in the sheet, the big-tablet sidebar and the phone drawer. A
  colour picker stays a Premium idea.
- **The book remembers its voice (8).** The book menu says which voice and speed this book keeps ("Use for this book
  only" in the reader), with Change opening the book.
- **Previously on (19).** Opening a book after 3 days or more shows a small card: "Previously, N days ago" and the two
  sentences before the place, with Hear them again and Go on. It reads only the place's own page (already read for
  the resume), so it costs no extra page load; a place at the top of a page shows the plain "Picked up" line instead.
- **The opening line (14).** A book's very first open (never opened, no place saved, nothing playing) shows its first
  line large over the page, in its script's font, for 6 s (longer with the system's accessibility timeout). It takes
  no touches and blocks nothing: the page and Play work under it.
- **Words you looked up (16).** Each word looked up in a book is kept with its first meaning and its sentence, at most
  200 a book, in `looked-up-words.txt` (in the backup; forgotten with the book). The book menu lists them and offers
  "Quiz me on 5 words": pick the meaning from four, the voice says the word and then its sentence. The words are book
  text: never logged.
- **Shelves you might like (17).** Under the Collections sheet's list: up to five suggestions from the books' own
  details (a series by title number, an author with 2 books or more, a language with 3 or more that not every book
  has), each made with one tap and never by itself. A name already taken or a group one collection already holds is
  not offered.
- **This week (7).** A new home part, first and on by default, also listed in Make it yours: the week's listening in
  the stats screen's goal ring, "3 h 10 min of 5 h" and the streak; a tap opens Your listening. It stays away until
  something was ever listened, so a new listener's home is as light as before. A home saved before this update gets
  it at the end of its order, shown.

## Alternatives

- **Shelf suggestions in the ⋮ menu or the side panel** (as the board drew): placed in the Collections sheet instead,
  where collections are made, so no new entry point or sheet stacks on another. Flagged to the maintainer.
- **"Previously on" from the pages before the place:** needs a second page load on open; kept to one page.
- **A modal opening-line card with a Begin button:** blocks the reader and every test of it; the overlay does not.
- **Reading "This week" from the stats screen's full computation:** works out all four periods, the calendar and
  milestones; the home needs two numbers, computed the same way (`WeekGlance`, tested to match).

## Consequences

- Two new small files in the app's storage and the backup's allow-list; a foreign file is set aside, never
  overwritten.
- `HomeSection` gains `THIS_WEEK`; old saved homes decode with it added at the end.
- Shelf dots in the sidebar grow from 10 dp to 12 dp to match the chips.
