# TalkBack can zoom the page and place the page snippet box (2026-10-28, no ADR)
- Two things on the drawn page could only be done with a gesture TalkBack cannot make. Pinch zoom now has **Zoom in**
  and **Zoom out** in the page's TalkBack actions (each offered only while it can still go that way; Fit width still
  resets). The page snippet box, which could only be dragged, now has **Move the box up / down / left / right** and
  **Make the box bigger / smaller**, and TalkBack says how much of the page it covers.
- Each snippet step is `ShareCardConfig.snippetNudgeStep` (5% of the page) and keeps the drag's rules: on the page,
  at least a handle wide, at most a third of the page (`core/share/SnippetNudge.kt`). A zoom step is
  `PageZoom.ACTION_STEP` (1.6x, three steps from width to the 4x limit), about the middle of the screen.
- From the accessibility audit (findings A3).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-talkback-zoom-snippet.txt`. `:pdf` changed: no. New
  dependency: no. Performance cost: none measurable; six action labels per recomposition of the snippet box and two
  for the page, only built while those are on screen.
- **Could affect:** page view zoom, page snippet sharing.
- **Verified in CI:** `SnippetNudgeTest` (core: every step, edges, size limits after 40 steps each way),
  `SnippetPickerTest.talkBackMovesAndResizesTheBoxAStepAtATime` (emulator), existing snippet and reader tests.

**Real-device checks (the test phone):** with TalkBack on, open a PDF, focus the page, open the actions menu (swipe
up then right): Zoom in, Zoom in, Zoom out. Then hold a sentence, Share part of the page, focus the box and use Move
the box down and Make the box bigger; TalkBack says the new share of the page each time.
