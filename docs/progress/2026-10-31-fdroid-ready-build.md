# A release build F-Droid can accept (2026-10-31, ADR 2026-10-31-fdroid-ready-build)
- sherpa-onnx now resolves from JitPack (`com.github.k2-fsa:sherpa-onnx:1.13.8`), a repository F-Droid trusts,
  instead of a GitHub release asset. JitPack republishes the same AAR, so the pinned SHA-256 is unchanged and still
  checked before every build.
- The signed APK no longer carries AGP's encrypted dependency-info block (`dependenciesInfo.includeInApk = false`);
  the Play bundle keeps it. `scripts/check_apk_signing_block.py` fails CI if it comes back (dev APK on every push,
  release APK in the release workflow).
- Store text and graphics moved into `fastlane/metadata/android/` (en-US, hi-IN, bn-BD), the format F-Droid and
  IzzyOnDroid read; `docs/store/listing.md` points there. English title is now "ListenLock: PDF & Books Aloud".
  `scripts/check_store_metadata.py` checks Play's limits and that the icon and feature graphic match their
  `docs/store/` masters; the release workflow also requires `en-US/changelogs/<versionCode>.txt`.
- `docs/store/fdroid.md`: F-Droid status table, anti-features to declare, the draft recipe and the maintainer's
  GitLab steps; IzzyOnDroid not planned for now.
- No app code changed. `:pdf` not changed (PIPELINE_VERSION unchanged). No new dependency. No "What's new" entry:
  nothing a listener notices.
- **Perf cost:** none. Same AAR byte for byte; the removed block was never read on the phone.
- **Could affect:** dependency resolution (one more module on the exclusive JitPack list), the release workflow
  (one more check), the Play listing text source.
- **Verified in CI:** `test_check_apk_signing_block.py`, `test_check_store_metadata.py` (including the repo's own
  fastlane folder), `verifySherpaOnnxAar` against the JitPack copy, the new signing-block step on the dev APK.
  No release journey step: nothing in the app changed for a listener to walk through.

**Real-device checks (the test phone):** install the dev APK from this branch over the current dev build, open a book
and press play: English (the bundled voice) speaks, and a downloaded Hindi or Bengali voice speaks. That proves the
JitPack copy of sherpa-onnx loads exactly as before.
