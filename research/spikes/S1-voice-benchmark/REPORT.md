# S1 — Voice benchmark

Status: desktop pre-run done; phone run pending (Moto Edge 50 Fusion). Decision: ADR-012.

## Question
Which offline voices are fast enough and good enough for Audora on a mid-range phone, and which English voice
ships bundled? Ticket S1: RTF and first-sentence latency on the phone, WAV samples, 16 KB check on the sherpa-onnx
native libraries, English default chosen.

## Method
- **One catalog** (`spikes/voices/src/main/assets/s1-voices.json`): 11 voices, 12 texts (en/hi/bn × first sentence,
  prose, formal UDHR article 1, hard: numbers, currency, abbreviations, conjuncts). The phone app, the desktop
  script and CI all read it, so every run measures the same thing.
- **Phone:** `:spikes:voices` debug app (`audora-s1-voices.apk`) on sherpa-onnx 1.13.8. Per voice and thread count
  (2 and 4): load time, cold first render, warm render of every text, RSS and thermal state before/after. Plus the
  phone's own system TTS per language. Then a blind listening test (letters, ratings 1–5) and a shareable report.
- **Desktop pre-run** (`scripts/bench_desktop.py`, same sherpa-onnx version, x86, 2 threads): ranks voices against
  each other, predicts which can be real time on the phone, and checks intelligibility by an ASR round trip
  (Whisper turbo for English, Omnilingual ASR CTC 300M for Hindi and Bengali), CER over letters on the prose and
  formal texts. Transcripts in the wrong script (Omnilingual sometimes writes Hindi speech in Urdu script) are not
  scored; the count is reported.
- **Models** come from the `s1-voices` pre-release, built by `.github/workflows/s1-voices.yml`: sherpa-onnx's
  `tts-models` archives repacked as zip (the app has no bzip2), plus the two Bengali conversions
  (`scripts/convert_bn_voices.py`), with `SHA256SUMS.txt` that the app verifies before unpacking.

Definitions: **First render** = first synthesis right after loading (desktop tables). **RTF** = render time / audio time over the prose, formal and hard texts (warm). **First sentence** =
load + first render, what a reader waits for after tapping Play on a voice that is not loaded yet.

## Changes to the candidate set (vs. the ticket)
| Ticket said | Benchmarked | Why |
|---|---|---|
| Kokoro v1.1 int8 | **Kokoro v1.0 int8** (af_heart, hf_alpha) | sherpa-onnx's v1.1 is the Chinese-focused release with 3 English voices and no af_heart/bf_emma or Hindi; v1.0 has them. |
| Piper en lessac/amy | **Piper en_US ljspeech, en_GB cori** (lessac desktop-only) | Lessac's data (Blizzard 2013) is research-only with no redistribution; amy and hfc_* are fine-tuned from it. ljspeech and cori are trained from scratch on public-domain data. |
| Piper hi (rohan/priyamvada/pratham) | **priyamvada, pratham** (rohan desktop-only) | rohan is fine-tuned from lessac. priyamvada and pratham are CC BY-NC-SA 4.0. |
| Supertonic-3 hi | Supertonic 3 hi **and en** | One multilingual model; English costs nothing extra to measure. |
| Piper bn (converted), MMS ben | as listed | Converted in CI (Hugging Face is not reachable from the agent container). |

## Desktop results (x86, 2 threads)
Disk = unpacked model directory (Piper/Kitten/Kokoro include 19 MB of espeak-ng-data that every espeak-based voice
shares). Bengali rows come from the CI run (`results/desktop-ci.md`; the CI runner is ~10–30 % faster than this container, and its English/Hindi numbers agree in ranking).

