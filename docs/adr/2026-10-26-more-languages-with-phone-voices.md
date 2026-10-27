# ADR 2026-10-26-more-languages-with-phone-voices: any language the phone reads offline

**Status:** Accepted (2026-10-26). Widens the language scope of the Build Spec (§1, §3: English, Hindi and Bengali,
`Lang` "D1" in its comment) without changing it for those three. Competitor coverage ticket CF-11.

## Context
ListenLock reads English, Hindi and Bengali with its own voices, normalizers and sentence rules. Every paid reader
it is compared with reads dozens of languages, and the brand is global. Today a French EPUB is read by the English
voice (Latin letters are "English"), a Marathi book by the Hindi voice, and a Tamil, Arabic or Japanese page by the
English voice, which cannot say those letters at all. Worse, "Skip unreadable text" (on by default) judges every Latin
sentence with the English word list: measured on everyday sentences, French, Spanish and Italian score 0.6 to 0.8,
German 0.55, and Polish, Turkish and Vietnamese 0.3 to 0.4, below the 0.55 cut, so those books were partly skipped.

Every Android phone ships a text-to-speech engine (usually Google's) with offline voices for many languages, already
used as the Hindi and Bengali fallback (`SystemSpeechEngine`).

## Decision
1. **Three tuned languages stay as they are.** `Lang` keeps EN, HI and BN; their voices, normalizers, sentence rules,
   OCR and scores do not change. Any other language is a *phone language*: a BCP 47 primary tag ("fr", "ta") read by
   the phone's best **offline** voice for it. Voices that need the network are never used (privacy promise).
2. **Where the language comes from.** The book's language as the library already holds it (an EPUB's or Office
   file's `dc:language`, an HTML `lang`, or the listener's choice in Edit details, which now lists every language the
   phone has an offline voice for). Letters of a script none of the three is written in (Tamil, Telugu, Kannada,
   Malayalam, Gujarati, Gurmukhi, Odia, Sinhala, Arabic, Hebrew, Cyrillic, Armenian, Georgian, Thai, Lao, Khmer,
   Myanmar, Ethiopic, Hangul, Han and kana) get that script's language with no book language needed
   (`PhoneLanguages`). Greek is left out of that list: outside a Greek book its letters are maths.
3. **Decided at render time, in `:speech`'s ScriptRouter** (as ADR-026 did for mixed scripts). `:pdf` output, resume
   anchors and `PIPELINE_VERSION` are unchanged, so the golden corpus and listening scores cannot move: no corpus text
   has three letters in a row of any routed script (checked over every golden and listening page dump).
4. **Read as written.** A phone-voice part skips our normalizers: the phone's engine says numbers, dates and money in
   its own language ("20 €" is "vingt euros", not "twenty euros").
5. **English stays English.** In a book whose phone language is written in Latin letters, a sentence of four or more
   words whose letters are 85% English words keeps the English voice (`SentenceLanguage`, `PhoneLanguageConfig`), so
   an English abstract in a French paper, or a LibreOffice file that names the author's own language, still reads in
   English. Measured: English sentences 1.0, French, Spanish, German, Portuguese, Italian, Dutch, Indonesian 0.1-0.6.
6. **Never worse than before.** No voice for the language: the sentence is read whole by its own `Lang` voice, as it
   was, and Edit details says the phone has no voice for it, with a button to Android's text-to-speech settings and
   the steps to download one. A missing language is asked about again after a minute, not every sentence.
7. **Skip unreadable text** does not judge a sentence read in a phone language written in the same letters as one of
   the three (French in Latin, Marathi in Devanagari): its score came from the wrong word list. Tamil or Arabic were
   never scored by a list, so their scores stand.
8. **Applies at once** (ADR-064): changing a book's language while it plays restarts the sentence playing in the new
   voice. Studio audio of a phone-language book is keyed by that language (`StudioSignature.phoneLanguage`); books in
   the three languages keep their keys, so no studio audio is thrown away.

## Alternatives
- **Our own neural voices per language** (Piper has about 40 languages): better sound, but 60-120 MB each, a licence
  check per voice (several are non-commercial, ADR-074), normalizers and sentence rules per language, and weeks of
  work. Kept for later, language by language, if listeners ask.
- **More `Lang` entries** (FR, DE, ES ...): every `when (lang)` and per-language table (time left, voices, quiz,
  pronunciation) would need a row per language, for no gain over the phone's engine.
- **Language detection by a model** (fastText, CLD3): another dependency and model for what the book's metadata and
  the script already say; the English guard covers the common mistake.

## Consequences
- Dozens of languages read aloud, offline, with nothing to download from us; quality is the phone's voice, plainer
  than ListenLock's own. Edit details says so.
- Not covered for phone languages: our number and abbreviation reading (the engine's own is used), dandas and
  abbreviation rules (ICU's generic sentence rules), OCR of scanned pages (Tesseract models are en/hi/bn only,
  ADR-025), the dictionary, character voices and acted emotion (one phone voice per language).
- A PDF says nothing about its language: a French PDF needs its language set once in Edit details. Other scripts are
  found by their letters.
