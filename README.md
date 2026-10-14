# Audora

A free, offline Android app that reads PDFs aloud in natural voices: English first, then Hindi and Bengali.
Start from any page, pick a voice, and keep listening with the screen off or in other apps.

**Status:** scaffolding (Phase 0). Nothing to read aloud yet. See [docs/PROGRESS.md](docs/PROGRESS.md).

## Principles
- Everything runs on the phone. No accounts, no analytics, no cloud, no paid services.
- Free and open source under [GPL-3.0](LICENSE).
- Android 12+ on 64-bit ARM phones.

## Try a build
On your phone, download the latest dev build:
**https://github.com/skm0078/audora/releases/download/dev-build/audora-dev.apk**
It is rebuilt on every change to the `dev` branch. Every other branch's APK is under
**Actions → Android CI → (run) → Artifacts**.

## For contributors
Read [AGENTS.md](AGENTS.md), then [docs/BUILD-SPEC.md](docs/BUILD-SPEC.md) and [docs/TICKET-PLAN.md](docs/TICKET-PLAN.md).
Quick check: `./gradlew ktlintCheck detekt test assembleDebug` (needs JDK 21 and the Android SDK).
