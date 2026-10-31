# Voice commands use a LibriSpeech-only model (2026-10-30, ADR 2026-10-30-voice-commands-librispeech)
- The optional voice-commands download is now k2-fsa's LibriSpeech streaming Zipformer (Apache-2.0, LibriSpeech
  CC BY 4.0) instead of the GigaSpeech-trained spotter, whose data is non-commercial only. From the pre-store
  licence audit.
- Phrases re-cut into the new model's pieces; the threshold and threads are unchanged.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-voice-commands-model.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). No new dependency.
- **Perf cost:** download 5.3 → 70.9 MB; spotter CPU about 3× (0.026 → 0.079 × real time on one desktop core);
  about 135 MB resident while listening instead of about 50 MB.
- **Could affect:** voice commands (download size, recognition, battery while listening), the voice mirror.
- **Verified in CI:** `KeywordTokensTest` (every phrase against the shipped tokens.txt), `VoiceCommandTest` (pins
  match `models.json`, phrases), the mirror script tests, and release journey step 62.

**Real-device checks (the test phone):** Settings → Voice commands model shows about 71 MB → Download. Turn on Listen
for voice commands, open a book, play it aloud, and say "ListenLock, pause", "ListenLock, play", "next", "back",
"repeat", "faster" and "slower". Each should act once. Let a chapter play for a few minutes without speaking: nothing
should trigger. Battery use while listening should stay reasonable.
