# ADR-049: Smart speed, an uneven stretch of the voice past the model's top speed

**Status:** Accepted (2026-10-18). Plan item 10. Amends ADR-033's speed split when the listener turns it on.

## Context
ADR-033 splits the listening speed: up to `maxModelRate` (1.5×) the voice model speaks faster itself; past it the
player (Sonic, in Media3) time-stretches the rest evenly. An even stretch shortens a consonant burst as much as a
held vowel or a silence, so at 2× and above plosives and fricatives get smeared and the words blur. Listeners of
podcast apps know the fix from "smart speed" features: take the time out of the parts that carry the least.

## Decision
1. **A setting, off by default.** `VoiceShaping.smartSpeed` (one switch in the speech sheet's sound section, saved
   with the rest of the shaping). Off keeps ADR-033 exactly. It is judged by ear in the final phone session before
   any default changes.
2. **The renderer stretches, the player stays at 1.0.** With it on, `SpeechStyleConfig.playerSpeed(style)` is 1.0
   and `voiceStretch(style)` is what the player would have stretched. The model still speaks at `modelRate`.
3. **Per voiced piece, after pause trim, before Audora's pauses.** `EngineRenderer` composes the stretch with pause
   trim in `SpeechRequest.shape`, which runs on each voiced piece before clause pauses and the silence after the
   sentence are added. Audora's own pauses therefore keep their computed length, which ADR-033 already shrinks with
   the square root of the speed; `silenceAfterMs` and `clauseSilenceMs` no longer pre-lengthen them for a player
   stretch that does not happen.
4. **The stretch** (`speech/audio/SmartStretch`, tunables in `SmartSpeedConfig`): every 10 ms frame is judged
   silent (35 dB or more below the piece's loud level, its 90th-percentile frame, so a quiet voice keeps its weak
   consonants), changing (a level jump over 6 dB: an onset or release, where plosives are) or steady (held vowels,
   hums, and a fricative's steady hiss). A held-sound frame next to an onset or release is spared with it (a guard
   frame each side). Every frame gets a floor of a quarter of the extra speed; the rest is spread by weight (silence
   1, steady 0.25, onset 0), capped at 4× per frame, and solved by bisection, each frame counted at its real length,
   so the piece ends up exactly `1/factor` as long. If the spread cannot reach the factor because too many frames
   are protected for the cap, the fallback stays graded: unprotected frames stay at the cap and the protected ones
   share the time that is left, so they still run slower than the factor. Only when there is nothing to spread (no
   weighted frames, or a cap at or below the factor) does every frame take the factor (an even stretch, as before).
   Each fallback logs its factor, the protected share and the protected speed (no text), so a blurred line can be
   traced to it. The time map then drives a WSOLA overlap-add (20 ms
   Hann windows, 50% overlap, ±8 ms alignment by normalised correlation searched outward from the nominal place; a
   window below −50 dBFS stays at its nominal place), which keeps the pitch down to 80 Hz voices. The piece's last
   4 ms fade to zero, so a cut mid-wave never clicks into the pause after it. Tuned against a line-by-line
   simulator on synthetic signals (at 2×: silences about 2.7–3×, held sounds 1.6×, onsets 1.25×). Those figures
   come from synthetic tones and silences, not real speech; on real voices the protected share runs higher, so they
   are a design target that the phone-session listening checks must confirm. The ±8 ms search is pinned by a test:
   with ±5 ms a five-harmonic 80 Hz voice loses over 1% of its energy off the harmonic lines at 1.33×.
5. **Speed bookkeeping follows.** The render-ahead drain rate multiplies in `voiceStretch`, and the render speed
   tracker is given `modelRate × voiceStretch`, so its normal-rate audio seconds stay comparable across settings.

## Alternatives considered
- **Sonic with a varying speed.** Media3's `SonicAudioProcessor` takes one speed per playback-parameters change;
  changing it every 10 ms through the player would cost a flush each time and is not what the API is for.
- **Silence removal only** (shorten pauses, keep speech even). Pause trim already does part of that; it does not
  help held vowels, and at 2× most of the time is speech.
- **A phase vocoder.** Higher quality at large factors, but phasiness on speech and several times the CPU of WSOLA,
  for a render thread that must stay ahead of playback on a mid-range phone.
- **A new library** (e.g. Rubber Band). GPL-compatible, but native and a new dependency; WSOLA in Kotlin is a few
  hundred lines and has no build impact.

## Consequences
- Rendering costs more CPU with it on (a correlation search per 10 ms hop, about 2–3 M multiply-adds per second of
  audio at 22 kHz); the render-ahead window sizes itself from measured speed, so a slow phone renders further ahead.
- A sentence rendered before the switch changes is re-rendered: the switch is a style change, which restarts the
  sentence like a speed change does.
- The audiobook export is unaffected: it renders at the normal rate, where the stretch is 1.0.
- At or below the model's cap (1.5×) it changes nothing, so switching it there restarts nothing.
- "What did it say?" with smart speed stretching the voice renders its sentence again at the slower speed
  (`SpeechStyleConfig.replayStyle`, `ReplayRender`) and reading rebuilds from it, instead of slowing the stretched
  audio with the player, which would run Sonic over WSOLA output. It costs that sentence's render time before the
  replay starts; with smart speed off or not stretching, the replay stays an instant seek with the player slowed.
- To check by ear (phone-session checklist): 2× and 3× with it on and off, English, Hindi and Bengali voices; listen
  for clicks at piece boundaries and for words running together.
