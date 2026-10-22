# Indic voice comparison (Bengali and Hindi)

Why: on the dev APK the user rated both Bengali voices (Piper google, Meta MMS) as unusable: unclear, with pacing
that ignores the text. This spike finds the best offline Bengali (and Hindi) voice we can legally ship and run on
the Moto Edge 50 Fusion. It extends P3-06 (ADR-030); the pick lands through speech/models.json's `recommended` map.

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
