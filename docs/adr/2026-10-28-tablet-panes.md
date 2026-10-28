# ADR 2026-10-28-tablet-panes: a book pane in the library and a side panel in the reader on wide screens

**Status:** Accepted (2026-10-28). Fourth step of the design pass before v0.1.0, after ADR 2026-10-28-four-places
(bar, rail or sidebar by window width) and ADR 2026-10-28-book-menu-sort-sheet (the one-sheet book menu). Follows the
tablet mockups on the design canvas, which the maintainer approved.

## Context

On a tablet the library stretched its covers across the whole width and every book's menu still rose as a phone
sheet over them. The reader put chapters and bookmarks in sheets that covered the page being read, and the character
guide in a dialog. A wide screen has room to keep these beside the content instead.

## Decision

- **Library book pane.** When the library itself is at least 880 dp wide (the 360 dp pane plus four covers a row:
  a 1280 dp tablet beside its 280 dp sidebar qualifies; a tablet in portrait or a phone in landscape does not), the
  picked book's menu sits in a pane at the end, with Save as already open. It is the same `BookMenuContent` the sheet
  shows, so both always offer the same choices in the same order. A choice acts at once and the pane stays.
- **Picking.** Beside the pane, a first tap on a book picks it (ring on the cover, tinted row in the list, TalkBack
  says "selected"); a tap on the picked book, or on the pane's cover and title, opens it. The ⋮ and a long press
  pick too, rather than open a sheet that would repeat the pane. With nothing picked the pane shows the book being
  read, else the first. A picked book hidden by a filter falls back the same way. Never in kids mode.
- **Reader side panel.** When the window is at least 840 dp wide (`WindowWidths.EXPANDED`) and a book is open, a
  320 dp panel sits at the page's start with tabs: Contents (only with chapters), Notes (bookmarks and voice notes,
  always) and People (the character guide). The page keeps at least 520 dp. Opening a chapter or a note leaves the
  panel up. The top bar's Bookmarks and Chapters buttons go while the panel shows, since it already holds both.
- **Window width, not the reader's own width,** decides the panel, as AppShell decides the rail: a split screen or a
  folded foldable keeps the sheets. `ReaderScreen` takes it as a parameter so tests can force a tablet.

## Alternatives

- **A first tap opens the book, as on a phone:** the pane would only follow the ⋮, and most people would never see
  it. Flagged to the maintainer: a second tap (or the pane's cover) opens, which is one tap more than a phone.
- **A collapse button for the reader panel:** at 840 dp the page keeps 520 dp, wider than a phone's; left out for
  now and flagged, can be added if listeners ask.
- **Material3 adaptive `ListDetailPaneScaffold`:** a new dependency for a two-column row that `Row` and a width
  check already give.

## Consequences

- Cost on the device: no new dependency, no new asset. On a phone nothing new is composed (the library adds one
  `BoxWithConstraints` around its frame to read its width). On a tablet the pane and the panel are composed only
  while shown; the People tab reads the book's guide file again on each page turn while it is the tab in view (a few
  KB on the IO thread), so it never shows people met later than the page.
- Strings: new `strings_tablet_panes.xml` (the three tab names) in English, Hindi and Bengali.
- Tests: `BookPaneTest`, `PanelTabTest` (JVM); `BookPaneScreensTest` and `ReaderSidePanelTest` on the emulator at a
  forced 1280 x 800 dp and at phone size, with screenshots; release journey step 24 (the phone keeps the sheets).
