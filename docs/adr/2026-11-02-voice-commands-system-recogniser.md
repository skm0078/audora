# ADR 2026-11-02-voice-commands-system-recogniser: voice commands use the phone's own speech recogniser

**Status:** Accepted (2026-11-02). Decided by the maintainer after the phone test of the LibriSpeech model.
Supersedes ADR 2026-10-30-voice-commands-librispeech. A fix allowed in the feature freeze: the feature did not work
for its own maintainer.

## Context

On the test phone, with the 70.9 MB LibriSpeech model installed and listening (the logs show the microphone open
and "Listening"), only "ListenLock, pause" was ever recognised, and only sometimes. "Play", "next" and every other
command never were. A desktop test with synthetic voices found the same: the model caught nearly every command in
American and British voices, and missed about half in Indian-accented English. It was trained on LibriSpeech only
(American audiobook readers). A small, accent-blind model the listener cannot use was also the costliest choice:
a 70 MB download and about three times the decode cost of the earlier 5 MB spotter.

## Decision

1. **The phone's own on-device recogniser** (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, Android 12+, the
   app's minSdk) listens, with partial results, on-device only (`EXTRA_PREFER_OFFLINE`). Languages are tried in
   order: `en-IN`, then `en-US`. On Android 13+ a missing speech pack is asked for once
   (`triggerModelDownload`), and the reader says so.
2. **Matching is text.** `CommandPhrases` finds the app's name (as the recogniser writes it: "listenlock",
   "listen lock", and near spellings a strong accent produces, such as "listen log") followed within one word by a
   command word or a near spelling ("paws" for "pause"). A command word without the name never acts, so the book's
   own voice through the speaker still cannot drive the player. `NarratorEcho` and `CommandRepeats` are kept.
3. **`RecognitionLoop`** (pure Kotlin, tested) runs sessions back to back, acts once per command across growing
   partial results, stops a session at once for a microphone hold (`VoiceCommandPause`, the tutor and voice
   notes), retries a failed session after `failureRetryMs`, and stops listening, with the reason, after
   `maxFailuresInARow`.
4. **Nothing to download.** The model row, its download worker, storage row, mirror entry, licence credit and
   journey step are removed. Builds before this one left the model in `noBackupFilesDir/voice-commands`; the app
   deletes it at start (`CommandListener.forgetOldModel`), freeing 68 MB.
5. **A phone with no on-device recogniser** (no Google speech services) shows the switch disabled with
   "Not available"; voice commands are simply absent there. The maintainer accepted this.
6. **The reader says whether it listens.** A line under the time left shows "Listening · say "ListenLock, pause""
   only while the recogniser really has the microphone, "getting ready" before that, and the reason when it cannot
   listen (no permission, no recogniser, speech pack downloading, microphone busy or lost). TalkBack reads changes
   politely.

## Alternatives considered

- **Keep the LibriSpeech model and lower its threshold.** Cheaper to try, but it does not fix the accent gap the
  data causes, and keeps the 70 MB download and the decode cost.
- **Train our own small keyword spotter** (LibriSpeech plus synthetic Indian-accented speech, about 5 MB). The way to
  voice commands on phones without Google; needs GPU time and a training pipeline. Left for after 0.1.0.
- **Headphone and Bluetooth buttons.** Already work with no microphone at all; they complement voice commands.

## Consequences

- No app-side model, download, or native decode for commands. The recogniser is the phone's: its accuracy, battery
  cost and whether it beeps when a session restarts depend on the phone and are checked by ear on the test phone.
- F-Droid: no new library; the recogniser is an Android framework API, absent on phones without one.
- Privacy is unchanged: on-device only, nothing recorded or sent by the app. The manifest declares a `queries`
  entry for `android.speech.RecognitionService`, which Android 11+ needs to let an app see the recogniser.
- Release journey step 62 now checks that voice commands are one switch with nothing to download, and that the
  switch says it cannot listen exactly where the phone has no on-device recogniser.
