# ADR-022: Voice manager — mirrored zips, catalog as build-time assets, resumable WorkManager downloads

**Status:** Accepted (P4-01, 2026-10-16).

## Context
P4-01 adds voices beyond the bundled English one: downloads listed in `models.json` (pinned by SHA-256, Build
Spec §8), delete, and import of a custom sherpa-onnx voice. P3-05 (Tesseract data) and P3-06 (Hindi/Bengali voices)
will download through the same path. Constraints: no new dependencies, no network except model downloads, an
interrupted download must resume and a corrupt one must be refused (done-when), and sherpa-onnx's releases ship
`.tar.bz2`, which Android cannot unpack without a new library.

## Decision
1. **Mirror:** each catalog voice is repacked from its sherpa-onnx release archive (SHA-pinned as `source`) into a
   reproducible STORED zip (sorted entries, fixed dates and modes, espeak-ng dictionaries trimmed to en/hi/bn) by
   `scripts/build_voice_mirror.py`. The `voice-mirror` workflow builds and verifies the zips on every change, and on
   `dev` uploads new ones to the `voices` pre-release of this repo. Asset names carry the hash (`<id>-<sha12>.zip`),
   so an asset is never replaced; a new pin is a new file.
2. **Catalog:** `speech/models.json` is validated at build time (`VoiceCatalogAssetsTask`) and written into the APK
   as one `voices/catalog/<id>.properties` per voice (the `voice.properties` keys plus `url`, `sha256`, `sizeBytes`).
   No JSON library at runtime; pins travel inside the signed APK, so nothing downloaded can change them.
3. **Download:** a plain (non-foreground) `CoroutineWorker`, unique work `voice-download:<id>` (KEEP), on a connected
   network with exponential backoff, up to `VoiceDownloadConfig.maxAttempts`. Each run resumes the `.partial` file
   with an HTTP `Range` request (206 appends, 200 restarts, Content-Range start checked), so WorkManager's ~10 min
   per-run limit only splits a download into runs. Free space for the zip plus its unpacked copy is checked before
   any request. The whole file is hashed at the end; a mismatch deletes it and fails without retrying.
4. **Install:** unpack into `<id>.partial` with a zip-slip guard, a size cap and a required-files check, then swap
   in by rename (`<id>.old` kept until the new copy is in place; `recover()` tidies after a kill). Installed voices
   live in `noBackupFilesDir/voices/managed`.
5. **Import:** the user picks a folder (Storage Access Framework). Accepted: exactly one `.onnx` model + `tokens.txt`
   (Piper/MMS as VITS; with `voices.bin` + `espeak-ng-data` as KittenTTS). Refused with a reason: no model, several
   models (Matcha/Kokoro-style multi-file), lexicon or `dict/` voices, Kitten without espeak data, too big, too many
   files, no space. Only the needed files are copied; espeak dictionaries are trimmed like the mirror's.
6. **Choosing a voice:** the reading voice per language is a properties file in `:speech` (`ReadingVoices`), read
   by the playback service for each sentence; `SET_VOICE` still overrides it for the session. A deleted voice
   stops being any language's choice.
7. **Module edge:** `:app` depends on `:speech` for the Voices screen's row types (already allowed by
   app → playback → speech).

## Alternatives considered
- **Download sherpa-onnx's `.tar.bz2` directly:** needs a bzip2 library (new dependency) and trusts their release
  never changing; rejected.
- **Hugging Face URLs:** per-file downloads with no single hash, and a third-party host for every voice; kept only
  as a manual source for the mirror script.
- **Android `DownloadManager`:** resumes, but cannot verify before exposing the file, reports progress poorly, and
  would need its own receiver; WorkManager is already in the stack for downloads.
- **Foreground worker with a notification:** survives longer per run, but needs `FOREGROUND_SERVICE_DATA_SYNC`
  and a notification for a few minutes' work; resume makes it unnecessary.
- **JSON parsing at runtime (org.json):** works on Android but not in JVM tests without a new test dependency.

## Consequences
- WorkManager's manifest adds `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and its own boot
  receiver; it only reschedules pending downloads, never playback (the "no boot auto-start" invariant holds).
  `:speech` declares `INTERNET`.
- Downloads work only once the mirror zips are on the `voices` release, i.e. after this lands on `dev`.
- Adding a catalog voice = an entry in `models.json` with pins from `build_voice_mirror.py --print-pins`.
- Custom voices are trusted only as far as sherpa-onnx's parser: Audora checks file presence, sizes and counts,
  not model contents.
