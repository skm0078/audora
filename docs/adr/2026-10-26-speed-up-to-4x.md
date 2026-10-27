# ADR 2026-10-26-speed-up-to-4x: speed up to 4x, with smart speed always on above 3x

**Status:** Accepted (2026-10-26), pending the maintainer's listening check of the 4x samples. Widens the range of
ADR-033 (0.5x to 3x); keeps its model cap (1.5x) and ADR-049's smart speed unchanged below 3x. Ticket CF-6.

## Context
Speechify, Voice Dream and @Voice offer 4x or more, and Audible 3.5x. Listeners used to fast speech ask for it; most
people stay at 1x to 2x. ListenLock stopped at 3x.

The voice model speaks up to 1.5x itself (ADR-033); everything above is a time-stretch of its output. At 4x that
stretch is 2.67x. A model pushed further swallows syllables: Bruno rendered at 2.0x and stretched 2x lost a third of
its words to a speech recogniser (Whisper base.en, the samples slowed back to normal pace first).

Measured on one English paragraph read by Bruno (the bundled voice), same method:

| Speed | Even stretch (stand-in for the player's Sonic) | Smart speed (the app's own code) |
|---|---|---|
| 2x | no loss | no loss |
| 3x | no loss | no loss |
| 3.5x | about 1 word in 25 lost | about 1 word in 9 lost |
| 4x | about 1 word in 6 lost, "cannot" heard as "can" | about 1 word in 8 lost, "cannot" kept |

Losses are counted above the 1x reading's own misses (numbers written as digits). The paragraph has 61 words, so
one word is about 2 points and the two columns are within a few words of each other; at 3.5x the even stretch even
did slightly better. The recogniser is a proxy, not a listener: it has never heard fast speech, and the slow-back step adds its own
smear. The decision rests on the maintainer's ear on the phone; the samples are in the project files
(`audora/speed-4x/`).

## Decision
1. `SpeechStyleConfig.maxRate` is 4x. The quick-pick speeds (0.75x to 2.5x) and the default (1x) do not change, so
   nobody reaches 4x without dragging the slider there.
2. Above `smartAlwaysAbove` (3x) smart speed carries the stretch whatever its switch says: it shortens silences and
   held sounds and spares consonants, which is what keeps negations like "cannot" intact. Up to 3x the switch decides,
   as before. `asHeard` reports smart speed as on there, so flipping the switch above 3x restarts nothing, and a
   "What did it say?" replay of a forced 4x keeps smart speed at its slower rate.
3. Render-ahead needs no change: the service already feeds it model rate × voice stretch × player speed, so at 4x
   it renders ahead four times as much audio.

## Alternatives
- **Stop at 3.5x.** Loses a little less; same code with `maxRate = 3.5`. The fallback if 4x sounds muddy.
- **Raise the model cap to 2x.** Worse: the model drops syllables before any stretch.
- **4x with the player's even stretch.** Measured worse than smart speed at 4x.

## Consequences
- At 4x the phone must render about 2.7 times faster than it plays the model's 1.5x audio. A voice that cannot keep
  up waits at the start (the start hold, ADR-027) instead of gapping. The phone's `Audora.RenderSpeed` log lines
  (ADR-072) show whether it keeps up.
- The smart speed switch can read "off" above 3x while smart speed acts; its help text says it is always on there.
