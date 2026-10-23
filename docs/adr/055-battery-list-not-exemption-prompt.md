# ADR-055: Battery onboarding opens Android's optimization list, not the one-tap exemption prompt

**Status:** Accepted (2026-10-23). Amends ADR-015 decision 5.

## Context
ADR-015 had "Allow background reading" open the system's one-tap "Let app always run in background?" prompt, which
needs the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission. Audora was only sideloaded then. It now ships on Google
Play, whose policy (Android "Optimize for Doze and App Standby", updated 2026-10-22) prohibits asking for the
exemption unless Doze or App Standby breaks the app's core function. Its acceptable-use table lists chat and VoIP
apps without push messaging, safety apps, task automation and device companions; media players are not on it.
Playback already runs in a `mediaPlayback` foreground service, and OEM background killers (OnePlus, Xiaomi, vivo)
are governed by their own settings, which the per-brand text steps cover.

## Decision
1. Drop `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` from the manifest, for every build (Play and GitHub share one APK
   signature and one source).
2. "Allow background reading" opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no permission needed), falling
   back to Audora's app info page. A hint under the button says how to find Audora in that list.

## Consequences
- One or two extra taps for the user; the exemption state check (`isIgnoringBatteryOptimizations`) and the status
  line are unchanged.
- No Play review risk from this permission. Re-adding it later needs a new ADR and a core-function argument.
