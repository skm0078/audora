# ADR-059: An acted Hindi voice for studio mode (Chatterbox, on the phone)

**Status:** Accepted (2026-10-23). Follows ADR-058 and the expressive reading spike; the last Kotlin feature before
the Play release. Asked for as "index and render overnight for better quality; call it out as offline and slow, if the
user really wants it they can wait".

## Context
Hindi reads with Piper or Supertonic: clear, but flat in dialogue. Chatterbox Multilingual (Resemble AI, MIT) acts
feelings and quotes well in Hindi, but it is an LLM-style model (a 0.5 B token model, then an audio decoder): far too
slow to read live on a phone. Studio mode (ADR-039) already renders a whole book ahead, on the charger, resumable,
into a lossless store that playback reads first. So the slow voice fits there and nowhere else.

Measured on CI's 4-core ARM runner (`research/spikes/acted-voices/time_chatterbox_onnx.py`, 12 Hindi lines, checked
by ear and by Omnilingual ASR):

| Language model | Size | Lines right | Language model RTF | Peak memory |
|---|---:|---:|---:|---:|
| fp32 (as exported) | 2081 MB | 12/12 | ~0.4 | 4.8 GB |
| fp16 | 1040 MB | 12/12 | ~2.1 | 8.2 GB |
| q4 (exported) | 354 MB | 8/12 | ~0.33 | 2.1 GB |
| int8, dynamic | 545 MB | 7/12 | — | — |
| int8, speech head kept fp32 | ~550 MB | ~8/12 | ~0.42 | 6.7 GB |

The multilingual audio decoder costs 13–17 s per sentence (RTF 3–4) whatever the precision. Chatterbox Turbo's
decoder (MIT), distilled to one step, decodes the multilingual model's speech tokens just as well (same ASR text) at
RTF 0.5–0.6: the decoder stops being the bottleneck.

## Decision
1. **A downloadable acted voice, studio mode only.** `ActedVoices` downloads one pinned zip (Wi-Fi only) into
   no-backup storage, like the punctuation model; downloading it is the opt-in. Once installed, a studio run reads
   Hindi with it (`RunSettings.acted`, `StudioMode.actedVoice`) and keys the audio by it; playback looks studio audio
   up under that voice while it still reads live with the listener's own. Clips, talking books and exports keep the
   reading voice and key by it, so no run stores audio under a voice it did not use.
2. **The model, at full precision.** Chatterbox Multilingual's ONNX export at a pinned revision: text embedder, the
   language model as exported in fp32 (the only precision that read every line right at usable speed), Turbo's
   one-step decoder, and the reference voice encoded once into `voice.bin` (multilingual conditioning for the
   language model, Turbo's for the decoder, and Turbo's three silence tokens), so neither speech encoder ships.
   `scripts/build_acted_pack.py` builds two zips, a core (~0.9 GB) and the language model (~2.1 GB), since GitHub
   caps a release file at 2 GiB; `speech/models.json` pins both on the voice mirror and one download job fetches
   them in turn. The user chose this over a smaller pack that misreads words (2026-10-23).
6. **Only on phones that can hold it.** Settings offers the voice when the phone reports at least 7 GB of memory
   (an "8 GB" phone), or when it is already installed so it can be deleted.
3. **Native engine, no second runtime.** `libaudora_acted.so` (C++, `speech/src/main/cpp`) keeps the token loop and
   its cache native and calls ONNX Runtime's C API, resolved with `dlsym` from the `libonnxruntime.so` sherpa-onnx
   already ships (API 17), as the PDFium extension does (ADR-011). Greedy decoding with a repetition penalty, as the
   export's reference script does; cancel stops a run mid-sentence.
4. **The tokenizer is Kotlin.** `scripts/acted_tokenizer.py` flattens the Hugging Face BPE tokenizer into
   `assets/acted/tokenizer.tsv`; `ChatterboxTokenizerTest` checks the port against the original's ids.
5. **Guards for a generative model.** Each sentence gets a token cap from its length; a take that runs on, or ends
   far too short, is retried once plainer (lower exaggeration, higher penalty) and then fails, so studio's own retry
   limit and the live voice take over. Long sentences are read in clause-sized pieces joined by a short pause. The
   acted voice reads whole sentences (`keepsSentencesWhole`): the quote split of ADR-058 is left to it.

## Alternatives considered
- **A smaller language model (q4, int8, int8 with an fp32 head):** a third of the pack, but each misread about a
  third of the lines; greedy decoding flips on the rounding. Words matter more than reach.
- **fp16:** right, but CPU kernels run it slower than fp32 and it peaked at 8 GB.
- **Batching a paragraph into one decoder call, or a 3 s reference:** each halved the multilingual decoder's cost;
  Turbo's decoder beats both and keeps per-sentence audio (needed for the store and seeking).
- **Running it on a server:** outside offline-first; online voices are Flutter's (decided 2026-10-23).

## Consequences
- Slow by design: on the runner a sentence takes about 1.5 times its length (language model ~0.95, decoder ~0.6); a
  phone is 1.5–2× slower. With Android 15's 6 h a day for media-processing jobs, a night gives roughly two hours of
  Hindi listening. Settings says so.
- About 3 GB on disk (and about 2 GB more free while the language model unpacks), and roughly 4.5 GB of memory while
  studio mode renders; nothing while it does not.
- Acted audio is rendered at the voice's own pace: the model has no rate control, so the speed setting does not
  change it.
- Installing or deleting the voice changes the studio key for Hindi books: their studio audio renders again.
- New native code: built for arm64 (and x86_64 for emulator tests), 16 KB aligned, `-Werror`.
