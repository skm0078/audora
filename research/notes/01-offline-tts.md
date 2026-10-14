# Research: offline TTS for English, Hindi, Bengali (2026-10-14)

Source: research pass for Build Spec v0.2. Items marked *unverified* go to spike S1.

## sherpa-onnx
- Latest **v1.13.8 (2026-09-10)**, onnxruntime 1.28.2.
- Families: VITS (Piper, Coqui, Mimic3, MMS, MeloTTS), Matcha, Kokoro (en v0.19, multi-lang v1.0/v1.1, v1.1 int8), KittenTTS (nano/micro/mini up to v0.8), PocketTTS (Kyutai), Supertonic (incl. Supertonic 3), ZipVoice, Inflect, Nabra.
- 16 KB page size: changelog claims support since 1.12.10; **not verified on 1.13.x binaries** (check with `zipalign -c -P 16` / APK Analyzer).
- Official **TTS-engine APKs** (v1.13.8, arm64-v8a) exist for many voices incl. Hindi Piper (priyamvada, rohan, pratham), Supertonic-3 Hindi, and Bengali (mimic3 bn multi_low, coqui bn custom_female): fastest way to listen on a real phone with zero code. https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html

## Kokoro-82M
- en v0.19 (330 MB), multi-lang v1.0 (310 MB), v1.1 (103 speakers, fp32 + int8). sherpa-onnx wires up English and Chinese only. Needs espeak-ng. int8 size not published (~100–150 MB estimate).
- Hindi voices all graded **C** by the author.
- RTF (VoxSherpa figures): flagship ~0.33–0.67, mid-range ~1.0–1.5, budget ~2–3. Piper on same phones: 0.08 / 0.17 / 0.33. **Kokoro is not real-time on mid/low phones.**

## New small English models
| Model | Size | License | espeak-ng | Notes |
|---|---|---|---|---|
| KittenTTS v0.8 nano | ~15M params, ~32 MB fp16 | Apache-2.0 | Yes | RTF ~0.3 on Pi 5 (ORT). Strong Piper alternative. |
| Supertonic 3 | ~99M params | OpenRAIL-M (code MIT) | No | 31 languages incl. **Hindi, no Bengali**; 24 kHz |
| Kyutai Pocket TTS | 100M | CC-BY-4.0, gated | ? | no phone numbers |
| NeuTTS Air/Nano | 0.4–1.8 GB | other | Yes | too heavy |

## Hindi
- Official Piper hi_IN pratham / priyamvada / rohan (medium, 22.05 kHz), card says MIT, trained on IITM IndicTTS data (dataset terms unread). Prebuilt in sherpa-onnx.
- supertonic-3-hi prebuilt in sherpa-onnx.
- AI4Bharat Indic-TTS: no ONNX export. IndicF5 (1.3 GB+), Indic Parler-TTS ONNX (~5 GB): too heavy.

## Bengali
- **Official Piper `bn/bn_BD/google/medium`**: 16 speakers, bn_BD + bn_IN, 82 MB, OpenSLR 37 (CC BY-SA 4.0) + CMU Indic. Not prebuilt in sherpa-onnx; standard Piper conversion should work *(unverified)*. Replaces the v0.1 third-party bn_BD candidate.
- MMS-TTS ben: 36M params, CC-BY-NC 4.0, 16 kHz, characters frontend (no espeak).
- sherpa prebuilt mimic3 bn multi_low, coqui bn custom_female: licenses unverified.
- SYSPIN vits_Bengali_Female (MIT, Coqui .pth, needs ONNX export).

## Google system TTS
- Offline packs exist for hi-IN and bn-IN/bn-BD; some voices network-only (`isNetworkConnectionRequired`, `KEY_FEATURE_NOT_INSTALLED`). Input capped by `getMaxSpeechInputLength()` (~4000). Needs `<queries>` for TTS_SERVICE on API 30+. OEM default engines (Samsung) vary *(device test)*.

## espeak-ng (GPL-3.0)
- Needed by: Piper (all languages), Kokoro, KittenTTS. Not needed by: MMS, Supertonic.
- Default sherpa-onnx build links espeak-ng, so the APK is effectively GPL-3.0 *(confirm)*.

## Spike S1 must answer
1. RTF on budget + mid-range arm64 phone: Piper medium, Kitten v0.8, Supertonic-3, Kokoro v1.1 int8.
2. 16 KB alignment of 1.13.8 .so files.
3. Exact sizes of Kokoro v1.1 int8 and Supertonic-3 int8.
4. Piper bn_BD conversion; espeak-ng Hindi/Bengali pronunciation (conjuncts, numbers).
5. Licenses: Supertonic OpenRAIL-M use restrictions, IITM data terms, Coqui/Mimic3 bn.
6. Google hi-IN / bn-IN offline + synthesizeToFile on Samsung and Xiaomi.
7. Blind listening: Piper hi vs Supertonic-hi; Piper bn vs MMS-ben vs Google bn.

## Links
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- https://github.com/k2-fsa/sherpa-onnx/blob/master/CHANGELOG.md
- https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html
- https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md
- https://github.com/CodeBySonu95/VoxSherpa-TTS
- https://huggingface.co/litert-community/kitten-tts-nano-0.8
- https://huggingface.co/Supertone/supertonic-3
- https://k2-fsa.github.io/sherpa/onnx/tts/all/Hindi/index.html
- https://huggingface.co/rhasspy/piper-voices/blob/main/hi/hi_IN/rohan/medium/MODEL_CARD
- https://huggingface.co/rhasspy/piper-voices/blob/main/bn/bn_BD/google/medium/MODEL_CARD
- https://huggingface.co/facebook/mms-tts-ben
- https://huggingface.co/SYSPIN/vits_Bengali_Female
