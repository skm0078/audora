# ADR-058: Act the quoted words, keep the mood, fix what a voice misreads

**Status:** Accepted (2026-10-23). Follows ADR-057; asked for after listening: "pronunciation and real emotion still lack".

## Context
With ADR-057 a styled voice (Rasa) read a whole sentence in its feeling's style, so "“দাদা, তুমি!” সে অবাক হয়ে
বলল" said the narration "she said, surprised" in the surprise style too, which sounds put on. Each sentence also
chose its style alone, so a sad scene went back to book style the moment the cue word was past. For pronunciation
there was no measure of what the voices themselves say: the listening score (0.986) checks only the text they get.

## Decision
1. **Only the quoted words are acted.** When the narrator voice acts feelings (`SpeechEngine.actsFeelings`, true for
   a voice with `styles`), `CastSpeech` splits a quoted sentence even without a dialogue voice; the quotes take the
   feeling and the narration around them is read plainly. A sentence without quotes ("মিনু কেঁদে ফেলল।") is acted
   whole, as before. Voices without styles are not split for it, so their reading is unchanged.
2. **A lasting mood carries one sentence.** `SceneMoods`: a sad or scared sentence colours the next sentence of the
   same paragraph when that one asks for no feeling of its own (`QuoteCarry.moodOf` → `DeliveryPlanner.plan(…,
   carried)`). Shouts, laughs and surprises are moments and never carry; a paragraph break ends a mood.
3. **A pronunciation check.** `scripts/pronunciation_check.py` reads `research/pronunciation/corpus.json` (rule-dense
   Hindi, Bengali and English sentences) with every catalog voice and transcribes them back with Omnilingual ASR.
   It finds garbled words, not fine vowel quality; samples go to the ear.
4. **Per-voice respellings.** `VoiceRespellings`, keyed on catalog voice ids like `CastSpeakers`, so installed voices
   get them without a download. First entry, from the check: Piper's Hindi voices garble ग़, ख़ and क़ ("ग़ज़ल"
   heard as "ओजल"), so they get them without the nukta. Supertonic reads them right and is untouched.

## Consequences
- Studio audio made before keeps its old reading until it is rendered again (the studio key is unchanged).
- Splitting a quoted sentence for a styled voice adds the quote pause between its parts, as a dialogue voice does.
- New respellings need a check result and a before/after listen; the check is run by hand (GitHub downloads only).