| Voice | Disk MB | Load s | First render s | RTF | CER prose+formal | Notes |
|---|---|---|---|---|---|---|
| piper-en-lessac *(reference only)* | 81 | 1.10 | 0.26 | 0.067 | 0.0 % | not redistributable |
| piper-en-ljspeech | 82 | 1.53 | 0.23 | 0.065 | 0.0 % | |
| piper-en-cori | 82 | 1.80 | 0.24 | 0.074 | 0.6 % | |
| kitten-en-nano (speaker 7) | 46 | 0.58 | 1.71 | 0.340 | 0.0 % | speaker 1 paced ~50 % slower; see sweep |
| kokoro-en-heart | 190 | 1.47 | 4.62 | **1.273** | 0.0 % | slower than real time on desktop |
| supertonic-en | 145 | 0.67 | 0.84 | 0.170 | 0.0 % | 44.1 kHz; garbles numbers/abbreviations (no text normalizer) |
| piper-hi-rohan *(reference only)* | 81 | 1.11 | 0.32 | 0.079 | 8.4 % | not redistributable |
| piper-hi-priyamvada | 81 | 1.12 | 0.30 | 0.066 | 4.6 % | 1 text in Urdu script, not scored |
| piper-hi-pratham | 81 | 1.09 | 0.26 | 0.071 | 3.5 % | |
| supertonic-hi (speaker 2) | 145 | 0.75 | 1.17 | 0.169 | 2.4 % | |
| kokoro-hi-alpha | 190 | 1.57 | 5.62 | **1.183** | 6.4 % | slower than real time on desktop |
| piper-bn-google | 95 | 0.80 | 0.28 | 0.068 | 13.9 % | CI runner; 22.05 kHz |
| mms-bn | 114 | 1.21 | 1.44 | 0.369 | 9.7 % | CI runner; 16 kHz, 5× slower than Piper |

Hard texts (numbers, "Dr.", "Vol. II", ₹) are not scored: no voice expands them reliably and ASR writes numbers
inconsistently. Listening shows the gap P2-04's normalizer must close: Piper and Kokoro read "$1,250.50" and
"Vol. II" passably through espeak; Supertonic produces nonsense on that sentence ("Smith-Smith-Paint-1-Town…").

## What the desktop numbers predict for the phone
The Moto's Cortex-A78 cores are roughly 2–3× slower per thread than this x86 container for ONNX Runtime int8/fp32
inference (estimate, to be replaced by the phone table):
- **MMS ben:** RTF ≈ 0.8–1.1. Borderline; a fallback at best.
- **Piper (all languages):** RTF ≈ 0.15–0.25. Comfortably real time; first sentence under 1 s once loaded.
- **Supertonic:** RTF ≈ 0.35–0.5. Real time with render-ahead.
- **Kitten nano:** RTF ≈ 0.7–1.0 and a 1.5–2.5 s cold first render on desktop already. Borderline.
- **Kokoro:** RTF ≈ 2.5–4. Cannot keep up; the spec's rule (offer only if measured RTF < 0.8) will exclude it.

## 16 KB alignment
`sherpa-onnx-1.13.8.aar` (SHA-256 `633c2432…bd96`, pinned in `libs.versions.toml` and verified on every build):
all four arm64 libraries (`libonnxruntime.so` 22 MB, `libsherpa-onnx-jni.so` 4.7 MB, `-c-api`, `-cxx-api`) have every
PT_LOAD segment aligned to 0x4000. The JNI library needs only `libonnxruntime.so`, so the app excludes the C and
C++ API libraries (−4.8 MB). CI runs `scripts/check_16kb_alignment.py` on `audora-s1-voices.apk`.

## Licences
| Voice | Licence | Can Audora ship it? |
|---|---|---|
| Piper ljspeech, cori | Code MIT; data public domain | Yes (bundle or download) |
| Piper lessac, amy, hfc_*, hi rohan | Blizzard 2013 / derived | No: research-only, no redistribution |
| Piper hi priyamvada, pratham | CC BY-NC-SA 4.0 | Download, with attribution (non-commercial is fine for Audora) |
| Piper bn google | CC BY-SA 4.0 | Download, with attribution |
| MMS ben | CC BY-NC 4.0 | Download, with attribution |
| Kitten v0.8, Kokoro v1.0 | Apache-2.0 | Yes |
| Supertonic 3 | Code MIT; model OpenRAIL-M | Download; the use restrictions (no harmful use) fit Audora |

## Samples
`research/voice-samples/` holds one MP3 per voice (all texts of its language, 48 kbit/s mono). Full WAVs per text
are in `s1-desktop-samples.zip` on the `s1-voices` release. The phone app saves its own samples for the blind test.

## Speaker sweeps (desktop)
Multi-speaker models were swept to pick the benchmarked speaker; see `results/desktop-sweep.md`. Kitten uses speaker 7 (speaker 1 paces ~50 % slower than every other voice) and Supertonic Hindi speaker 2 (lowest CER, every text scored).

## Phone results (Moto Edge 50 Fusion)
Pending: filled from the app's shared report (`s1-report.md`). Phone steps:
`/mnt/project-files/audora/s1/phone-test-steps.md` in the project files.

## Outcome
See ADR-012. English default: **Piper medium (ljspeech or cori, whichever rates higher blind)**, unless Kitten
rates clearly higher and measures RTF ≤ 0.5 at 2 threads on the Moto.
