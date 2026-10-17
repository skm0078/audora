# ADR-033: Speech speed and pace spoken by the voice, and the app's everyday basics

**Status:** Accepted (2026-10-16). Supersedes Build Spec C6 (speed only through `setPlaybackSpeed`).

## Context
The first hands-on run of the dev build on the Moto Edge 50 Fusion found speed changes smearing words and the pace
hard to follow. Two causes:
- C6 sped every voice up or down by time-stretching the finished audio (Sonic). Slower than 1× and faster than about
  1.5× this blurs consonants; C6 itself allowed an engine-native rate "if quality at >2x is bad".
- sherpa-onnx's `GenerationConfig.silenceScale` defaults to 0.2: every silence longer than 200 ms inside a sentence
  (after a comma, colon or dash) is cut to a fifth (`GeneratedAudio::ScaleSilence` in `offline-tts.cc`, v1.13.8).
  Audora never set it, so clauses ran together.
The research behind the choices below is in the project's `research/05-read-aloud-best-practice.md`.

## Decision
1. **Keep the voice's pauses.** `silenceScale = 1.0` on every sherpa-onnx render.
2. **Hybrid speed.** One listening rate, 0.5×–3× in 0.05 steps (`SpeechStyleConfig`). Up to `maxModelRate` (1.5×)
   the voice model itself speaks at that rate (VITS/Piper length scale, Kokoro, Kitten and Supertonic duration; the
   phone's engine through `setSpeechRate`); above it the model stays at the cap and the player time-stretches the
   rest (Sonic). Slower than 1× is always the model's own. The cap is a starting point from published listening
   tests and is tuned by ear per voice later.
3. **Pace.** Three paces (Short, Normal, Long) set a gap after every sentence and scale each sentence's own pause
   (paragraph, heading, list item; ADR-029). Pauses shrink with the square root of the speed, not in proportion,
   since kept pauses are what keep fast speech intelligible. Normal is about 650 ms after a paragraph.
4. **One owner.** The playback service keeps the style (`SpeechStyleStore`, a small properties file), so reading
   resumed from the lock screen speaks at the listener's rate. The reader sends `SET_SPEECH_STYLE`; the session
   extras carry the style back. A change restarts the sentence playing, like a voice change, because the sentences
   rendered ahead were spoken at the old rate. The slider sends only when the finger lifts.
5. **Render-ahead.** Render speeds are recorded as if spoken at 1× (sample scaled by the model's rate), and the
   render-ahead policy treats the model's share of the speed like the player's (ADR-027), so a rate change neither
   skews a voice's measured speed nor starves the queue.

6. **Library basics.** Each book shows its progress in pages, from its resume anchor (`BookProgress`; counting
   text would mean extracting every page). The book read most recently gets a "Continue reading" card that opens
   and resumes it, and a mini player under the list shows what is playing with play/pause. Resuming from the card
   is a user action, so C7 holds.
7. **Open with Audora.** Audora accepts `application/pdf` from other apps (VIEW and SEND). Such a grant usually
   cannot be kept across restarts, so when the library cannot keep it the PDF is copied into app storage
   (`filesDir/shared-pdfs`, at most 512 MiB) and the copy is the book; removing the book deletes the copy.
8. **Rewind on resume.** Resuming after a pause steps back by whole sentences, never seconds: 20 s or more restarts
   the sentence that was cut off, 5 min or more also replays the one before (`ResumeRewindConfig`). A pause at a
   sentence end (sleep timer) steps one sentence less; a seek while paused, or a new position, cancels it.

## Consequences
- A speed change costs a restart of the current sentence (a short gap) instead of being instant. Accepted: clear
  words matter more, and the slider sends once per drag.
- PIPELINE_VERSION is unchanged: `:pdf` output is the same; only rendering changed.
- Shared PDFs that cannot be kept use storage twice (the other app's and Audora's copy). Accepted: otherwise the
  book is lost on the next restart.
- External controllers (a car, a watch) still see and may set the player's speed; Audora's own controls do not use it.
