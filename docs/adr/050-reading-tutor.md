# ADR-050: Read aloud to me, an offline reading tutor

**Status:** Accepted (2026-10-18). Plan item 67.

## Context
Audora reads to the listener; item 67 turns it around: the reader reads the page aloud and Audora follows, marking
each word as read, worth checking, or skipped, then sums the page up. It must work offline in English, Hindi and
Bengali, add no library (sherpa-onnx 1.13.8 is already in the app for voices and runs speech recognition too), stay
free, and never keep or log what the reader says.

## Decision
1. **Models, one per language, opt-in downloads** (`speech/models.json` root key `"asr"`, built by
   `scripts/build_asr_mirror.py` into the voice mirror, SHA-pinned like the voices and dictionaries):
   - English: streaming Zipformer transducer (20M, icefall, Apache-2.0), 44 MB. Word by word.
   - Bengali: Vosk small streaming Zipformer2 (Alpha Cephei, Apache-2.0), 94 MB. Word by word. Its licence comes from
     the model card, so the mirror re-checks the card's licence field through the Hugging Face model API before
     building, on every run and in every mode (`licenceCheck`: status 200, no redirect, 30 s timeout), and fails if
     it changes. That step runs on its own (continue-on-error, its own output folder), so a Hugging Face outage
     never stops the voices or dictionaries publishing; the run still ends red.
   - Hindi: no free streaming Hindi model exists, so Dolphin base's CTC branch (DataoceanAI and Tsinghua, Apache-2.0)
     reads a whole phrase at a time, cut by Silero VAD (MIT), 105 MB together. Phrase by phrase: marks appear after
     each pause (at most 8 s of speech). The screen says which mode applies. Dolphin is not told the language, so a
     phrase that comes back mostly in another script (Urdu) is dropped instead of marking every word.
   They install into `noBackupFilesDir/listen-<lang>` through the shared `PinnedZipFiles`/`PinnedZipJob` (the
   dictionaries' downloader, made generic): an `installed` marker written last, files synced first, sizes checked,
   so a cut install reads as missing. sherpa-onnx 1.13.8 aborts the process on some damaged models instead of
   throwing, so a `loading` file marks each load; one left by a load that never returned reports the model as
   failed, with *Try again* and *Download it again*. Credits are in Voices & licences.
2. **Following the reader** (`:core` `tutor`): the page's words (`PageWords`; numbers, symbols and other scripts are
   shown but not judged) are aligned with each heard stretch by a weighted edit alignment (`WordAligner`): a skipped
   line costs less than a row of mismatches, a number may take up to six heard words ("nineteen ninety five"), short
   words must match exactly, longer ones within 70 % similarity. Words stay whole across joiners and soft hyphens;
   Hindi and Bengali are compared without nukta or joiners, with candrabindu as anusvara, long i and u as short, a
   nasal cluster as anusvara (हिन्दी/हिंदी), ये/यी after a letter as ए/ई (गये/गए) and khanda-ta however written
   (উত্‍সব/উৎসব). Short abbreviations ("Mr.", "p.m.") are not judged. A growing stretch is re-marked as it changes;
   only a final one moves the reader's place (`ReadAlong`). A stretch that fits the words just read (up to the
   lookahead before the reader's place) strictly better than the words ahead is a re-read and changes nothing. The
   page is finished once no judged word is left; a final stretch that reaches the last word marks it even if
   misread. The marks survive rotation and process death. The report counts read words over judged ones and lists words to check, which the screen says may
   also be words the model misheard.
3. **Listening** (`:playback` `tutor`): `AudioRecord` at 16 kHz, float, VOICE_RECOGNITION, 100 ms chunks into a 4 s
   queue, so a Hindi phrase being decoded loses no audio; decoding runs off the main thread. Reading aloud pauses
   first (the microphone would hear it); listening stops when the page is read, on *Stop* (the stretch under way
   still counts), or when the app leaves the screen (Android silences a hidden app's microphone). A busy microphone
   (silenced by Android) and a missing permission are reported as such. The microphone is taken through the
   app-wide `MicrophoneGate.shared` (`hold(reason)` returns a handle to close, taken under `NonCancellable`), which
   app basics' `VoiceCommandPause` fills at landing, so voice commands pause meanwhile; voice notes take it too. The
   tutor and voice notes never record together, since each is a modal dialog. The model is freed however the run ends, even when it is cancelled
   while loading, and a native decode error is reported as a damaged model. Nothing heard is stored or logged.
4. **Screen:** *Read aloud to me* in the reader's ⋮ menu opens a dialog over the shown page: the page's text with
   each word styled (coloured when read, red and underlined to check, struck through when skipped; the next word
   shaded), a legend, *Start reading* / *Carry on* / *Stop*, *Start over*, then the summary, words to check as
   buttons that say the word in the book's voice (the quiz's speaker), and *Next page*, which turns the reader's
   page. A model that will not load offers *Download it again*. RECORD_AUDIO is asked for at the first start (the
   manifest line already exists for voice notes); after "Don't ask again" the screen offers *Open settings*.

## Costs
- Storage: 44 MB (en), 94 MB (bn), 105 MB (hi), only for the languages downloaded; deleted from Storage.
- Memory while listening: roughly 150-250 MB (model plus decoder); freed when the dialog closes. The "hear it"
  voice (60-150 MB) is freed before the model loads, so the two are never held together.
- Speed and battery: two decoder threads; the streaming models run faster than real time on a mid-range phone, and
  Dolphin decodes an 8 s phrase in well under its length. To be measured on the test phone in the final phone session
  (accuracy per language, Bengali first, latency and heat) and listed in the phone checklist.

## Alternatives considered
- **Android's SpeechRecognizer:** Google's offline models are not on every phone, send audio online on many, and are
  proprietary (D2, AGENTS.md). Rejected.
- **Whisper (offline, via sherpa-onnx):** good Hindi, but not streaming and several times larger and slower for the
  same accuracy on a phone; a phrase model was needed for Hindi only.
- **A streaming Hindi model:** none with a free licence was found; if one appears, it replaces Dolphin through the
  catalog alone (same `ListenKind.STREAMING_TRANSDUCER` path).
- **Judging pronunciation (phoneme scoring):** needs a forced-alignment model per language and more research; word
  matching gives the reader useful feedback now.

## Consequences
- A word marked to check can be a recogniser mistake, more so for names and rare words; the screen says so.
- Hindi feedback lags a phrase behind; English and Bengali follow word by word.
- The Bengali licence depends on its model card staying Apache-2.0; the mirror build fails loudly if it changes.
