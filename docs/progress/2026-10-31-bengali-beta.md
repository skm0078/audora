# Bengali says it is beta (2026-10-31, ADR 2026-10-31-bengali-beta)
- `speech/models.json` `beta: ["bn"]`; the build marks those voices (`VoiceCatalogAssetsTask`), and the Voices screen
  shows a Beta line on each Bengali voice and on the Bengali one-tap download (`BetaLabel`).
- A scanned Bengali page shows a card under the OCR badge: early beta, OCR misreads older print, a text PDF reads far
  better, and "Use the phone's Bengali voice" when the phone has one and ours reads now (`BetaScanNotice.kt`).
- **Could affect:** the Voices screen layout, the reader's page top on Bengali scans.
- **Verified in CI:** `BetaScanNoticeTest`, `CatalogVoiceParserTest`, `VoiceRowsTest`, `BetaLanguagesCatalogTest`,
  release journey step 65.

**Cost:** one bind of the phone's TTS the first time a Bengali scanned page shows in a reader session; nothing else.

**Real-device checks (the test phone):** see the PR.
