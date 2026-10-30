# Every licence text opens from Voices & licences (2026-10-30, ADR 2026-10-30-open-source-notices)
- The APK now carries ListenLock's GPL-3.0, a notices index naming every bundled work with its copyright and licence,
  and the full text of each licence (Apache, BSD, MIT, IJG, FreeType, libpng, zlib, ONNX Runtime's notices). Voices &
  licences → All licence texts → Read opens them all. From the pre-store licence audit.
- Two research voice samples removed from `research/voice-samples` (MMS Bengali, CC BY-NC; Piper Google Bengali,
  withdrawn).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-licence-texts.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). No new dependency.
- **Could affect:** Voices & licences (new row; a voice's Files dialog now lists paragraphs lazily), the licence
  source wiring in `VoicesModule`.
- **Verified in CI:** `AppNoticesTest` (GPL copy, index ↔ folder, catalog libraries credited, statements, order,
  paragraphs), `LicencesControllerTest`, release journey step 59.

**Real-device checks (the test phone):** You → Voices & licences → All licence texts → Read: the notices index shows
first; scroll on to listenlock-GPL-3.0.txt and to onnxruntime-ThirdPartyNotices.txt (should scroll smoothly). A
downloaded voice's Files still shows its licence files.
