# ADR-056: Read each book to suit it, and a simpler Play build

**Status:** Accepted (2026-10-23). Expressive reading Stage 1. Stage 2 (emotion voices) is not part of it.

## Context
Every book was read the same way: a children's story, a psalm, a news report and a maths paper got the same pace,
pauses and loudness. The `:pdf` pipeline already labelled document kinds (RI-10), but playback never used it. The
Play release also needs a first screen a new listener understands: Settings → Voices listed 13 voices with licences
and speeds, and Settings put rarely used switches next to common ones.

## Decision
1. **Content intent per book.** `IntentClassifier` (`:pdf`, pure) labels a book STORY, CHILDREN, POETRY,
   SCRIPTURE, NEWS, REPORT, STUDY or GENERAL from counts over its first pages (`IntentSignals`): quoted dialogue,
   speech verbs ("said", "कहा", "বলল"), story openers, verse lines from the page's line boxes, verse numbers and
   words of worship, datelines and agencies, official vocabulary, teaching cues, citations and captions. Word lists
   cover English, Hindi and Bengali; Indic words match from a word start (`\b` knows no Indic letters). The label
   settles after 1,500 words (`BookIntent`) and needs at least 120. Thresholds live in `DeliveryConfig`.
2. **Delivery per sentence.** `DeliveryPlanner` (`:speech`) turns the intent's profile (rate, dialogue rate,
   pauses, heading pauses, clause pauses) and, for stories, a sentence's feeling (`SentenceFeelings`: loud, soft,
   sad, lively, trailing, asking) into multipliers. `EngineRenderer` applies them at render time only: the model's
   rate (never past its cap), the silences, and up to +3 dB of loudness. Facts and scripture are never acted.
3. **A sentence a page cut goes on.** A body sentence of 4+ words that ends on a lowercase letter or a comma (a page
   or column break) is spoken with a trailing comma (rising, not final) and 20 % of its pause. Hindi and Bengali only
   count a comma, since OCR can drop dandas. Verse and scripture lines keep their fall.
4. **One switch.** "Read to suit the book" (`VoiceShaping.suitBook`, default on) sits in the speech sheet's first
   rows with what the book reads as; off, every book reads plainly. The rarer speech options sit under "More speech
   options", closed.
5. **Language packs.** Settings → Voices opens on one download per language (models.json `recommended`: Pratham for
   Hindi, SYSPIN male for Bengali), with plain percent and MB progress; the full list is under "All voices". The
   reader offers the pack on a Hindi or Bengali page the phone's voice would read. The first voice of a language is
   picked automatically (unchanged).
6. **A simple Play build.** `BuildConfig.SIMPLE_VOICES` (release true, debug false, `-Paudora.simpleVoices`
   overrides) hides Supertonic Hindi (licence file missing upstream), MMS Bengali (non-commercial) and Piper Google
   Bengali (weakest), plus "Import a voice". Hidden voices already on a phone stay listed. Nothing is deleted.
7. **Settings order.** Listening, reading, appearance and app first; punctuation for scans, volume keys, voice
   commands, pronunciation, Share logs and debug under a closed "Advanced".
8. **Branding.** The release icon is the app's amber mark (sound bars over an open book) on its paper colour, also
   the themed icon; the Android 12 splash uses the same colours (dark in night mode). Store art is in `docs/store/`.
   The dev build keeps "Audora Dev" and its orange bars icon.

## Alternatives considered
- An on-device language model for genre and emotion: hundreds of MB and seconds per page; rejected for Kotlin
  (online and LLM features are Flutter-only).
- Emotion voices (AI4Bharat Rasa): Stage 2, needs the user's go-ahead and a new download.
- SSML prosody: the sherpa-onnx voices ignore it; rate, silence and gain are what they honour.

## Consequences
- No `:pdf` output change: PIPELINE_VERSION stays 12, the golden and listening scores are unchanged. Intent labels
  on the golden excerpts are pinned by `IntentLabelsTest`.
- `suitBook` joins the voice shaping encoding, so studio audio made before renders once more.
- A wrong label costs a few percent of pace, never words; the switch turns it off per listener.
- Settings rows moved: users who knew where "Share logs" was now open "Advanced".
