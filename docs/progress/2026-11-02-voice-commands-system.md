# Voice commands use the phone's own speech recogniser (2026-11-02, ADR 2026-11-02-voice-commands-system-recogniser)
- **Problem (test phone):** with the 70 MB LibriSpeech model listening, only "ListenLock, pause" was ever heard, and
  only sometimes; "play", "next" and the rest never. The logs showed the microphone open, so it was recognition,
  not wiring: the model was trained on American audiobook readers and misses Indian-accented English. Nothing in
  the reader said whether it was listening at all.
- **Change:**
  - `speech/kws`: `CommandListener` now drives Android's on-device `SpeechRecognizer` (en-IN, then en-US),
    `RecognitionLoop` keeps it listening session after session, `CommandPhrases` finds "ListenLock, <command>" in
    its text (with the near spellings an accent produces). The sherpa-onnx spotter, its model download, energy gate
    and token check are removed. `VoiceCommandConfig` keeps only what the recogniser needs.
  - Settings: one switch, no download; disabled with "Not available" on a phone without an on-device recogniser.
  - Reader and player: a line under the time left says "Listening" only while the recogniser has the microphone,
    or why it can't listen (`VoiceCommandsLine`, `ReaderState.voiceCommands`).
  - The old model folder is deleted at start (68 MB back). Storage, Jobs, the voice mirror catalogue and the
    licences list no longer name it. The manifest declares the `RecognitionService` query Android 11+ needs.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-11-02-voice-commands-system.txt`. `:pdf` not changed. No
  new dependency.
- **Perf cost:** no app-side decode or 135 MB model in memory while listening any more; the phone's recogniser does
  the work instead, and its battery cost is the phone's (to be checked by ear and battery on the test phone). APK
  size unchanged; 68 MB of storage freed where the old model was downloaded.
- **Could affect:** voice commands (on screen and screen-off), Settings → Advanced, Storage, Jobs, the reader's and
  player's header lines, the tutor and voice notes (they still pause voice commands while they record).
- **Verified in CI:** `CommandPhrasesTest` (every command and spelling, never without the name, one-word gap, order),
  `RecognitionLoopTest` (once per command across partials, retries, giving up with the reason, a hold stops the
  session and listening resumes, the book's own "ListenLock"), `VoiceCommandsStatusTest` (the line's states),
  `SettingAnchorTest` (unavailable phone, rows stack), release journey step 62 (one switch, nothing to download).

**Real-device checks (the test phone):**
1. You → Advanced: "Listen for voice commands" has no download row above it. Turn it on and allow the microphone.
2. Open an English or Bengali book and play it. Under the time left: "Listening · say …", and Android's mic dot.
3. Say "ListenLock pause", then "ListenLock play", "next", "back", "repeat", "faster", "slower" (each after
   "ListenLock"). Each should act once, with a short beep. Note whether the phone also beeps on its own between
   commands.
4. Let a chapter play for 10 minutes without speaking: nothing should act. Check Android's battery page for
   ListenLock afterwards.
