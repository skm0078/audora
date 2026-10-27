# Speed up to 4x (2026-10-26, ADR 2026-10-26-speed-up-to-4x)
- The speed slider now goes from 0.5x to 4x (was 3x), for listeners used to fast speech. Above 3x smart speed always
  carries the extra speed, sparing consonants, because the player's even stretch loses words there. The quick picks
  and the default speed are unchanged. CF-6.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-speed-up-to-4x.txt`. No `:pdf` change. No new
  dependency.
- **Could affect:** speed slider and +/- buttons, smart speed switch, "What did it say?" replay at high speed,
  render-ahead at high speed, Quiz me read-aloud speed.
- **Verified in CI:** `SpeechStyleConfigTest` (4x offered, forced smart speed above 3x, model × voice × player is
  always the asked speed up to 4x, a replay of a forced 4x keeps smart speed), `ShapedRenderTest` (a 4x render with
  the switch off is shortened by the voice's stretch), `ReleaseJourneyTest`.

**Real-device checks (the test phone):** drag the speed to 4x with smart speed off and listen to a page of English,
then Hindi and Bengali: clear words, no gaps between sentences after the first few seconds. Flip smart speed at 4x:
nothing restarts. "What did it say?" at 4x replays slower without a pitch jump. Share logs after a page at 4x: the
`Audora.RenderSpeed` lines show whether the phone keeps up.
