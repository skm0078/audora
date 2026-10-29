# Voice commands rows no longer overlap; repeated buttons say what they act on (2026-10-28, no ADR)
- From the accessibility and code structure audit (findings A1 and A5).
- **Bug fixed:** in You > Advanced, the voice commands model row and the "Listen for voice commands" switch were drawn
  on top of each other, their texts unreadable. `SettingAnchor` (the wrapper search uses to find a setting) held its
  rows in a `Box`; voice commands puts three rows in one anchor. It is a `Column` now. Came in with search for
  actions and settings (#89).
- **TalkBack:** buttons that repeat down a list now say what they act on, through `ui/theme/NamedButtons.kt`
  (`buttonNamedFor`, string `a11y_named_button` in en/hi/bn): Download, Retry, Cancel and Delete on each model pack
  ("Download: Pauses in unpunctuated scans (English)"); Download, Update, Cancel and Delete on each voice; Download and Cancel on each language pack ("Download: Hindi"); Licence,
  Source and Licence files on the Licences page. The buttons still show only their word.
- **Wider screen tour audit** (`ui/tour/TalkBackAudit.kt`), per screen and window:
  - controls that all read the same ("Download", "Download");
  - two controls where one covers the middle of the other, neither inside the other (it found the bug above);
  - a sheet's scrim (Close sheet, filling its window) is left out of both.
- `LampContrastTest` checks text on every card, sheet and dialog surface too, in the forest green day palette, the
  lamp at night and true black. All pass; no colour changed.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-wider-a11y-audit-fixes.txt`. `:pdf` changed: no. New
  dependency: no. Performance cost: none (one Column instead of a Box; a content description per button).
- **Could affect:** You > Advanced and Listening (model pack rows), Voices, Licences, Up next's Clear (its button can
  now take a modifier; unchanged here).
- **Verified in CI:** unit tests (contrast), `SettingAnchorTest` (the rows stack; Download is named), the UI tour on
  this branch (no look-alike or overlapping controls left).

**Real-device checks (the test phone):** You > Advanced: the voice commands model row, then the switch under it, each
readable. With TalkBack, swipe through Voices: each Download names its voice.
