# Books in any language your phone reads, with its own voice (2026-10-26, ADR 2026-10-26-more-languages-with-phone-voices)
- A book whose language is none of English, Hindi and Bengali (from its file, or set in Edit details) is read by the
  phone's own offline voice for that language. Tamil, Telugu, Arabic, Cyrillic, Japanese, Chinese, Korean and other
  scripts are found by their letters and read the same way, even inside an English sentence. English sentences of
  a French book stay English. With no voice on the phone, a book reads as it did before.
- Edit details lists every language the phone has an offline voice for, says when the book's language has none, and
  opens Android's text-to-speech settings with the steps to download one.
- "Skip unreadable text" no longer cues good French, Polish or Marathi sentences as junk.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-more-languages-with-phone-voices.txt`. `:pdf` not
  changed (routing is at render time, ADR-026), so `PIPELINE_VERSION` stays 13 and the corpus and listening scores
  cannot move. No new dependency.
- **Could affect:** mixed-script routing (Hindi/Bengali/English sentences route exactly as before, tested), the
  Hindi and Bengali phone-voice fallback, studio keys (unchanged for books in the three languages), Edit details.
- **Verified in CI:** `PhoneLanguagesTest`, `PhoneLanguageRoutingTest` (incl. English, Hindi and Bengali books route as
  before), `ScriptRouterTest`, `RoutedSpeechTest`, `CastSpeechTest`, `SystemSpeechEngineTest`, `SentenceLanguageTest`
  (incl. the bundled English list), `EngineRendererTest`, `BookEditDraftTest`, `MadeAndEditScreensTest`, golden and
  listening tests, `ReleaseJourneyTest`.

**Real-device checks (the test phone):**
1. In Android Settings, search "Text-to-speech output", tap the gear beside Speech Services by Google, Install voice
   data, and download French (or any language). Back in ListenLock, open a book's Edit details: French is listed.
2. Open a French EPUB (or set a PDF's language to French): it reads in the French voice; "20 €" is said in French.
3. A page with an English paragraph in that book: the English voice reads it.
4. Set the language back to "As the book says" while it plays: the sentence restarts in the old voice at once.
5. Pick a language the phone has no voice for (a Tamil book on a phone without Tamil): Edit details says so; the book
   still plays with ListenLock's own voices.
