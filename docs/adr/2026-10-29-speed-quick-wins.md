# ADR 2026-10-29-speed-quick-wins: load the voice before Play, draw the first screen once, lighter turns and rows

**Status:** Accepted (2026-10-29). The first PR of the on-phone speed audit (items 1, 4, 5, 7 and 10 of the ranked
list), approved by the maintainer. None of it removes a feature or changes how a voice sounds.

## Context

An audit of dev at 8c13ad0 and of the published dev APK found time still spent where the listener waits:

- **The first Play waits for the voice.** The voice model (the bundled English voice is a 41 MB file) is loaded
  inside the first sentence's render, so every first Play after opening a book waits for it. Share logs show it as
  the `first` line of `Audora.RenderSpeed`.
- **The first screen is built twice.** `MainActivity` drew its first frame with the default look while the saved one
  (dark mode, colours) was still being read, then built the whole screen again in the saved look.
- **The start of a page turn can stop for a copy.** A page drawn ahead is a software bitmap (8 to 10 MB at phone
  width). It was copied to the graphics chip only when a curl first showed it, on the drawing thread. Each curl frame
  also made four new outlines (paths) for the collector to clean up mid-turn.
- **Every library row redraws on every update.** `Book` and `BookProgress` live in `:core`, which is compiled without
  the Compose compiler, so Compose cannot tell an unchanged book from a changed one and recomposes every visible row
  each time the list is published (a file check, a reading position, a shelf change).
- **Unused resources ship.** R8 shrinks code but resource shrinking was off.

## Decision

1. **Load the voice while the book is open** (`ReaderVoiceWarmUp` in the reader, `VoiceWarmUpCommand` from reader to
   service, `VoiceWarmUp` in the service). Once the shown page's sentences are known, the reader waits
   `ReaderConfig.voiceWarmUpDelayMs` (1 s), then, if the player is connected and nothing is playing, asks the service
   once to load the voice for that page's language. The service picks the voice as reading will (the book's own
   voice while installed, else the language's voice) and loads it when it is a sherpa-onnx model, by rendering empty
   text, which loads the model and speaks nothing. Phone voices are already bound by finding them; the acted Hindi
   voice is large and opt-in, so it is left alone. The command never starts, changes or queues playback, so it is
   not a user action and C7 still holds.
2. **Draw the first screen once** (`FirstFrameHold.kt`). The window's first frame is held, so the system splash stays,
   until the saved look is read, at most `StartupConfig.appearanceWaitMs` (300 ms; the default look after that).
   The screen is composed only once the look is known. The hold itself ends after
   `StartupConfig.firstFrameMaxHoldMs` (600 ms) by the main thread's clock whatever happens, because Compose's clock
   stands still under a UI test while a frame is held, so a limit measured by Compose alone never ends the hold there.
3. **Lighter page turns.** A page bitmap starts its copy to the graphics chip as soon as it is drawn
   (`Bitmap.prepareToDraw`, on the render thread). `CurlFrame` keeps its four outlines and traces them again each
   frame instead of making new ones.
4. **Library rows redraw only when their book changes** (`app/compose-stability.conf`, wired in the application
   convention plugin). `Book`, `BookId` and `BookProgress` are listed as stable: every property is a read-only value
   of an immutable type. Listing a type that changes in place would show stale rows, so the file says so.
5. **Resource shrinking on** for release and dev builds (`isShrinkResources`). Nothing looks a resource up by name.

## Performance cost

- Voice warm-up: the model's memory (60 to 150 MB) is used about a second after a book opens instead of at Play, even
  if the listener only reads with their eyes. It is the same memory Play would use, given back on the same trim and
  release as before (`SpeechConfig.maxLoadedVoices`). A one-time CPU burst of about a second to load it. Nothing when
  something is playing, nothing for books flicked past in under a second, nothing for phone voices.
- First frame: the splash stays up to the read of the saved look (tens of milliseconds); bounded at 300 ms
  (600 ms at the very most).
- Page turns: the graphics chip memory for the two neighbour pages is taken when they are drawn ahead rather than at
  the turn (the same amount).
- Stability config and resource shrinking: none at run time; resource shrinking adds a few seconds to a release build.

## Alternatives considered

- **Warm the voice when the app starts.** Faster still for the first book, but loads 60 to 150 MB for every launch,
  including the many that never play. Rejected.
- **Warm on every page turn or language change.** Bilingual books would load a second model the listener may not
  need. One warm-up per open book, for the page shown, covers the common case.
- **`androidx.core:core-splashscreen` for the first frame.** Same effect, one more dependency; the platform's
  pre-draw hook does the job.
- **Mark all of `kotlin.collections` stable.** A wider promise than this change needs; only the library's row types
  are listed.

## Consequences

- New custom command `VoiceWarmUpCommand.ACTION`, offered to Audora's own controllers only. `PlaybackPort.warmVoice`
  defaults to doing nothing, so fakes and other ports need no change.
- Release journey step 56 opens a book with nothing playing and waits for the service's "Warmed voice=" log line,
  then checks reading did not start.
- `Loaded voice=… loadMs=…` (sherpa engine) and `Warmed voice=…` (`Playback.VoiceWarmUp`) in Share logs show the
  warm-up on a phone; the first `Audora.RenderSpeed` line after it should no longer carry the load time.
