# ADR 2026-10-31-fdroid-ready-build: a release build F-Droid can accept

**Status:** Accepted (2026-10-31). Amends where ADR-012 resolves sherpa-onnx from; the library, version and
checksum are unchanged. No new dependency.

## Context

While Play reviews the developer account, ListenLock is being readied for F-Droid's main repository. Measured
against F-Droid's inclusion policy (f-droid.org/docs/Inclusion_Policy, read 2026-10-31), the build had three gaps:

1. **sherpa-onnx came from a GitHub release asset** through an Ivy repository (ADR-012). F-Droid accepts prebuilt
   FLOSS libraries only from trusted Maven repositories: Maven Central, Google, OSS Sonatype, JFrog, JitPack and
   Clojars. A GitHub release is not one of them.
2. **AGP wrote its dependency-info block into the signed APK**: an encrypted list of the app's libraries that only
   Google can read. F-Droid's scanner and IzzyOnDroid both reject APKs with it.
3. **No store metadata in the repository.** F-Droid and IzzyOnDroid read the name, descriptions, icon, screenshots
   and per-version changelogs from `fastlane/metadata/android/`. The text existed only as Markdown blocks in
   `docs/store/listing.md`.

## Decision

- **sherpa-onnx resolves from JitPack** as `com.github.k2-fsa:sherpa-onnx:1.13.8`, inside the existing exclusive
  JitPack repository (which already serves only Tesseract4Android, ADR-025). sherpa-onnx's own `jitpack.yml` at the
  v1.13.8 tag downloads the GitHub release AAR and republishes it unchanged, so the file is byte-identical: the
  pinned `sherpaOnnxAarSha256` stays, and `verifySherpaOnnxAar` still fails the build on any difference. The
  F-Droid-listed SherpaTTS app resolves sherpa-onnx the same way.
- **`android.dependenciesInfo.includeInApk = false`** in `:app`. `includeInBundle` stays true: Play Console reads it
  for its SDK warnings, and the bundle goes only to Play. `scripts/check_apk_signing_block.py` fails CI if a signed
  APK carries the block again (the dev APK on every push, the release APK in the release workflow).
- **`fastlane/metadata/android/` is the one copy of the store text** (en-US, hi-IN, bn-BD; Play has no bn-IN). The
  English title is the maintainer's "ListenLock: PDF & Books Aloud". `docs/store/listing.md` keeps the rules,
  graphics and category and points to these files. The icon and feature graphic are copies of the masters in
  `docs/store/`, which the public site also serves. `scripts/check_store_metadata.py` checks Play's limits (the
  strictest of the three stores), that the copies match their masters, and, in the release workflow, that the
  version being released has an English changelog.
- The draft F-Droid recipe and the steps only the maintainer can take live in `docs/store/fdroid.md`.

## Alternatives considered

- **Build sherpa-onnx and ONNX Runtime from source in the F-Droid recipe.** The most "pure" option, and F-Droid can
  do it (their packagers say many apps do), but ONNX Runtime is a long native build to maintain in a recipe, and the
  policy accepts the JitPack copy. Kept as the fallback if a reviewer asks for it.
- **Keep the GitHub asset and add `scanignore`/`srclibs` workarounds in the recipe.** Hides the binary from the
  scanner rather than fixing its source; reviewers reject that.
- **Turn off `includeInBundle` too.** Costs Play's SDK warnings and gains nothing: the bundle never reaches F-Droid.
- **Separate store text for F-Droid.** Two copies drift; Play and F-Droid users would read different claims.

## Consequences

- One more host on JitPack's exclusive list. If JitPack loses the v1.13.8 build, the checksum check stops the build
  rather than shipping something else; the GitHub release is the documented fallback.
- Any change to the listing is a change to `fastlane/`, reviewed like code and checked by CI.
- The APK size, speed and behaviour do not change: the same AAR, and the dependency block was never read on the
  phone.
- **IzzyOnDroid** is not planned for now (the maintainer's decision; it also reserves about 30 MB per app, and the
  release APK is about 108 MB). The fastlane metadata would serve it unchanged.
