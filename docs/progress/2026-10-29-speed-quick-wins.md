# Speed quick wins (2026-10-29, ADR 2026-10-29-speed-quick-wins)
- The voice a book will be read with loads about a second after the book opens, when nothing is playing, so the first
  Play no longer waits for the model (`ReaderVoiceWarmUp`, `VoiceWarmUpCommand`, `VoiceWarmUp`).
- The first screen is drawn once, in the saved look: the splash stays until the look is read, at most 300 ms
  (`FirstFrame.kt`, `StartupConfig`).
- Page turns: page bitmaps start their copy to the graphics chip as soon as they are drawn; `CurlFrame` reuses its
  outlines instead of making four new ones every frame.
- Library rows redraw only when their own book changes (`app/compose-stability.conf`: `Book`, `BookId`,
  `BookProgress`).
- Resource shrinking on for release and dev builds. What's new entry `2026-10-29-speed-quick-wins`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). No new dependency.
- **Could affect:** opening a book and its first Play, the first screen at launch (look, dark mode), PDF and text page
  turns, the library list, any resource only reached by name (none found).
- **Verified in CI:** `VoiceWarmUpTest` (book voice first, phone voices not loaded, failures never thrown),
  `ReaderVoiceWarmUpTest` and `ReaderVoiceWarmUpModelTest` (once, after the wait, never while playing, not after
  close, a late player connection), the existing reader, curl
  and library tests, release journey step 56, the APK size gate (the saving shows on the run's summary page).

**Cost:** see the ADR's "Performance cost". In short: the voice's memory a second after a book opens instead of at
Play; nothing more at cold start; no new dependency.

**Real-device checks (the test phone):** open a book, wait two seconds, press Play: the first words should come
noticeably sooner than before; Share logs show `Warmed voice=` then the first `Audora.RenderSpeed` line without the
load. Launch the app in dark mode with the forest green look: no flash of the default look. Curl a PDF page right
after it was drawn. Scroll a big library while it checks files.
