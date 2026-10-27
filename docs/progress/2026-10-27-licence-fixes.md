# Licence fixes from the voice audit (2026-10-27, ADR 2026-10-27-licence-fixes)
- Piper Google Bengali withdrawn (Lessac-derived): gone from the catalog, deleted from phones at start.
- Supertonic: its OpenRAIL-M rules are shown before every download or update and must be agreed to; the full licence
  ships in its zip as `MODEL_LICENSE` (re-pinned) and shows on Voices & licences.
- PDFium and DjVuLibre credited on Voices & licences; their notices ship in `assets/licences`.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-27-licence-fixes.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged).
- **Could affect:** the Voices screen and the reader's language-pack card (download buttons), the catalog parser
  (new `useTerms` key), the voice mirror (new `modelLicence` key), Bengali voice choice for anyone who picked Google.
- **Verified in CI:** `UseTermsGateTest`, `CatalogVoiceParserTest`, `VoiceRowsTest`, `LanguagePacksTest`,
  `CatalogLicencesTest` (withdrawn ids absent), the voice mirror's script tests and pin check, the release journey.

**Real-device checks (the test phone):** Settings → Voices → Download Supertonic: the rules appear, Cancel downloads
nothing, Agree downloads it. Open a Hindi book with no Hindi voice: the card's Download shows the same rules. Voices &
licences → Supertonic → Files shows MODEL_LICENSE; PDFium and DjVuLibre are listed under the software.
