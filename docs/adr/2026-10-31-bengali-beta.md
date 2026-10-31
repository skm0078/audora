# ADR 2026-10-31-bengali-beta: Bengali says it is beta, and a Bengali scan offers the phone's voice

**Status:** Accepted (2026-10-31). The maintainer's call: Bengali ships as a very early beta; unless a book is a text
(digital) PDF, our reading of it is worse than the phone's own Bengali voice. A fix under the feature freeze: nothing
is removed, and a listener who changes nothing hears what they heard before.

## Context

A Bengali book whose font drops its joined letters (ADR 2026-10-30-lost-conjuncts, #130) is read by OCR on every
page. Tesseract's Bengali model (tessdata_best, the only offline Bengali OCR that fits the app's size and GPL) reads
clean modern print well (about 99 % of letters on the test book) but misreads older, worn print often. Our Bengali
voices (SYSPIN male and female, Rasa; VITS models that read letters, not words) then say the slips exactly as written,
so a poor scan sounds like every word is wrong. The phone's own Bengali voice (Google's, where installed) guesses
words more forgivingly. There is no modern offline Bengali OCR to switch to before launch: ML Kit has no Bengali, and
the open models that read it better are too big and slow for a phone.

## Decision

1. **The catalog marks beta languages** (`speech/models.json` `beta`, today `["bn"]`). The build writes `beta=true`
   into each such voice's catalog asset (`VoiceCatalogAssetsTask`), `CatalogVoice.beta` and `VoiceRow.beta` carry it,
   and the Voices screen shows "Beta: early quality, may say some words wrong" on the voice and on the language's
   one-tap download. Other voices' assets are unchanged byte for byte.
2. **A scanned page in a beta language says so** (`ReaderConfig.betaScanLanguages`, today Bengali;
   `BetaScanNotice.kt`). Only an OCR page that is mostly Bengali gets the card; a Bengali text PDF does not. It says
   OCR misreads older print, that the voice reads what was recognised, and that a text PDF reads far better.
3. **It offers the phone's voice** when the phone has an offline Bengali voice and one of ours reads Bengali now. The
   tap goes through the existing voice switch (`onBookVoice`): the book keeps it if it has its own settings,
   otherwise it becomes every Bengali book's voice, as picking it in Voices would. Voices undoes it. "Not now" hides
   the card for the book until the reader is opened again.

## Consequences

- Nothing changes for English or Hindi, nor for Bengali text PDFs beyond the Beta tag in Voices.
- **Cost:** one call that binds the phone's TTS (a few hundred ms, off the main thread) the first time a Bengali OCR
  page is shown in a reader session; no new dependency, model or download.
- Graduating Bengali is deleting it from both lists; `BetaLanguagesCatalogTest` and `BetaScanNoticeTest` then need
  the same edit, on purpose.
- The pronunciation fixes for clean text (ADR 2026-10-30-bengali-spoken-spelling) ship in the same PR: the labels say
  what is still rough, the respelling fixes what can be fixed on the text we are given.
- **Tests:** `BetaScanNoticeTest`, `CatalogVoiceParserTest`, `VoiceRowsTest`, `BetaLanguagesCatalogTest`; release
  journey step 65 (`BetaLanguageJourney`).
