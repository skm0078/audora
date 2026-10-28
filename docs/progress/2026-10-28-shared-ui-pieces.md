# Section titles and switch rows look the same everywhere (2026-10-28, no ADR)
- From the accessibility and code structure audit (finding S4). Four screens had their own private section title,
  each in a different size or colour (Downloads and jobs: large accent; Edit book: small accent; Sort and show: grey
  label; Studio audio: plain). They now share `ui/theme/SectionTitle.kt`, the look the settings groups already used
  (small accent label, a TalkBack heading). The settings groups use it too.
- Up next and the sleep options dialog had their own switch row. Both now use `SwitchLine` in `ui/theme/SwitchRows.kt`
  (built on the existing `switchRow` modifier: the whole row toggles, at least 48 dp, read as one switch).
- The sleep options' bedtime timer chips (15, 30, 60 min) now wrap onto a second line at large text instead of
  pushing the last one off the dialog.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-shared-ui-pieces.txt`. `:pdf` changed: no. New
  dependency: no. Performance cost: none (fewer composables, same work).
- **Could affect:** Downloads and jobs, Edit book, Sort and show, Studio audio, every settings group title, Up next,
  Sleep options.
- **Verified in CI:** ktlint, detekt, unit tests, the UI tour at 200% text (no broken words), existing screen tests.

**Real-device checks (the test phone):** open Sort and show, Edit book and Downloads: the section titles look alike.
In the player, Sleep timer > options at the largest text size: all three bedtime chips are visible.
