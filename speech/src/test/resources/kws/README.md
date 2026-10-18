# kws test data

`gigaspeech-3.3M-tokens.txt` is `tokens.txt` from the keyword-spotting model Audora ships for voice commands, unchanged. `KeywordTokensTest` checks every command phrase against it.

- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2
  - Archive sha256: `f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a` (the same as `speech/models.json`).
- File sha256: `fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53` (500 lines).
- Licence: Apache-2.0 (k2-fsa, trained with icefall). The app's Licences screen credits the model.

If the model in `models.json` changes, replace this file from the new archive and update the hashes above.
