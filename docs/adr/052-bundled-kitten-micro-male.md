# ADR-052: The built-in English voice is KittenTTS micro, read by a male speaker

**Status:** Accepted (2026-10-22). Supersedes ADR-014's provisional choice of Piper en_US ljspeech.

## Context
ADR-014 bundled Piper ljspeech until a blind rating on the phone. The maintainer tested the dev build and asked for a
male, friendly voice instead of the one female voice that reads by default, and found the downloads too slow or odd
on the test phone. Every Piper English male voice we could bundle is fine-tuned from Lessac, which the catalog rules out.

## Decision
1. **Bundle `kitten-micro-en-v0_8` (sherpa-onnx's packaging of KittenTTS v0.8 micro, Apache-2.0)** as
   `kitten-en-micro`, read by speaker 2 (`expr-voice-3-m`, "Bruno"), the deepest of its four male speakers.
2. **Its other seven speakers are voices of their own** on the Voices screen (Jasper, Hugo and Leo male; Bella, Luna,
   Rosie and Kiki female), chosen as `kitten-en-micro#<speaker>` ([CastSpeakers.variant]): no download and no extra
   memory, since the engine loads the model once and picks the speaker per sentence.
3. **The same model supplies character, quote and heading voices** with nothing to download (CastSpeakers keys the
   micro archive with the nano's speaker table; the model's own metadata lists the same eight speakers).
4. **Expressiveness shows only while a Piper or MMS voice reads**: Kitten has no noise values, so the slider would do
   nothing.

## Measurements (desktop, 4 threads, the same 13 s passage)
| Voice | Real-time factor | Median pitch |
|---|---|---|
| Piper ljspeech (old default) | 0.06 | 218 Hz |
| Kitten nano int8 | 0.23-0.34 | speaker 2: 108 Hz |
| **Kitten micro (new default)** | **0.20-0.26** | **speaker 2: 104 Hz** |
| Kitten mini | 0.48-0.55 | speaker 2: 110 Hz |
| Kokoro Michael (download) | 1.06 | 115 Hz |

## Alternatives considered
- **Keep ljspeech and promote a male download.** It leaves the voice the maintainer rejected as the first one heard, and
  downloads were what failed on the phone.
- **Kitten nano int8 (28 MB) bundled.** Smaller and about as fast, but the micro model sounds fuller for the same
  speakers.
- **Kitten mini.** About twice micro's render time, for a smaller step in quality.
- **Kokoro.** The most natural voice, but too slow to bundle as the default (about real time on four desktop cores).

## Consequences
- The APK shrinks by about 23 MB (44 MB archive instead of 67 MB; the espeak-ng data is identical).
- Rendering costs about four times Piper's, still about four times faster than speech on four desktop cores; the
  render-ahead queue and the speed check on the Voices screen cover a slower phone.
- A saved choice of `piper-en-ljspeech` no longer resolves and falls back to the default voice.
- Kokoro remains the premium download; it renders at about real time on the desktop, so it may lag on the phone.
