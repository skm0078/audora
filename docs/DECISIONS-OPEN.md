# Audora — Decisions (all settled, locked 2026-10-14)

Updated 2026-10-14 with Swarnakeshar's answers.

## Project-wide rule (from the user)
**No cloud services and no paid services.** Everything runs on the phone; local self-hosting is the maximum. Free infrastructure that only builds or hosts our own files (public GitHub repo, GitHub Actions on a public repo, GitHub Releases, Hugging Face model downloads) is allowed because it costs nothing and never receives user text.

## Settled

| # | Decision | Answer | Consequence |
|---|---|---|---|
| D1 | GitHub repo | **Public, GPL-3.0** | Repo `skm0078/audora` created at P0-01. |
| D3 | Build/dev setup | **Your phone for testing; CI builds the APKs** | No Android Studio needed. For logs and the Android 17 audio test, install Google's free "SDK Platform-Tools" (adb only, ~10 MB) on Windows; wireless debugging works without a cable. |
| D4 | Target phones | **OnePlus 12 (Snapdragon 8 Gen 3, Android 16)**; friends on phones ≤ 2–3 years old | Kokoro premium English is realistic on your phone (published RTF ~0.3–0.7 on this chip). OxygenOS/ColorOS battery steps ship first in onboarding. |
| D5 | Oldest Android | **minSdk 31 (Android 12), arm64-v8a only** | Covers phones from ~2021 onward with margin; drops pre-12 compat code. |
| D6 | Hosted-AI page cleanup | **Dropped** (violates the no-cloud rule) | Removed from v1. A possible post-v1 experiment: an on-device small model, only if the deterministic pipeline proves insufficient. |
| D2 | OCR engine | **One app; open-source OCR only, no ML Kit** | Tesseract + PaddleOCR ONNX (Apache-2.0) on the ONNX Runtime we already ship; S2 picks per script. **S2 result: Tesseract for eng/hin/ben; PaddleOCR Hindi optional (ADR-011).** |
| D8 | Reverse engineering (REA, morluto/rea) | **Use freely on open-source references and our own APK; closed apps (Adobe) by outside observation** | Reference set: Librera Reader (GPL PDF reader with read-aloud, confirm licence before reuse), VoxSherpa-TTS, sherpa-onnx TTS engine app, our CI APK. |
| D7 | Package name | **`io.github.skm0078.audora`** | Permanent once installed/registered. |
