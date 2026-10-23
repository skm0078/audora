# Pronunciation check

`scripts/pronunciation_check.py` reads `corpus.json` with every downloadable voice (the zips the app downloads,
sherpa-onnx 1.13.8) and transcribes each sentence back with Meta's Omnilingual ASR (Hindi, Bengali) or Whisper
(English). Run it locally; GitHub release downloads are enough:

    python3 -I scripts/pronunciation_check.py --models-dir models --out out --download --mp3 \
        --asr-indic asr/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12

## What it can and cannot hear
- It hears garbled and wrong words. It cannot judge Indic pronunciation finely: a kept schwa usually still
  transcribes as the right word, and where Bengali sound and spelling differ (পদ্মা said "podda") the ASR often
  writes the sound, so a rightly said word can show as missed. "Missed" means "listen to this one".
- Hindi sentences the ASR writes in Urdu script are not scored. Bengali heard in Devanagari is mapped back letter
  for letter. A word counts as heard when a transcript word is within one letter in four of it.

## First run (2026-10-23)
- **Piper Hindi (Pratham, Priyamvada): ग़, ख़ and क़ are garbled.** "ग़ज़ल" was heard as "फ़ज़ल" and "ओजल";
  without the nukta, "गज़ल" was heard right. Fixed per voice in `VoiceRespellings` (ज़ and फ़ read well and stay).
  Supertonic reads the nukta letters right and is unchanged.
- Hindi schwa and conjunct sentences: heard right on every Hindi voice, apart from one word per voice that needs an
  ear (Pratham "अर्थव्यवस्था", Priyamvada "क्षत्रिय").
- Bengali SYSPIN and Rasa: inherent vowels and loanwords heard right; the ja/ba-phala and conjunct "misses" are
  mostly the ASR writing the sound (লক্ষ্মী as "লোক্খি"), so they need the ear, not a rule.
