# A release build F-Droid can accept (2026-10-31, ADR 2026-10-31-fdroid-ready-build)
- The signed APK no longer carries AGP's encrypted dependency-info block (`dependenciesInfo.includeInApk = false`);
  the Play bundle keeps it. `scripts/check_apk_signing_block.py` fails CI if it comes back (dev APK on every push,
  release APK in the release workflow).
- Store text and graphics moved into `fastlane/metadata/android/` (en-US, hi-IN, bn-BD), the format F-Droid and
  IzzyOnDroid read; `docs/store/listing.md` points there. English title is now "ListenLock: PDF & Books Aloud".
  `scripts/check_store_metadata.py` checks Play's limits and that the icon and feature graphic match their
  `docs/store/` masters; the release workflow also requires `en-US/changelogs/<versionCode>.txt`.
- `docs/store/fdroid.md`: F-Droid status table, anti-features to declare, the draft recipe and the maintainer's
  GitLab steps; IzzyOnDroid not planned for now. One blocker left: sherpa-onnx comes from a GitHub release, and
  JitPack's v1.13.8 build has no AAR, so it needs an upgrade or a source build before applying.
- No app code changed. `:pdf` not changed (PIPELINE_VERSION unchanged). No new dependency. No "What's new" entry:
  nothing a listener notices.
- **Perf cost:** none. No app code or library changed; the removed block was never read on the phone.
- **Could affect:** the release workflow (one more check), the Play listing text source.
- **Verified in CI:** `test_check_apk_signing_block.py`, `test_check_store_metadata.py` (including the repo's own
  fastlane folder), the new signing-block step on the dev APK.
  No release journey step: nothing in the app changed for a listener to walk through.

**Real-device checks (the test phone):** none needed; the app's code and libraries are unchanged. Optional: install
the dev APK from this branch and play a book, as a smoke test.
