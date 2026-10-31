# kws test data

`librispeech-2023-06-26-tokens.txt` is `tokens.txt` from the model Audora ships for voice commands, unchanged. `KeywordTokensTest` checks every command phrase against it.

- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
  - Archive sha256: `639e25b578e9e997131402199419c13a941f8e4e198e2da1ce57dbf5cf401282` (the same as `speech/models.json`).
- File sha256: `49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb` (502 lines).
- Licence: Apache-2.0 (k2-fsa, trained with icefall on LibriSpeech, CC BY 4.0). The app's Licences screen credits the model.

If the model in `models.json` changes, replace this file from the new archive and update the hashes above.
