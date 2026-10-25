# ADR-071: Pitch works like speed; option rows never squeeze their titles

**Status:** Accepted (2026-10-25). Narrows ADR-067's pitch range.

## Context
Phone testing of dev 517c524 found two problems.

1. **Pitch felt crude.** It was a bare slider from 0.8 to 1.25 with "Back to the voice's own" under it. The range was
   lopsided, so the voice's own pitch was not in the middle of the track. Nothing was heard until the finger
   lifted. Speed, next to it, has named one-tap presets. The top of the range also sounded like a cartoon. The
   player's Sonic shift moves a voice's formants along with its pitch, so beyond about 15% either way the voice
   stops sounding like itself.
2. **Long values squeezed titles.** Material's `ListItem` measures its trailing slot first and gives the headline
   only what is left. A long value such as "English (US), Kokoro Michael, male (premium)" left the title "English
   voice" one letter wide, one letter per line. Every `LinkOption`, `ChoiceOption` and `OptionBlock` used that
   pattern.

## Decision
- **Pitch**
  - The range is 0.85 to 1.15 in steps of 0.05, with Natural (1.0) in the middle. Saved values outside the range
    are clamped on read, as before.
  - `SpeechStyleConfig.pitchPresets` (0.9, 0.95, 1.0, 1.05, 1.1) are named chips under the slider: Deeper, A bit
    deeper, Natural, A bit higher, Higher. They work like the speed presets, and Natural is the one-tap reset.
  - The slider sends each new step while the finger moves. A pitch change restarts nothing (ADR-067), so the
    listener hears each step at once, and the service saves at most one change per step.
  - A short line under the title says it is heard at once and that speed never changes it. Speed is carried by
    the model's rate or by a time stretch that keeps pitch (`SpeechStyleConfig.playerSpeed`, `voiceStretch`).
  - A book's own pitch is unchanged from ADR-068.
- **Option rows:** `TitleAndTrailing` lays out a title with its value, help and chevron.
  - When the title's first line and the trailing part both fit whole, they share one line.
  - Otherwise the trailing part goes on its own line under the title, and the value wraps once before an ellipsis.
  - `LinkOption` (and so `ChoiceOption`), `OptionBlock` and the collections sheet's Rename and Delete use it.
- **Test:** a device test renders the cast rows with a long voice name, link rows and the pitch block. It checks
  every text at font scales 1, 1.3 and 2, and fails if any line breaks inside a word. It saves screenshots of each
  scale.
- **No `:pdf` change:** PIPELINE_VERSION stays 12.

## Alternatives considered
- **Ellipsize the value on one line.** The title would keep its space, but the voice name, which is the point of
  the row, would be cut to a few letters at large text sizes.
- **Always put the value under the title.** This wastes a line on every short value ("Rain", "1.25×") and makes
  the sheets longer for most people.
- **A formant-preserving pitch shift.** This would allow a wider range, but Media3 has none built in, and adding a
  DSP library costs APK size and work on low-end phones for a setting most people change by a step or two.

## Consequences
- Anyone who saved a pitch past ±15% now hears ±15%.
- New option rows get the adaptive layout for free. Rows that build their own `ListItem` with buttons at the end
  should use `TitleAndTrailing` too.
