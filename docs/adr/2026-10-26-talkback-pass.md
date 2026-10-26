# ADR 2026-10-26-talkback-pass: what TalkBack says, and a tour that fails when a screen gets it wrong

**Status:** Accepted (2026-10-26). Builds on ADR-036 (one set of sizes) and the TalkBack work in the player.

## Context
The app had TalkBack labels where someone remembered to add them. Nothing checked new screens, sheets or menus, so
gaps came back with every feature. A first audit over the UI tour found, on every language and font size: the
library's Add book button read nothing; every bottom sheet had a handle that read nothing (Material wraps it in a
tooltip whose long press is a control of its own); the speed and pitch sliders said their value but not what they
set; every slider was 44 dp tall, under the 48 dp touch minimum; and the bookmarks sheet and the search screen had no
heading to jump back to. Listening also needs announcements that do not talk over the voice.

## Decision
1. **The UI tour audits TalkBack on every screen it captures**, for every language, font size and dark mode it runs:
   every control (click, long press, switch, slider) has a name, every control that is wholly on screen is at least
   48 dp by its touch bounds (what TalkBack and touch use, not its drawn size), and the topmost window has a heading
   (menus are exempt). Problems fail the tour and go to `talkback.txt` beside the screenshots. The tour also opens the
   reader's sheets (chapters, bookmarks, page text, search, up next) and a book's menu and details.
2. **Two shared fixes in `ui/theme/Accessibility.kt`**, used everywhere instead of per-screen semantics:
   `Modifier.namedSlider(name, value)` (name, value and the 48 dp height) and `SheetDragHandle()` (Material's handle
   with a name), passed as the `dragHandle` of every `ModalBottomSheet`.
3. **Listening announcements stay quiet while the voice speaks.** No live announcement per sentence: the sentence
   being read carries the state "Now reading" for whoever moves to it. A turned page is announced only when the book
   is not being read aloud. The sleep timer is read in whole minutes left, rounded up and never zero, so it changes
   once a minute and not every second.

## Alternatives considered
- **Accessibility Test Framework checks** (Espresso `AccessibilityChecks`): built for Views; with Compose it sees one
  host view and misses most nodes. The audit reads Compose's own semantics tree instead.
- **`dragHandle = null`** on sheets: removes the unnamed node, but also the handle's dismiss and expand actions that
  TalkBack users rely on.
- **Polite live region on the sentence text:** TalkBack would read every sentence over the voice reading it.

## Consequences
- A new screen, sheet or slider without a name or heading fails the PR's UI tour (font scale 2) before merge.
- The audit cannot judge whether a name is good, only that there is one; real-device checks with TalkBack still
  matter.
- The tour still cannot open the chapters sheet on its test book (no chapters) and the player at font scale 2; those
  are covered by their own screen tests.
