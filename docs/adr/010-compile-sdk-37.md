# ADR-010: compileSdk 37, targetSdk stays 36

**Status:** Accepted (2026-10-14, P0-01)

## Context
Build Spec §4 pinned compileSdk 36 and targetSdk 36. The current AndroidX releases pinned in P0-01
(core 1.19.1, Compose BOM 2026.09.00, lifecycle 2.11.0, activity 1.13.0) declare in their AAR metadata
that consumers must compile against API 37. The first CI build failed `checkAarMetadata` for this reason.

## Decision
Compile against API 37. Keep targetSdk 36 and minSdk 31.

## Alternatives
- Pin older AndroidX releases that accept compileSdk 36: stale libraries on day one, and the same bump returns at the next update.

## Consequences
compileSdk only controls which APIs the code can reference at build time; it changes no runtime behavior.
Runtime behavior follows targetSdk, which stays at 36 until the Android 17 while-in-use rule is tested (spec §4).
Code must still guard any API 37 call with an SDK_INT check, since phones run Android 12 to 16.
