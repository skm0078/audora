# Quiz lines respect calls, sleep options survive a turn, name limits keep whole letters (2026-10-29, no ADR)
- Edge-case pass, gaps E4, E7 and E8.
- E4: `QuizSpeaker` (Words, the quiz, voice samples) asked for audio focus but ignored the answer and never listened
  for its loss, so a line played over a call and kept playing. It now plays only when focus is granted, and stops when
  focus is lost (`QuizFocus.stopsOn`: a loss stops, a duck does not).
- E7: shelf, collection, quote-card and voice-label limits cut by UTF-16 units, so an emoji or a Bengali or Hindi
  conjunct at the limit became a broken box. They now cut with `TextCuts.take`, as the character guide already did;
  `CollectionNames.clean` too.
- E8: the sleep options dialog closed on a rotation and its draft was lost. Both are now saved instance state (the
  draft as `SleepOptions.encode`'s line).
- E6 (sync files with no size cap) was already handled: another phone's file over `maxRemoteFileBytes` is skipped
  when its provider reports a size. No change.
- Perf cost: none. One focus request per line, as before; the text cut runs only while typing at the limit.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-small-edge-cases.txt`. `:pdf` unchanged. No new
  dependency.
- **Could affect:** Words and quiz lines, voice samples in the voice list, the reading tutor's spoken lines; shelf,
  collection, card and voice-label text boxes; the sleep timer's options.
- **Verified in CI:** `QuizFocusTest`, `CollectionNamesTest` (emoji and conjunct at the limit, trim, blank), device test
  `SleepOptionsDialogTest` (an unsaved switch survives a saved-state restore); the full release journey.

**Real-device checks (the test phone):** during a phone call, open a book's Words and tap a word: nothing plays over
the call. Play a voice sample and take a call: it stops. Open the sleep options, switch Fade off, turn the phone: the
dialog stays open with Fade off. Type a shelf name of 60 letters ending in an emoji: the emoji is left out whole.
