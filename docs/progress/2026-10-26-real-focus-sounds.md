# Focus sounds are real nature recordings (2026-10-26, ADR 2026-10-26-real-focus-sounds)
- Quality ticket Q-1. The focus sound list is now Off, Rain, Rain in a forest, Forest stream, Babbling brook, River,
  Birds at dawn, Spring birds, Fireplace, Wind and Brown noise. All but brown noise are CC0 field recordings from
  Freesound, bundled as Opus loops (about 2.1 MB, `playback/src/main/assets/focus/`, credits in `CREDITS.md` there).
  Café is gone (it needs people talking); a saved café choice now plays rain.
- The focus sound is mixed after the speed and pitch change (`FocusAfterSpeedChain`), so recordings never stretch.
  Loops decode once off the audio thread (`FocusLoops`), resample once to the voice's rate (`FocusResampler`) and
  fade in when ready.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-real-focus-sounds.txt`. `:pdf` not changed
  (`PIPELINE_VERSION` unchanged). No new dependency.
- **Could affect:** every focus sound, playback speed and pitch, and skip silence (the audio processor chain
  changed).
- **Verified in CI:** `FocusLoopTest` (resampler level, pitch, aliasing, seamless loop; every sound has its loop and
  credit), `FocusAfterSpeedChainTest`, `FocusMixerTest`, `FocusSoundProcessorTest`, `FocusNoiseTest`,
  `FocusSoundOptionsTest` (café reads back as rain), `ReleaseJourneyTest`.

**Real-device checks (the test phone):** in the speech sheet pick each focus sound while reading: it fades in within
a second, has no click where it loops, and dips under the voice. Set speed to 2× and pitch up: the voice changes, the
rain does not. Switch sounds quickly a few times; nothing stutters.
