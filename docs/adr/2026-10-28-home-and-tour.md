# ADR 2026-10-28-home-and-tour: a lighter library home and a replayable 1-minute tour

**Status:** Accepted (2026-10-28). Sixth step of the design pass before v0.1.0, after ADR 2026-10-28-action-search.
Follows the home and tour mockups on the design canvas (row 7), which the maintainer approved.

## Context

The library opened on two rows of chips, a tall Continue reading card with its own Continue button and, for many
phones, a four-line battery card, so a phone showed few covers above the fold. Nothing showed a new listener where
the book menu, Notes, Made and You were; they found them by chance or not at all.

## Decision

- **Greeting.** "Good morning / afternoon / evening" over the Library title, by the hour the library opened (5-12,
  12-17, otherwise), in English, Hindi and Bengali. Hidden while searching, as the title is.
- **Compact Continue card.** One row: a small cover, "Continue reading", the title (two lines at most), the progress
  line and a round 52 dp Play. A tap on the card opens the book; Play reads on. About half the old card's height.
- **One chip row.** The shelves (with their counts), a thin rule, then Filters, the collections and New or Edit, in
  one sideways-scrolling row instead of two.
- **Battery banner.** The setup card becomes one line: what it is for, Set up, and an X (Not now). The longer
  explanation lives on the setup screen it opens.
- **The tour.** Five stops, each dimming the screen around one thing with an outline and a card beside it: Play on
  the Continue card (or the first book when nothing has been read), the first book (press and hold for its menu),
  then the Notes, Made and You places in the bar, rail or sidebar. Each card says "Step n of 5" (read out by
  TalkBack when the stop changes), with Skip and Next, and Done on the last. Back steps back; taps on the dimmed
  screen do nothing, so nothing opens by mistake. The stop survives a rotation.
- **Offered once.** As a sheet ("Your first book is in. Want a 1-minute tour?", Maybe later / Show me around) the first
  time the library holds a book, out of kids mode, after What's new is closed. Any answer, including a swipe down,
  marks it offered for good. Testers who already have books see it once too.
- **Replay.** You's first row, "Show me around", starts the tour on the library. Search finds it ("tour", "help").

## Alternatives

- **A coach-mark library** (TapTargetView, Showcase): a dependency and View-based overlays for five stops that one
  Canvas and a custom `Layout` draw; and none reads "Step n of 5" to TalkBack.
- **A static carousel of screenshots:** cheaper, but shows pictures of the app rather than the app itself, and goes
  stale with every UI change.
- **Offering it inside the first-run screens:** the library is empty there, so nothing could be pointed at.

## Consequences

- Cost on the device: no new dependency, no asset beyond strings. While the tour is not showing, five small modifier
  nodes report their bounds to a state map on layout, and write only when the bounds move (the first book's tile while
  the grid scrolls). While a stop shows, one offscreen layer draws the dimmed screen. The saved flag is one boolean
  read once off the main thread. APK about 6 KB (strings in three languages, code).
- Flagged to the maintainer, against the mockup: the chips keep their counts; "18 min left" on the Continue card
  waits for personal touch 13 (time left needs the listener's speed and voice rate); the greeting has no name until
  personal touch 1 asks for one; the library's ⋮ menu stays; the battery card's second sentence moves to its setup
  screen.
- Tests: `GuidedTourTest` (JVM: when it is offered, the stops' order and targets, Back, the greeting by hour);
  `GuidedTourScreensTest` on the emulator (offer starts it, card sits under Play, Back, Done, Bengali at 1.5x, with
  screenshots); `CatalogueScreensTest` on the merged chip row; release journey steps 01 (the offer after first run)
  and 24c (replay from You walks the five stops).
