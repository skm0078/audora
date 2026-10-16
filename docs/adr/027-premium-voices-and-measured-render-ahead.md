# ADR-027: Premium voices, and a render-ahead window sized from each voice's measured speed

**Status:** Accepted (P4-02, 2026-10-16).

## Context
P4-02 adds the premium voices of Build Spec §3: Kokoro v1.0 for English and Supertonic 3 for Hindi, offered "only
where measured RTF < 0.8". Both are slower than the bundled Piper voice (S1 desktop RTF: Kokoro 1.18–1.27,
Supertonic 0.17; predicted on the Moto: Kokoro 2.5–4, Supertonic 0.35–0.5). The render queue (P1-04, ADR-016)
renders a fixed 3 sentences ahead (`QueueConfig.prefetchUnits`), which is plenty for Piper but not for a voice
that needs half a second or more per second of speech: a 30-second run-on sentence after three short ones leaves
the player waiting. The spec's own words for the fix are "measured-RTF-based" render-ahead (§6).

## Decision
1. **Two more sherpa families** (`SherpaFamily.KOKORO`, `SUPERTONIC`) in `:speech`, with three optional manifest
   keys: `lexicon` (Kokoro's), `engineLang` (Kokoro's `lang` in the model config, Supertonic's `lang` per render)
   and `numSteps` (Supertonic's flow steps per render, 5 as in S1). A Supertonic voice names its `tts.json` as
   `model` and its voice-style file as `voices`; its four ONNX parts and unicode indexer have fixed names
   (`SupertonicParts`). The catalog (`models.json`), the build's catalog task, the mirror script and the voice
   folder manifest carry the new keys. Import of Kokoro/Supertonic folders stays refused (ADR-022).
2. **Catalog entries:** `kokoro-en-heart` (v1.0 int8 multi-language archive, speaker 3 = af_heart, US lexicon only;
   the Chinese lexicons, FSTs and `dict/` are dropped, 149 MB) and `supertonic-hi` (Supertonic 3 int8, speaker 2,
   Hindi, 145 MB). Both zips were built with `build_voice_mirror.py`, loaded with sherpa-onnx 1.13.8 using the exact
   config the app builds, and rendered (desktop, 2 threads: Kokoro RTF 1.38–1.44, Supertonic 0.19).
3. **Speed is measured, not benchmarked:** every sentence the queue renders is timed (`RenderSpeedTracker`). A
   sentence counts only when one voice read it all and the sentence before ended with the same voice, so model
   loads and mixed-script sentences never skew a voice. Per voice, real-time factor and speech rate (characters
   per second of audio) are averaged with a weight of `audio seconds / 120 s` (`VoiceSpeedConfig`) and stored in
   `voice-speeds.properties` next to the voices (`VoiceSpeeds`, saved every 20 renders and when the service stops).
4. **Window in seconds, not sentences** (`MeasuredRenderAhead`): keep `rtf × 30 s × 1.5` of audio rendered ahead
   (the slowest voice among the queued sentences; at least 8 s and the spec's 3 sentences, at most 90 s and 40
   sentences). A sentence's length is estimated from its text and its voice's measured speech rate. Unmeasured
   voices assume RTF 0.5 and 14 characters a second (`RenderAheadConfig`).
5. **Start hold:** at every (re)start the queue is empty. The first sentence is handed out only once the sentences
   already in the window are predicted to render in time (each at `rtf × 1.2`), for at most 15 s
   (`StartHold` in `RenderAheadQueue`). A fast voice is never held; a slow one starts a few seconds later instead
   of pausing after its first sentence.
6. **"Offered only where measured RTF < 0.8":** once a voice has rendered 30 s of audio, Settings → Voices shows
   its speed; a voice at or above 0.8 is marked "Too slow on this phone" and cannot be newly picked. If it is
   already the language's voice it stays (the user chose it) and reads with pauses. Deleting a voice forgets its
   speed.

## Evidence
`RenderAheadSimulationTest` replays the queue's timing over twenty one-hour books (2–12 s sentences with 8 %
run-ons of 18–30 s), ±20 % noise per sentence and a 25 % slowdown one quarter of the time that the policy does
not know about. With the measured window and start hold: no stall at RTF 0.1–0.6; at 0.79 at least 4× less
stalled time than the fixed window. The fixed 3-sentence window stalls at 0.6. The two design choices came from
this simulation: with a 20 s design sentence the window still stalled at RTF 0.4, and without the start hold every
remaining stall was in the first three sentences.

## Alternatives considered
- **Benchmark each voice after download** (render a fixed text): measures a cold engine once, on whatever the phone
  was doing then; and synthesis runs only in `:playback` (AGENTS.md). Measuring real sentences costs nothing and
  follows the phone's state. Rejected.
- **Fixed larger `prefetchUnits` for premium voices:** sentences vary 10× in length; a count is wrong for both short
  and long ones and wastes memory on fast voices. Rejected.
- **Disk cache of rendered audio** (spec §6's 20 MB `cacheDir/tts/`): ADR-016 chose memory; 90 s of 44.1 kHz audio is
  7.6 MiB, so the window fits in memory. Unchanged.
- **Parallel rendering:** sherpa-onnx already uses several threads inside one call, and the engine keeps one model
  loaded; two renders would compete for the same cores. Rejected.
- **Supertonic 3 for English too:** same 145 MB model, but S1 found it garbles numbers until P2-04 part 2 wires the
  normalizer; and the catalog's one-language-per-entry model would download it twice. Left for later.

## Consequences
- Kokoro will very likely measure too slow on the Moto (S1 predicted 2.5–4, desktop 1.4): it is downloadable and
  playable, and the Voices screen says so after 30 s of reading. Supertonic should qualify.
- The window is sized for 1.0× speed; at 1.5× a voice effectively runs 1.5× slower. Not handled yet.
- A mixed English+Hindi book with both premium voices swaps models at each switch (one model loaded); those
  sentences are slower and not measured.
- Supertonic's model licence (OpenRAIL-M) asks redistributors to pass on its use restrictions; the mirror zip
  carries Supertonic's MIT code licence and README, and the in-app licence line names OpenRAIL-M and where to read
  it. The full OpenRAIL-M text should go into the zip and the "Voices & licenses" screen (P3-06).
