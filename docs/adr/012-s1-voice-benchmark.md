# ADR-012: S1 outcome: sherpa-onnx vendoring, voice candidates, English default rule, test phone as reference phone

**Status:** Accepted for the build setup and candidate set (2026-10-15, S1). The English default is chosen by the
rule below once the phone run is in. Evidence: `research/spikes/S1-voice-benchmark/REPORT.md`.

## Context
Spec §3 left the bundled English voice, the premium voices and the Hindi/Bengali neural candidates to S1. It
listed Piper lessac/amy or Kitten for English, Kokoro v1.1 as premium, and assumed sherpa-onnx 1.13.8 would come
from Maven. S1 found:
- sherpa-onnx 1.13.8's Android AAR is published only as a GitHub release asset, not on Maven Central or Google
  Maven. Its arm64 libraries are 16 KB aligned.
- Kokoro v1.1 int8 in sherpa-onnx is the Chinese-focused release with 3 English voices and no Hindi; v1.0 int8
  carries af_heart, bf_emma and four Hindi voices. Kokoro is slower than real time even on desktop (RTF 1.2–1.3).
- Piper lessac's training data (Blizzard 2013) is research-only with no redistribution, and amy, hfc_* and
  hi_IN rohan are fine-tuned from it. ljspeech and cori are trained from scratch on public-domain data and
  measure the same speed.
- The Bengali Piper and MMS voices are not prebuilt for sherpa-onnx; they convert cleanly (`convert_bn_voices.py`).
- Spec D4 names the second test phone as the reference phone; it is a friend's phone, available only before a release.
  The maintainer's dev phone is the test phone (a mid-range chip), a harder target.

## Decision
1. **sherpa-onnx is vendored from its GitHub release.** `settings.gradle.kts` declares an `exclusiveContent` Ivy
   repository for `com.k2fsa:sherpa-onnx` only, pointing at `github.com/k2-fsa/sherpa-onnx/releases/download/`.
   The AAR's SHA-256 is pinned in `libs.versions.toml` and checked before every build. Modules that use it exclude
   `libsherpa-onnx-c-api.so` and `libsherpa-onnx-cxx-api.so` (the JNI library does not load them).
2. **Voice models are served from our own GitHub release**, repacked as zip with a `SHA256SUMS.txt` the app
   verifies (spec §9 "mirror to own Release"). S1 uses the `s1-voices` pre-release; P4-01 adopts the same layout.
3. **Candidate set:** Kokoro **v1.0** int8 replaces v1.1. Piper English candidates are **ljspeech** and **cori**.
   Voices trained on or fine-tuned from the Lessac/Blizzard data (lessac, amy, hfc_*, hi_IN rohan) are never
   bundled, downloaded or mirrored. Supertonic 3 is measured for English too.
4. **English default rule** (closes S1 when the phone report arrives): the bundled voice is the Piper medium
   voice (ljspeech or cori) the maintainer rates higher in the blind test. KittenTTS v0.8 nano replaces it only if
   it rates higher than both **and** measures RTF ≤ 0.5 with 2 threads on the test phone (it must leave headroom for
   the player, UI and PDF pipeline). Kokoro stays premium-only under the spec's existing rule (offered only where
   measured RTF < 0.8); on the desktop evidence it will not qualify on the test phone.
5. **Hindi and Bengali** neural voices stay download-only. P3-06 picks among priyamvada, pratham, Supertonic
   (Hindi) and Piper bn google, MMS (Bengali) from the phone report and S0, and shows attributions for the
   CC BY-SA / BY-NC-SA / BY-NC voices.
6. **Reference phone for development is the test phone.** Performance targets and gates are measured
   there. The second test phone is a pre-release check only (D4 amended).

## Alternatives considered
- **Build sherpa-onnx from source in CI:** full control, but a long NDK + ONNX Runtime build and our own 16 KB
  work for a prebuilt artifact that is already aligned and checksum-pinned. Rejected.
- **Commit the AAR (50 MB) to the repo:** simplest resolution, but bloats every clone and history forever.
  Rejected; the pinned SHA gives the same reproducibility.
- **Download sherpa-onnx's `.tar.bz2` models on the phone:** needs a bzip2 decoder (new dependency) and has no
  checksum list to verify against. Rejected for the mirror.
- **Keep lessac as the default** (the most common Piper voice): its data licence forbids redistribution, and an
  APK that bundles it is redistribution. Rejected.

## Consequences
- Builds need github.com reachable for the first resolution of the AAR (Gradle caches it); a moved or re-uploaded
  asset fails the checksum loudly rather than silently changing the runtime.
- P1-03 bundles one Piper voice (~64 MB model + 19 MB espeak-ng-data) or Kitten (~28 MB + espeak-ng-data).
  espeak-ng-data is identical across voices; P4-01 should store it once.
- P2-04's normalizer must expand numbers, currency and abbreviations before every engine: Supertonic has no
  normalizer of its own and produces nonsense on them.
- Spec §3 table, §11, D4 wording and `docs/PROGRESS.md` updated accordingly.
