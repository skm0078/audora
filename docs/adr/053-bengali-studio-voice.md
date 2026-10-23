# ADR-053: Bengali default voice: SYSPIN studio VITS, with a per-voice thread count

**Status:** Accepted (2026-10-23). It replaces ADR-030's provisional Bengali default. The choice of the male or
female SYSPIN voice stays open until the maintainer has listened to both. Changing it is a one-line change to
`recommended` in `speech/models.json`.

## Context
On the dev APK the maintainer rated both downloadable Bengali voices, Piper google and MMS, as unusable. Their
complaints were poor clarity and pacing that does not follow the text. That matches the sources. Piper google is a
fine-tune on crowd-sourced phone recordings (OpenSLR 37), and MMS was trained on Bible readings at 16 kHz.

The spike in `research/spikes/indic-voices` (workflow `indic-voices.yml`) compared every Bengali voice we could
legally ship and run offline. It rendered the same texts with sherpa-onnx 1.13.8 at 2 threads and scored
intelligibility with an ASR round trip. Desktop CER over the prose and formal texts:

| Voice | CER | Desktop RTF |
|---|---|---|
| Piper google, speaker 0 (old default) | 13.8% | 0.06 |
| MMS ben | 10.3% | 0.30 |
| Coqui Bangla female / male | 10.1% / 8.1% | 0.34 / 0.38 |
| AI4Bharat Rasa BEN_F / BEN_M, BOOK style | 8.9% / 9.1% | 0.40 |
| **SYSPIN Bengali female / male** | **8.1% / 5.1%** | 0.39 |

SYSPIN's voices are Coqui VITS models trained by IISc's SYSPIN project on about 30 hours of studio speech per
voice. The model cards give an MIT licence. They take Bengali characters directly, so espeak-ng is not involved,
and their token set includes the danda, so sentence ends are spoken as ends.

Speed is the catch. Their RTF scales almost linearly with threads: 0.71 at 1 thread, 0.39 at 2 and 0.22 at 4.
S1 measured the Moto at roughly 2 to 3 times the desktop RTF. At the app's 2 threads that puts these voices near
real time (the same tier as MMS); with 4 threads they have headroom. Dynamic int8 quantization was tried and
dropped: it shrank the model to 38 MB but made it about 3.6 times slower (ONNX Runtime's int8 convolutions).

## Decision
1. Add `syspin-bn-male` and `syspin-bn-female` to the catalog (114 MB each). The source zips are our own
   conversions on the `indic-voices` pre-release, pinned by sha256 through `source.url`, as ADR-030 did for the
   Bengali zips from S1.
2. Bengali's recommended voice becomes `syspin-bn-male`, the clearest by the round trip. Piper google and MMS stay
   available as downloads.
3. Add an optional per-voice `threads` field (catalog → `voice.properties` → `SherpaVoiceSpec.threads`). When it is
   absent, `SpeechConfig.sherpaThreads` (2) applies. The two SYSPIN voices set 4, which matches the Moto's four
   big cores. Every other voice keeps 2.
4. Rasa is not shipped as a plain voice: it is no clearer than SYSPIN on plain text. Its value is acted styles,
   and switching style per sentence belongs to the expressive-reading work, which can add it later with its
   `emotion_id`.

## Consequences
- Existing users who picked a Bengali voice keep it. A new Bengali download only becomes the reader when none was
  chosen (ADR-030 §3), so the maintainer picks SYSPIN in Settings → Voices.
- At 4 threads a SYSPIN voice uses more CPU, and so more battery, than Piper while it renders. The render-ahead
  window means it only runs while there is text left to buffer.
- If the Moto still measures too slow, the existing "too slow on this phone" check flags the voice. The fallback
  is Piper google, unchanged.
- Rejected without a trial: IndicF5 (1.3 GB or more, diffusion), Indic Parler-TTS (0.9B), Kokoro and Supertonic
  (no Bengali), AI4Bharat FastPitch (no route into sherpa-onnx).
