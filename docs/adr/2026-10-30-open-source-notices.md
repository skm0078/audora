# ADR 2026-10-30-open-source-notices: every licence text and notice ships in the app

**Status:** Accepted (2026-10-30). Follows ADR 2026-10-27-licence-fixes, after the pre-store licence audit of
everything ListenLock ships. A licence fix, allowed in the feature freeze. No new dependency.

## Context

The audit found every voice, model, font, sound and library allowed in a commercial Play app, but the APK did not
carry what some licences ask to travel with a binary:

1. **ListenLock's own GPL-3.0.** Sections 4 and 6 require a copy of the licence with every copy of the program. The
   APK had none.
2. **BSD-style and permissive notices inside native code.** PDFium bundles FreeType (its licence asks for "Portions
   of this software are copyright (c) <year> The FreeType Project" in the documentation), libjpeg-turbo (the IJG
   licence asks for "this software is based in part on the work of the Independent JPEG Group"), OpenJPEG, Little
   CMS, libpng, zlib and Anti-Grain Geometry. sherpa-onnx bundles ONNX Runtime, Protocol Buffers and RE2 (BSD-3).
   Tesseract4Android bundles Leptonica and IJG libjpeg 9f. DataStore bundles Protocol Buffers. BSD licences ask for
   their notice "in the documentation and/or other materials provided with the distribution".
3. **Java and Kotlin libraries** (AndroidX, Media3, Room, DataStore, WorkManager, Guava, Dagger/Hilt, Kotlin,
   coroutines, Material Icons) were not credited at all. Apache-2.0 asks for a copy of the licence.

The screen showed names and links only, and the OFL, PDFium and DjVuLibre texts already in `assets/licences` could
not be opened.

## Decision

- `app/src/main/assets/licences/` holds every text: `THIRD-PARTY-NOTICES.txt` (each work, its copyright holders,
  licence and the file with the full text, plus the IJG and FreeType statements), `listenlock-GPL-3.0.txt` (the
  repository's `LICENSE`), Apache-2.0, and each BSD, MIT, IJG, FreeType, libpng and zlib text as published upstream.
  ONNX Runtime's own `ThirdPartyNotices.txt` ships unchanged.
- Voices & licences gets one row near the top, "All licence texts", whose Read button opens all of them in the
  existing licence dialog: the index first, the GPL second, then the rest by name. The dialog now lays files out as a
  lazy list of paragraphs, so ONNX Runtime's 330 KB notices file scrolls without laying out one huge block.
- `AppNoticesTest` keeps the folder honest: the GPL copy must equal `LICENSE`, the index must list exactly the files
  in the folder, no file may be empty, the IJG and FreeType statements must be present, and every library group in
  `gradle/libs.versions.toml` that reaches the APK must be named in the index. A new library therefore fails the build
  until it is credited.
- Hand-kept rather than generated: a generator plugin (such as AboutLibraries) would be a new build dependency, and
  it cannot see the C libraries inside the AARs, which are most of what needed fixing.

## Consequences

- APK grows by about 100 KB compressed (mostly ONNX Runtime's notices). No runtime cost until Read is tapped.
- Transitive Java dependencies are credited by family, not artifact by artifact; a version bump that brings a new
  family must be added by hand (the test only sees the catalog's direct libraries).
- Two research voice samples leave the repository: Meta MMS Bengali (a CC BY-NC model) and Piper Google Bengali (the
  Lessac-derived voice withdrawn on 27 October).
