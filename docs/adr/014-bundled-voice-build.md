# ADR-014: The bundled English voice is fetched at build time, SHA-pinned, with espeak-ng-data trimmed

**Status:** Accepted (P1-03, 2026-10-15). The voice itself stays provisional until the ADR-012 phone rating.

## Context
Spec §3 bundles one English neural voice in the APK. Model binaries must never enter git (AGENTS.md), the container
and CI build the APK without Android Studio, and ADR-012 left the choice between Piper ljspeech, Piper cori and
KittenTTS to a blind rating on the test phone that has not happened yet. P1-03 has to ship a voice now and make the later
swap a configuration change. Each sherpa-onnx voice archive also carries 19 MB of espeak-ng-data, 17 MB of which are
dictionaries for languages Audora never speaks.

## Decision
1. **One file decides the voice:** `speech/bundled-voices.json` lists the candidates (archive, SHA-256, family, files,
   speaker, licence) and `"bundled"` names the one in the APK. Provisional choice: **Piper en_US ljspeech** (0.0 % CER
   and the lowest RTF in the S1 desktop run). Changing `"bundled"` to `piper-en-cori` or `kitten-en-nano` is the whole
   swap; the code reads the family and files from the generated `voice.properties`.
2. **Fetched at build time from sherpa-onnx's `tts-models` release**, not from our `s1-voices` pre-release: upstream
   assets are immutable, ours is rebuilt by a workflow and could drift from a pinned hash. The build task
   (`build-logic/.../BundledVoiceAssetsTask.kt`) checks the SHA-256 and fails loudly on a mismatch; downloads are
   cached in the Gradle user home.
3. **espeak-ng-data keeps only the en, hi and bn dictionaries** (19 MB → 2.1 MB). Desktop sherpa-onnx 1.13.8 speaks
   the trimmed voice normally.
4. **Weights are read from the APK** through the AssetManager and stored uncompressed (`noCompress "onnx"`); only
   espeak-ng-data is copied to `noBackupFilesDir` (once per voice version), because espeak-ng reads real files.

## Alternatives considered
- **Download the voice on first launch** (like premium voices): smaller APK, but the first run needs a network and the
  spec's offline-first default fails until it finishes. Rejected for the default voice.
- **Commit the model to the repo:** forbidden (AGENTS.md) and 60 MB per voice change in history.
- **Copy the whole voice to internal storage:** uniform with downloaded voices but doubles the 60 MB on the phone.

## Consequences
- The APK grows by about 63 MB (model) + 2 MB (espeak) + 27 MB (sherpa-onnx native libraries). With Kitten it would
  be ~30 MB smaller; the rating decides.
- First builds need github.com reachable (as for the sherpa-onnx AAR, ADR-012).
- P4-01's voice manager can reuse `voice.properties` and `SherpaSpeechEngine` with a file-based factory.
