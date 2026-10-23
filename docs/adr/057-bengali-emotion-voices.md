# ADR-057: Bengali emotion voices (AI4Bharat Rasa)

**Status:** Accepted (2026-10-23). Expressive reading Stage 2, on top of ADR-056.

## Context
ADR-056 gives each story sentence a feeling (loud, soft, sad, lively, trailing, asking) but can only show it through
pace, pauses and loudness: the voice itself always sounds the same. AI4Bharat's Rasa (`vits_rasa_13`, CC BY 4.0) is
one VITS model with 1024 speaker slots and 32 speaking styles, among them BOOK, HAPPY, SAD, ANGER, FEAR, SURPRISE and
CONV, with a Bengali female and male speaker. MatiasLin's sherpa-onnx export adds the style as an `emotion_id` input,
which sherpa-onnx 1.13.8 (already in the app) reads from `GenerationConfig.extra`. Rasa in BOOK style was trialled
for ADR-053 at about 9 % CER, behind SYSPIN male (5.1 %), so it is not the default Bengali voice.

## Decision
1. **One opt-in download.** `rasa-bn` in `speech/models.json`: 123 MB, the narrator is the Bengali female speaker
   (2), threads 4. The source zip is published once by `.github/workflows/rasa-source.yml` from a pinned commit of
   the export and pinned by SHA-256 like the SYSPIN zips. SYSPIN male stays the recommended Bengali pack. Rasa is
   shown in the Play build (CC BY 4.0 allows commercial use with credit; the credit is in the catalog entry).
2. **Style per feeling.** A catalog entry may carry `styles` (feeling → style id), written into the voice manifest
   (`SherpaVoiceSpec.styles`, `styles=NEUTRAL:3,SAD:12`). Rasa: NEUTRAL → BOOK 3, LOUD → ANGER 1, SAD → SAD 12,
   SCARED → FEAR 7, SURPRISED → SURPRISE 14, LIVELY → HAPPY 8, ASKING → CONV 4; SOFT and TRAILING read as BOOK. The
   feeling travels `EngineRenderer` → `SpeechRequest.feeling` → `RoutedSpeech` → `SpeechEngine.render(…, feeling)`,
   a default method, so every other engine and voice ignores it unchanged.
3. **Two new feelings.** `SentenceFeelings` adds SCARED ("trembled", "डरकर", "ভয়ে") and SURPRISED ("gasped",
   "चौंककर", "অবাক হয়ে"), between SAD and LIVELY, so the five styles promised all have cues. Their pace and
   loudness nudges are in `DeliveryConfig`. Cue matching now runs on NFC text, so a PDF's one-code-point "য়"
   matches the cues too.
4. **No noise at the end.** sherpa-onnx cuts its input after `.`, `?` and `!`; with Rasa a final mark leaves an empty
   piece that is voiced as a short noise (measured: "যাচ্ছ?" renders as 1.07 s + 0.36 s, "যাচ্ছ।" as one 1.14 s
   piece). A styled voice's sentence drops its final ASCII marks and ends on the danda (`StyledText`). The cost: a
   question loses the "?" token; the CONV style carries the question instead.
5. **Characters.** `CastSpeakers` lists Rasa's two Bengali speakers, so with the female narrator, characters can be
   read by the male speaker (plan item 63), checked against the model's 1024 speakers at load.

## Consequences
- Only voices with `styles` change; every other voice renders byte for byte as before. No `:pdf` change.
- Rasa is fp32 and about RTF 0.2 at 4 threads on a desktop; it must be heard on the Moto before it is recommended.
- The upstream Hugging Face repository is gated: the licence (CC BY 4.0) is taken from the export's model card and
  the upstream metadata. Check the upstream page's terms once while signed in before the Play release.
- Rasa's style ids are fixed by the export; a different export would need its own table (the catalog pins the zip).
