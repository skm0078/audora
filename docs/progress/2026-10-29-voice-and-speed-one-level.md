# Voice and speed, one level deep (2026-10-29, ADR 2026-10-29-voice-and-speed-one-level)
- The Voice and speed sheet's finer groups (What to read, Sound, Cast, Study, Focus sound) open in place under a
  header that says what is set in them, one at a time. No more sheets over the sheet.
- The player's "Speed 1×" button is now a chip: tune icon, speed, and the voice reading under it.
- A shortcut row under the player's buttons: Voices, What to read, Focus sound. Each opens the sheet at its group.
- Sleep timer stays at the right end of the main row. The shortcut row waits until the first-run gesture tips are
  put away (Got it), so the two never squeeze the page together.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-voice-and-speed-one-level.txt`. `:pdf` unchanged. No
  new dependency. New strings: `speech_group_all_off`, `speech_chip_state` (three languages); new icon `ic_tune`
  (Material, Apache-2.0).
- **Cost:** the player's controls are one chip row taller (about 52 dp of screen height). Run time: the chip's voice
  name is remembered per page, voices and picks, so playback ticks do not recompute it; the sheet composes only the
  open group, so it opens no slower than before. A few KB of APK.
- **Could affect:** the player's and reader's control panel height; the speech sheet's layout; screens that found
  the speed button by its "Speed" text (tests updated to its content description).
- **Verified in CI:** `SpeechSpotTest`, `BookVoicesSectionTest`, `SpeechSheetGroupsTest`, `PlayerLayoutTest`,
  `UiTourTest`, release journey step 51.

**Real-device checks (the test phone):** open a book. The chip shows "1×" and the voice's name; tap it: the sheet
opens at its top with every group closed and each header saying what is on. Tap Sound, then Cast: Sound closes as
Cast opens. Back closes the sheet. Tap the Focus sound shortcut: the sheet opens with Focus sound open and in view.
In Hindi and Bengali at the largest text size, the shortcut row scrolls sideways rather than wrapping, and the play
button stays in the middle. TalkBack on the chip says "Voice and speed, Speed 1×, <voice>".
