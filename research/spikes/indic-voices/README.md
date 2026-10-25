# Indic voice comparison (Bengali and Hindi)

Why: on the dev APK the user rated both Bengali voices (Piper google, Meta MMS) as unusable: unclear, with pacing
that ignores the text. This spike finds the best offline Bengali (and Hindi) voice we can legally ship and run on
the test phone. It extends P3-06 (ADR-030); the pick lands through speech/models.json's `recommended` map.

How: `.github/workflows/indic-voices.yml` converts the candidates sherpa-onnx does not ship (Hugging Face is only
reachable from CI), renders the same texts with every voice through `bench_desktop.py` (S1's bench, same
sherpa-onnx 1.13.8 as the app, 2 threads), scores intelligibility by transcribing each sample back with the
omnilingual ASR (CER: lower is clearer) and publishes MP3 samples, results and the converted model zips to the
`indic-voices` pre-release. Desktop RTF ranks voices; the phone is roughly 2 to 3 times slower (S1).

## Candidates

| Voice | Lang | Architecture, data | Licence | Notes |
|---|---|---|---|---|
| Piper google (today's default) | bn | VITS fine-tune on crowd-sourced OpenSLR 37 | CC BY-SA 4.0 | 16 speakers, phonemes via espeak-ng |
| Meta MMS ben | bn | VITS on Bible readings | CC BY-NC 4.0 | characters, 16 kHz |
| Coqui Bangla female / male | bn | Coqui VITS, @mobassir94 | Apache 2.0 (Coqui model list) | characters; female prebuilt by sherpa-onnx |
| SYSPIN Bengali female / male | bn | Coqui VITS, IISc SYSPIN studio corpus (about 30 h per voice) | MIT (model card) | characters |
| AI4Bharat Rasa BEN_F / BEN_M | bn | VITS, 40M params, 13 languages, acted styles (BOOK, CONV, SAD...) | CC BY 4.0 | sherpa-onnx export by MatiasLin; style = `emotion_id` in GenerationConfig.extra. Emotion switching per sentence belongs to the "Expressive reading research" thread |
| Piper pratham / priyamvada / rohan | hi | Piper VITS on the AI4Bharat Indic corpus | CC BY-NC-SA 4.0 | |
| Supertonic 3, speaker 2 | hi | flow matching, 99M params | OpenRAIL-M | premium voice |
| SYSPIN Hindi female / male | hi | Coqui VITS, IISc studio corpus | MIT (model card) | characters |

Ruled out without a trial: IndicF5 (1.3 GB+, diffusion: far from real time on a phone CPU), Indic Parler-TTS
(0.9B parameters), Kokoro and Supertonic (no Bengali), AI4Bharat Indic-TTS FastPitch (no ONNX path into
sherpa-onnx).

## Local pre-run (agent container, x86, 2 threads, 2026-10-22)

CER is the mean over the prose and formal texts; lower is clearer.

| Voice | RTF | CER |
|---|---|---|
| Piper google speaker 0 (default) | 0.07 | 15.8% |
| Piper google, best of 16 speakers (4, 15) | 0.07 | 11.2% |
| MMS ben | 0.31 | 11.4% |
| Coqui Bangla female | 0.36 | 10.6% |
| Coqui Bangla male | 0.39 | 7.1% |

## CI run (GitHub runner, 2 threads, 2026-10-23)

CER is over the prose and formal texts; numbers and abbreviations are not in this set (the app's normalizer
handles those before the voice). Samples: `indic-voices` release, one MP3 per voice.

| Voice | Lang | MB | RTF | CER |
|---|---|---|---|---|
| Piper google, speaker 0 | bn | 95 | 0.059 | 13.8% |
| MMS ben | bn | 114 | 0.299 | 10.3% |
| Coqui Bangla female | bn | 114 | 0.335 | 10.1% |
| Coqui Bangla male | bn | 114 | 0.376 | 8.1% |
| **SYSPIN Bengali female** | bn | 114 | 0.393 | 8.1% |
| **SYSPIN Bengali male** | bn | 114 | 0.389 | **5.1%** |
| Rasa BEN_F, BOOK | bn | 123 | 0.403 | 8.9% |
| Rasa BEN_M, BOOK | bn | 123 | 0.396 | 9.1% |
| Rasa BEN_F, CONV / SAD | bn | 123 | 0.39 / 0.41 | 8.9% / 8.3% (some texts unscored) |
| Piper pratham | hi | 81 | 0.051 | 4.5% |
| Piper priyamvada | hi | 81 | 0.048 | 9.7% |
| Piper rohan (comparison only: Lessac-derived, never shipped) | hi | 81 | 0.055 | 5.1% |
| Supertonic 3, speaker 2 | hi | 145 | 0.128 | 4.7% |
| SYSPIN Hindi female | hi | 114 | 0.372 | 15.7% |
| SYSPIN Hindi male | hi | 114 | 0.356 | 9.3% |

Outcome (ADR-053): SYSPIN Bengali male becomes the recommended Bengali voice, female is offered too; both load with
4 threads (local: RTF 0.71 / 0.39 / 0.22 at 1 / 2 / 4 threads). Dynamic int8 was tried and dropped (38 MB but RTF
1.44). Hindi stays on pratham: SYSPIN Hindi is less clear here.
