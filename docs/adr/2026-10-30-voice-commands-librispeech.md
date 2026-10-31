# ADR 2026-10-30-voice-commands-librispeech: voice commands use a LibriSpeech-only model

**Status:** Accepted (2026-10-30). The user chose this option after the pre-store licence audit. It follows ADR
060-app-name-listenlock (the phrases) and ADR 051-screen-off-voice-commands. A licence fix, allowed in the feature
freeze. No new dependency.

## Context

The optional voice-commands download (plan item 38) was k2-fsa's 3.3M-parameter Zipformer keyword spotter, trained
on **GigaSpeech**. The weights are Apache-2.0, but GigaSpeech's terms limit the data to non-commercial research. A
model trained on it is the one licence question the audit could not close for a store app. It is a risk that a
larger company could point to.

Replacements tested on a desktop (sherpa-onnx 1.13.8, the app's keyword-spotter settings). Each was played 36 spoken
commands in two synthetic voices at normal and fast speed, plus 12 clips of plain speech with no command:

| Model | Training data | Download | Commands caught | False triggers | CPU (1 thread) |
|---|---|---|---|---|---|
| GigaSpeech KWS 3.3M (old) | GigaSpeech (non-commercial) | 5.3 MB | 29 / 36 | 0 | 0.026 × real time |
| Tutor's LibriSpeech 20M | LibriSpeech | already shipped | 0 / 36 | 0 | n/a |
| LibriSpeech-960h streaming Zipformer 2023-06-26 | LibriSpeech (CC BY 4.0) | 70.9 MB | 29 / 36 at 0.3 (30 at 0.2) | 0 | 0.079 × real time |

The tutor's smaller model cannot decode short single words like "pause" and "next", and its vocabulary lacks
`▁PLAY` and `▁NEXT`. Reusing it would have cost nothing, but it does not work.

## Decision

- `KeywordModel.ENGLISH` is `sherpa-onnx-streaming-zipformer-en-2023-06-26` (icefall PR 1058, Apache-2.0, trained on
  LibriSpeech 960 h only). Its int8 encoder, decoder and joiner, `tokens.txt` and README are mirrored as
  `kws-en-zipformer-librispeech-e08608953a6b.zip` by `scripts/build_voice_mirror.py` from the pinned upstream archive.
- `VoiceCommand`'s phrases are re-cut into this model's BPE pieces with its own `bpe.model` (sentencepiece):
  "ListenLock" is `▁LI S TEN LO CK` / `▁LI S TEN ▁LO CK`, and "stop", "play", "next", "resume" and "slower"
  changed pieces. `KeywordTokensTest` checks every piece against the shipped `tokens.txt`, because sherpa-onnx ends
  the process on an unknown piece.
- `VoiceCommandConfig` is unchanged: threshold 0.3, one thread. 0.2 caught one more command in the test, but a lower
  threshold raises the chance that the book's own voice triggers a command.
- The Licences screen credits LibriSpeech for the model.

## Consequences

- **Download:** 5.3 MB becomes 70.9 MB. It is still optional and only offered in Settings.
- **CPU and battery:** about 3× the decode cost. The energy gate still skips quiet chunks, but while the book plays
  aloud most chunks are decoded. On the desktop that is under a tenth of one core. A phone core is slower, so expect
  a few times that.
- **Memory while listening:** about 135 MB resident for the spotter instead of about 50 MB, freed when the reader
  closes. Nothing stays loaded otherwise.
- **Phones that had the old model:** the new pin is a new folder, so the old model no longer counts as installed.
  Settings offers the new download, and installing it removes the old folder. Delete in Settings clears both. There
  is no migration code: the app is not on a store yet.
- **Accuracy:** the same as before on the desktop test. It must be confirmed by ear on the test phone, including
  with the book reading aloud through the speaker.

## Alternatives

- **Keep GigaSpeech.** No size change, but it leaves the only open licence risk in the Play app.
- **Remove voice commands until a small clean model exists.** Clean and smallest, but it drops a working feature.
- **Train our own small spotter on LibriSpeech.** Needs GPU time and a training pipeline the project does not have.
  This is the way back to a small download later.
