# ADR-065: The app icon is the hand-drawn ListenLock logo

**Status:** Accepted (2026-10-24).

## Context
The launcher icon was a vector mark drawn in code (sound bars). A graphic designer drew a logo for ListenLock by hand
(a listener in headphones behind a shield with an "L" and a sound wave). It is the app's own mark; TelosMind Labs, the
developer name on Play, keeps a separate logo. The logo came as one PNG, 743 × 1019 px with a transparent background.

## Decision
- **Launcher icon:** an adaptive icon with the logo as the foreground on a white background
  (`ic_launcher_background` = `#FFFFFF`). The logo is scaled to fit a 33dp radius around its centre, so circle,
  squircle and square masks never cut it (Android shows a 72dp circle of the 108dp canvas; 66dp is the safe zone).
  PNGs per density in `drawable-{m,h,xh,xxh,xxxh}dpi/ic_launcher_logo.png`, made from the master with a high-quality
  downscale; a vector redraw would change the designer's lines, so the bitmap is kept.
- **Themed icon (Android 13+):** `ic_launcher_logo_monochrome.png`, the logo's opaque pixels as one colour; Android
  tints it.
- **Splash (Android 12+):** unchanged code; it shows the launcher icon on the app's paper colour (`splash_background`).
- **Debug builds** use the same icon. The dev APK (`audora-dev.apk`) is the debug build and is what testers install,
  so it must look like the app; the label "ListenLock Dev" tells the two apart. (First version kept the old orange
  bars mark for debug, so the dev APK never showed the logo; removed 2026-10-24.)
- **Store:** `docs/store/logo.png` is the master; `docs/store/icon.png` (512 × 512, logo on white) and
  `docs/store/feature-graphic.png` (the logo next to the name) are made from it.
- **Rights:** the GPL covers the code, not the name or the logo. README says so: the name and logo belong to the owner,
  and forks must use their own.

## Alternatives considered
- **A different icon for debug builds:** tells installs apart at a glance, but the dev APK is the build people test,
  so they never see the real icon.
- **Keep the sound-bars mark:** consistent with the old look, but generic and not the brand.
- **Trace the logo to a vector drawable:** sharper at every size, but it alters hand-drawn lines; the PNGs at
  xxxhdpi (432 px for 108dp) are already sharper than any launcher draws them.
- **Logo on the app's paper colour:** the logo was drawn on white, and its light purple face reads better on white.

## Consequences
- The APK grows by about 0.2 MB (ten PNGs).
- A change to the logo means re-running the export: scale the master into the 66dp circle at each density, make the
  one-colour copy, and re-render `icon.png` and `feature-graphic.png`.
- The designer should give the owner a short written assignment (or an exclusive licence) of the logo's copyright,
  so the owner can register it and act on copies.
