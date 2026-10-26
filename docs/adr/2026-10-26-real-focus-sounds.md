# ADR 2026-10-26-real-focus-sounds: focus sounds are real nature recordings, mixed after the speed change

**Status:** Accepted (2026-10-26). Replaces the generated rain and café of plan item 72; brown noise stays generated.

## Context
Quality ticket Q-1: the rain and café made on the phone from filtered noise sounded fake. Ambient apps that sound
good (Ambie, Blanket) play field recordings. The focus sound was also mixed before Media3's Sonic processor, so the
player's speed and pitch (ADR-067) would stretch and re-pitch any recording.

## Decision
1. **Nine CC0 nature recordings ship in the APK** (`playback/src/main/assets/focus/`, about 2.1 MB of Opus): rain,
   rain in a forest, forest stream, babbling brook, river, birds at dawn, spring birds, fireplace and wind. Each
   licence was read on its own Freesound page (Creative Commons 0, so commercial use needs no permission or
   credit); `CREDITS.md` there still names every recordist. Bundled, not downloaded: the sounds work offline from
   the first launch, and 2 MB does not justify a download step.
2. **Only sounds with no doubt about them:** recordings with people's voices in them (a café, a busy beach) were
   left out, so no one's voice ships in the app. Café goes; a saved café choice reads back as rain
   (`FocusSoundOptions.decode`). Brown noise stays generated: it is noise by definition, so a recording sounds the
   same.
3. **Mixed after the speed change:** `FocusAfterSpeedChain` wraps Media3's `DefaultAudioProcessorChain` and runs the
   focus processor after Sonic, so the voice's speed and pitch never touch the recording.
4. **Decoded once, off the audio thread:** `FocusLoops` decodes the picked loop with `MediaExtractor` and
   `MediaCodec` on its own thread, mixes it to mono and resamples it once to the voice's rate with a windowed-sinc
   filter (`FocusResampler`, circular so the seam stays seamless). Until it is ready the sound is silent, then fades
   in (`FocusSoundConfig.loopFadeInMs`). A loop that fails to decode is logged and stays silent; nothing else plays
   in its place. Only one decoded loop is kept in memory.

## Alternatives considered
- Keep improving the generated sounds: tried before Q-1; filtered noise does not sound like rain or a café.
- Download the sounds on first use: adds a network step and a failure mode for 2 MB.
- Recordings under CC BY: usable with credit, but CC0 alone leaves nothing to get wrong in either app.

## Consequences
- APK grows by about 2.1 MB.
- The licences screen lists voices only; CC0 asks for no credit, so the sound credits live in `CREDITS.md`.
- Loops shorter than 20 s (river, spring birds, fireplace, wind, brook) repeat often; judged by ear before shipping.
