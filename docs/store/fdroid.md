# ListenLock on F-Droid

What each store needs, what the repository already does, the draft F-Droid recipe, and the steps only the maintainer
can take. Decision record: ADR 2026-10-31-fdroid-ready-build. Policies read on 2026-10-31; re-read them before
applying.

## F-Droid main repository

F-Droid builds the app itself from the source at a release tag and signs it with F-Droid's key.

| Requirement (f-droid.org/docs/Inclusion_Policy) | Status |
| --- | --- |
| FLOSS licence | GPL-3.0-only. Every voice, model and library is checked in the licence audit and credited in the app. |
| Prebuilt libraries only from trusted Maven repos | **Open: sherpa-onnx** still comes from its GitHub release, which F-Droid does not trust. JitPack's v1.13.8 build has no AAR (its build hit a 404), so moving there fails. Fix before applying: a sherpa-onnx version whose JitPack build has the AAR, or build it from source in the recipe. PDFium (Maven Central) and Tesseract4Android (JitPack) are fine; llama.cpp is built from source. |
| No Google Play Services, Firebase, analytics, ads | None. The Android Auto `meta-data` line is a manifest entry, not a library. |
| No Google dependency-info blob in the APK | Off since this ADR; CI fails a signed APK that carries it. |
| No downloading executable code without opt-in | Downloads are voice, OCR and dictionary data only, each started by the listener. |
| Store metadata in fastlane format | `fastlane/metadata/android/` (en-US, hi-IN, bn-BD): title, descriptions, icon, feature graphic, per-version changelog. Screenshots still to add. |
| Developed for at least 3 months, or has users | First commit 2026-10-14, so 2027-01-14; earlier once Play production has users (planned 14 Dec). |
| Release tags F-Droid can follow | Releases are tagged `vMAJOR.MINOR.PATCH` on `prod`; the recipe watches those tags. |

**Anti-features to declare** (undisclosed ones get an app rejected, so the recipe lists them up front):

- **NonFreeNet**: voices and language packs download from this repository's GitHub releases (F-Droid gives this
  label to apps that download models from a hosted service, as with SherpaTTS).
- **NonFreeAdd**: the optional Hindi voice Supertonic is under OpenRAIL-M, which adds use restrictions, so it is not
  FLOSS. Every other downloadable voice and model is Apache-2.0, MIT or CC-BY.

**Known review risks** (no fix needed now, answer if a reviewer asks):

- sherpa-onnx from source, if chosen: a `srclibs` build of sherpa-onnx's `build-android-arm64-v8a.sh` with ONNX
  Runtime built from its tag, and a Gradle property that points `:speech` at the resulting AAR.
- The build downloads llama.cpp's source archive and the bundled English voice, both checked against pinned SHA-256
  values. If F-Droid wants them offline, the recipe can fetch them as `srclibs`/`prebuild` into the same cache paths
  (`~/.gradle/caches/audora-llama`, `~/.gradle/caches/audora-voices`).

**Signing.** F-Droid signs with its own key, so an F-Droid install cannot update a Play or GitHub install (or the
reverse) without uninstalling, which loses the library. F-Droid can instead ship our own signed APK if its build
matches it byte for byte (reproducible builds, `Binaries:` and `AllowedAPKSigningKeys:` in the recipe). That needs a
CI job that builds the release twice in different folders and compares them; it is the next step after this one.

### Draft recipe

This file goes into the `fdroiddata` repository as `metadata/io.github.skm0078.audora.yml` (see the steps below).
Fill `commit:` with the release tag once `v0.1.0` exists on `prod`.

```yaml
AntiFeatures:
  NonFreeAdd:
    en-US: The optional Hindi voice Supertonic is licensed under OpenRAIL-M, which adds use restrictions.
  NonFreeNet:
    en-US: Voices and language packs are downloaded from the project's GitHub releases when you choose them.
Categories:
  - Reading
License: GPL-3.0-only
AuthorName: TelosMind Labs
AuthorEmail: support@telosmindlabs.com
WebSite: https://listenlock.telosmindlabs.com/
SourceCode: https://github.com/skm0078/audora
IssueTracker: https://github.com/skm0078/audora/issues
Changelog: https://github.com/skm0078/audora/releases

AutoName: ListenLock

RepoType: git
Repo: https://github.com/skm0078/audora.git

Builds:
  - versionName: 0.1.0
    versionCode: 1000
    commit: v0.1.0
    subdir: app
    gradle:
      - yes
    ndk: r28c

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: 0.1.0
CurrentVersionCode: 1000
```

`ndk: r28c` is NDK 28.2.13676358, the version `libs.versions.toml` pins. The release build type is what `gradle: yes`
builds; without the signing environment variables it comes out unsigned, which is what F-Droid expects.

### Maintainer steps (when the time comes)

Do these after `v0.1.0` is tagged on `prod` and Play production has testers or users (or after 2027-01-14).

1. Create a GitLab account at https://gitlab.com/users/sign_up with the company identity: username `telosmindlabs`,
   email `admin@telosmindlabs.com`, and "TelosMind Labs" as the display name (Settings → Profile → Full name).
2. Open https://gitlab.com/fdroid/fdroiddata and click **Fork** (top right), then **Fork project**.
3. In your fork, click **+** → **New file**. File name: `metadata/io.github.skm0078.audora.yml`. Paste the draft
   recipe above, with `commit:` set to the tag. Commit message: `New app: ListenLock`. Choose **Commit to a new
   branch** named `io.github.skm0078.audora`, then **Commit changes**.
4. Click **Create merge request**. Target: `fdroid/fdroiddata` `master`. Title: `New app: ListenLock`. In the
   description, tick the template's checklist honestly and add one line: "Upstream author; GPL-3.0; anti-features
   declared in the recipe." Click **Create merge request**.
5. The pipeline builds the app on GitLab. If it fails, paste the failing job's link in this project's chat and the
   fix comes back as a PR here.

## IzzyOnDroid

Not planned for now (the maintainer's decision). IzzyOnDroid takes the developer's own signed APK from GitHub
releases and reserves about 30 MB per app as a rule of thumb; the release APK is about 108 MB with the bundled English
voice. Its inclusion policy is at izzyondroid.org/docs/general/AppInclusionPolicy.

F-Droid-client users are still covered: Obtainium installs and updates from GitHub releases (README), and the same
fastlane metadata serves any F-Droid-format repository later.
