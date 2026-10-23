# ADR-038: Clean-room study of closed listening apps with rea (widens D8)

**Status:** Accepted (2026-10-17, maintainer's decision in the project chat).

## Context
D8 allowed reverse engineering (rea, github.com/morluto/rea) only on open-source references and our own APK; closed
apps such as Adobe Acrobat could only be observed from outside. The maintainer wants Audora to match the convenience
of the leading listening apps (Audible, Speechify, ElevenReader and similar) and asked to use rea on them for real.

## Decision
1. **Scope widened.** rea may inspect the current APKs of closed listening and read-aloud apps that the maintainer
   installed from the Play Store on their own phone: manifest declarations, permissions, string tables and other
   resources, and decompiled class and method names where needed to understand *what a feature does*.
2. **Clean room.** Only features and observable behaviour are recorded, in plain words, in `research/`. No code,
   assets, strings, layouts, models, voices or APIs of those apps are copied into Audora; whoever implements a
   feature works from the written description and our own design.
3. **Hands off protection.** DRM, licence checks, encryption and account or server APIs are never analysed,
   patched or bypassed. Nothing is distributed: pulled APKs and decoded output stay on the maintainer's PC and are
   never committed or shared.
4. **Open-source players first.** GPL-3.0 apps (Voice, AntennaPod, Librera Reader) are studied at code level and,
   being licence-compatible, may be reused with attribution when that is cleaner than rewriting.
5. The procedure (rea `pull-adb-package` and `decode-android-resources` on the maintainer's PC) and the findings
   live in the project's research folder, outside the repo: `research/rea-app-study/pull-and-decode.ps1` and
   `research/05-listening-app-features.md`.

## Alternatives considered
- **Keep D8 as it was (outside observation only):** legally the safest, but the maintainer rejected it.
- **Full decompilation and porting of closed-app logic:** fastest route to parity, but copying code is copyright
  infringement and would put the GPL release at risk. Rejected.

## Consequences
- The maintainer accepts the legal risk that inspecting a closed app may break its terms of service; the risk is
  kept small by reading resources only, keeping the outputs private, and copying nothing.
- APKs cannot be fetched from the cloud workspace (APK mirrors are blocked), so the closed-app pass runs on the
  maintainer's PC with the phone attached; web research and the open-source code study need neither.
- DECISIONS-OPEN.md D8 points here.
