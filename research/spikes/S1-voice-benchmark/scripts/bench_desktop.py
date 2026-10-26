#!/usr/bin/env python3
# File: research/spikes/S1-voice-benchmark/scripts/bench_desktop.py
# Purpose: S1 desktop pre-run: synthesizes every catalog voice on the shared benchmark texts with the same
#          sherpa-onnx version as the app, measures load time, first-sentence latency and RTF, writes WAV
#          samples, and scores intelligibility by transcribing each sample back with Whisper (ASR round trip).
#
# Desktop RTF is NOT the phone number (x86 vs. Cortex-A78); it ranks voices against each other and
# predicts which ones can possibly be real time on the test phone. The phone numbers come from :spikes:voices.
#
# Usage:
#   python3 bench_desktop.py --catalog spikes/voices/src/main/assets/s1-voices.json \
#       --models-dir /path/to/models --out /path/to/out
#       [--asr-en /path/to/sherpa-onnx-whisper-turbo] [--asr-indic /path/to/sherpa-onnx-omnilingual-asr-...]
#       [--threads 2] [--only id1,id2] [--sweep kitten-en-nano:0-7]

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import sys
import time
import unicodedata
import wave
from dataclasses import asdict, dataclass, field
from pathlib import Path

import numpy as np
import sherpa_onnx

WARMUP_TEXT = {"en": "Hello.", "hi": "नमस्ते।", "bn": "নমস্কার।"}
# Texts whose words are scored by the ASR round trip. "hard" texts hold numbers and abbreviations the
# voices are not expected to expand yet (that is P2-04's normalizer), so their CER is reported separately.
SCORED_KINDS = ("prose", "formal")
PCM16_MAX = 32767
CONCAT_GAP_S = 0.8
MP3_BITRATE = "48k"
# Omnilingual ASR sometimes writes Hindi speech in Urdu script (same spoken language). Such a transcript says
# nothing about the voice, so texts whose transcript is mostly outside the expected script are not scored.
EXPECTED_SCRIPT = {"hi": (0x0900, 0x097F), "bn": (0x0980, 0x09FF)}
SCRIPT_MATCH_MIN = 0.8


@dataclass
class TextResult:
    text_id: str
    kind: str
    synth_s: float
    audio_s: float
    rtf: float
    asr_text: str = ""
    cer: float | None = None
    script_mismatch: bool = False


@dataclass
class VoiceResult:
    voice_id: str
    label: str
    lang: str
    family: str
    sid: int
    threads: int
    disk_mb: float
    sample_rate: int = 0
    load_s: float = 0.0
    first_sentence_s: float = 0.0
    rtf_mean: float = 0.0
    cer_scored: float | None = None
    cer_hard: float | None = None
    script_mismatches: int = 0
    error: str = ""
    texts: list[TextResult] = field(default_factory=list)


def dir_size_mb(path: Path) -> float:
    return sum(p.stat().st_size for p in path.rglob("*") if p.is_file()) / 1e6


def build_tts(voice: dict, model_dir: Path, threads: int) -> sherpa_onnx.OfflineTts:
    family = voice["family"]
    d = str(model_dir)
    data_dir = f"{d}/{voice['dataDir']}" if voice.get("dataDir") else ""
    model_config = sherpa_onnx.OfflineTtsModelConfig(num_threads=threads, provider="cpu")
    if family == "vits":
        model_config.vits = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=f"{d}/{voice['model']}", tokens=f"{d}/tokens.txt", data_dir=data_dir,
        )
    elif family == "kitten":
        model_config.kitten = sherpa_onnx.OfflineTtsKittenModelConfig(
            model=f"{d}/{voice['model']}", voices=f"{d}/{voice['voices']}", tokens=f"{d}/tokens.txt",
            data_dir=data_dir,
        )
    elif family == "kokoro":
        lexicon = f"{d}/{voice['lexicon']}" if voice.get("lexicon") else ""
        model_config.kokoro = sherpa_onnx.OfflineTtsKokoroModelConfig(
            model=f"{d}/{voice['model']}", voices=f"{d}/{voice['voices']}", tokens=f"{d}/tokens.txt",
            data_dir=data_dir, lexicon=lexicon, lang=voice.get("kokoroLang", ""),
        )
    elif family == "supertonic":
        model_config.supertonic = sherpa_onnx.OfflineTtsSupertonicModelConfig(
            duration_predictor=f"{d}/duration_predictor.int8.onnx",
            text_encoder=f"{d}/text_encoder.int8.onnx",
            vector_estimator=f"{d}/vector_estimator.int8.onnx",
            vocoder=f"{d}/vocoder.int8.onnx",
            tts_json=f"{d}/tts.json",
            unicode_indexer=f"{d}/unicode_indexer.bin",
            voice_style=f"{d}/voice.bin",
        )
    else:
        raise ValueError(f"unknown family {family}")
    config = sherpa_onnx.OfflineTtsConfig(model=model_config, max_num_sentences=1)
    if not config.validate():
        raise ValueError(f"invalid sherpa-onnx config for {voice['id']}")
    return sherpa_onnx.OfflineTts(config)


def synthesize(tts: sherpa_onnx.OfflineTts, voice: dict, sid: int, text: str):
    gen = sherpa_onnx.GenerationConfig()
    gen.sid = sid
    gen.speed = 1.0
    if voice["family"] == "supertonic":
        gen.num_steps = int(voice.get("numSteps", 5))
        gen.extra = {"lang": voice["extraLang"]}
    if voice.get("extra"):
        # e.g. {"emotion_id": "3"} for AI4Bharat Rasa; sherpa-onnx reads it per call, no reload.
        gen.extra = {**gen.extra, **voice["extra"]}
    start = time.perf_counter()
    audio = tts.generate(text, gen)
    elapsed = time.perf_counter() - start
    samples = np.asarray(audio.samples, dtype=np.float32)
    return samples, audio.sample_rate, elapsed


def write_wav(path: Path, samples: np.ndarray, sample_rate: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1.0, 1.0) * PCM16_MAX).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(pcm.tobytes())


def normalize_for_cer(text: str) -> str:
    text = unicodedata.normalize("NFC", text).lower()
    # Drop punctuation (incl. danda), whitespace and nukta (ज़ vs ज is accent, not intelligibility): CER over
    # letters only, as in S2. NFC keeps nukta letters decomposed (they are composition exclusions).
    text = re.sub(r"[\s।॥़়-]", "", text)
    return "".join(ch for ch in text if not unicodedata.category(ch).startswith("P"))


def cer(reference: str, hypothesis: str) -> float:
    ref, hyp = normalize_for_cer(reference), normalize_for_cer(hypothesis)
    if not ref:
        return 0.0
    prev = list(range(len(hyp) + 1))
    for i, rc in enumerate(ref, 1):
        cur = [i] + [0] * len(hyp)
        for j, hc in enumerate(hyp, 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (rc != hc))
        prev = cur
    return prev[-1] / len(ref)


def script_share(text: str, lang: str) -> float:
    """Share of letters (incl. combining vowel signs) in the language's script; 1.0 for languages not checked."""
    span = EXPECTED_SCRIPT.get(lang)
    letters = [ch for ch in text if unicodedata.category(ch)[0] in "LM"]
    if span is None or not letters:
        return 1.0
    return sum(span[0] <= ord(ch) <= span[1] for ch in letters) / len(letters)


class Transcriber:
    """ASR round trip. Whisper for English; Meta's Omnilingual CTC for Hindi and Bengali, because sherpa-onnx's
    Whisper drops every character whose UTF-8 bytes span two BPE tokens (most Indic letters). Omnilingual sometimes
    writes Hindi words in Urdu script, which counts as errors, so Indic CER is a relative ranking between voices,
    not an absolute accuracy."""

    def __init__(self, whisper_dir: str, indic_dir: str, threads: int):
        self.whisper_dir = Path(whisper_dir) if whisper_dir else None
        self.indic_dir = Path(indic_dir) if indic_dir else None
        self.threads = threads
        self._by_lang: dict[str, sherpa_onnx.OfflineRecognizer] = {}

    def available(self, lang: str) -> bool:
        return (self.whisper_dir if lang == "en" else self.indic_dir) is not None

    def _recognizer(self, lang: str) -> sherpa_onnx.OfflineRecognizer:
        if lang not in self._by_lang:
            if lang == "en":
                d = self.whisper_dir
                self._by_lang[lang] = sherpa_onnx.OfflineRecognizer.from_whisper(
                    encoder=str(next(d.glob("*-encoder.int8.onnx"))),
                    decoder=str(next(d.glob("*-decoder.int8.onnx"))),
                    tokens=str(next(d.glob("*-tokens.txt"))),
                    language=lang, task="transcribe", num_threads=self.threads,
                )
            else:
                d = self.indic_dir
                self._by_lang[lang] = sherpa_onnx.OfflineRecognizer.from_omnilingual_asr_ctc(
                    model=str(d / "model.int8.onnx"), tokens=str(d / "tokens.txt"), num_threads=self.threads,
                )
        return self._by_lang[lang]

    def transcribe(self, samples: np.ndarray, sample_rate: int, lang: str) -> str:
        rec = self._recognizer(lang)
        stream = rec.create_stream()
        stream.accept_waveform(sample_rate, samples)
        rec.decode_stream(stream)
        return stream.result.text.strip()


def concat_mp3(wavs: list[Path], sample_rate: int, out_mp3: Path) -> None:
    """One listenable file per voice: all texts in order with a short gap. Needs ffmpeg."""
    parts = []
    gap = np.zeros(int(CONCAT_GAP_S * sample_rate), dtype="<i2")
    for wav_path in wavs:
        with wave.open(str(wav_path), "rb") as w:
            parts.append(np.frombuffer(w.readframes(w.getnframes()), dtype="<i2"))
            parts.append(gap)
    joined = out_mp3.with_suffix(".wav")
    with wave.open(str(joined), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(np.concatenate(parts).tobytes())
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(joined), "-ac", "1", "-b:a", MP3_BITRATE, str(out_mp3)],
        check=True,
    )
    joined.unlink()


def run_voice(voice: dict, sid: int, texts: list[dict], args, transcriber: Transcriber | None) -> VoiceResult:
    model_dir = Path(args.models_dir) / voice["archive"]
    result = VoiceResult(
        voice_id=voice["id"] if sid == voice.get("sid", 0) else f"{voice['id']}#sid{sid}",
        label=voice["label"], lang=voice["lang"], family=voice["family"], sid=sid, threads=args.threads,
        disk_mb=round(dir_size_mb(model_dir), 1) if model_dir.is_dir() else 0.0,
    )
    if not model_dir.is_dir():
        result.error = f"model directory missing: {model_dir}"
        return result
    try:
        start = time.perf_counter()
        tts = build_tts(voice, model_dir, args.threads)
        result.load_s = time.perf_counter() - start
        result.sample_rate = tts.sample_rate
        synthesize(tts, voice, sid, WARMUP_TEXT[voice["lang"]])
        wav_dir = Path(args.out) / "wav" / result.voice_id
        written = []
        for text in texts:
            samples, sr, elapsed = synthesize(tts, voice, sid, text["text"])
            audio_s = len(samples) / sr if sr else 0.0
            tr = TextResult(
                text_id=text["id"], kind=text["kind"], synth_s=round(elapsed, 3), audio_s=round(audio_s, 2),
                rtf=round(elapsed / audio_s, 3) if audio_s else float("inf"),
            )
            wav_path = wav_dir / f"{text['id']}.wav"
            write_wav(wav_path, samples, sr)
            written.append(wav_path)
            if transcriber is not None and transcriber.available(voice["lang"]) and audio_s > 0:
                tr.asr_text = transcriber.transcribe(samples, sr, voice["lang"])
                if script_share(tr.asr_text, voice["lang"]) < SCRIPT_MATCH_MIN:
                    tr.script_mismatch = True
                else:
                    tr.cer = round(cer(text["text"], tr.asr_text), 3)
            if text["kind"] == "first":
                result.first_sentence_s = round(elapsed, 3)
            result.texts.append(tr)
        # The first sentence is too short for a stable RTF; the phone app excludes it the same way.
        result.rtf_mean = round(statistics.mean(t.rtf for t in result.texts if t.kind != "first"), 3)
        result.script_mismatches = sum(t.script_mismatch for t in result.texts)
        scored = [t.cer for t in result.texts if t.kind in SCORED_KINDS and t.cer is not None]
        hard = [t.cer for t in result.texts if t.kind == "hard" and t.cer is not None]
        result.cer_scored = round(statistics.mean(scored), 3) if scored else None
        result.cer_hard = round(statistics.mean(hard), 3) if hard else None
        if args.mp3:
            mp3_dir = Path(args.out) / "mp3"
            mp3_dir.mkdir(parents=True, exist_ok=True)
            concat_mp3(written, result.sample_rate, mp3_dir / f"{result.voice_id}.mp3")
        result.load_s = round(result.load_s, 3)
    except Exception as exc:  # noqa: BLE001 - one broken voice must not stop the run; the error is reported
        result.error = f"{type(exc).__name__}: {exc}"
        print(f"[{result.voice_id}] FAILED: {result.error}", file=sys.stderr)
    return result


def parse_sweeps(values: list[str]) -> dict[str, list[int]]:
    sweeps = {}
    for value in values:
        voice_id, span = value.split(":")
        lo, hi = (int(x) for x in span.split("-"))
        sweeps[voice_id] = list(range(lo, hi + 1))
    return sweeps


def note(r: VoiceResult) -> str:
    if r.error:
        return r.error
    if r.script_mismatches:
        return f"{r.script_mismatches} text(s) not scored: ASR answered in another script"
    return ""


def markdown(results: list[VoiceResult]) -> str:
    def fmt(x, pattern="{:.2f}"):
        return "n/a" if x is None else pattern.format(x)

    lines = [
        "| Voice | Lang | Disk MB | Load s | First sentence s | RTF mean | CER prose+formal | CER hard | Note |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for r in results:
        lines.append(
            f"| {r.voice_id} | {r.lang} | {r.disk_mb:.0f} | {fmt(r.load_s)} | {fmt(r.first_sentence_s)} | "
            f"{fmt(r.rtf_mean, '{:.3f}')} | {fmt(r.cer_scored, '{:.1%}')} | {fmt(r.cer_hard, '{:.1%}')} | "
            f"{note(r)} |"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--models-dir", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--asr-en", default="", help="sherpa-onnx Whisper model dir (English round trip)")
    parser.add_argument("--asr-indic", default="", help="sherpa-onnx Omnilingual CTC model dir (hi/bn round trip)")
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--only", default="", help="comma-separated voice ids")
    parser.add_argument("--sweep", action="append", default=[], help="voice_id:first-last sid range")
    parser.add_argument("--mp3", action="store_true", help="also write one concatenated MP3 per voice")
    args = parser.parse_args()

    catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8"))
    only = set(filter(None, args.only.split(",")))
    sweeps = parse_sweeps(args.sweep)
    transcriber = Transcriber(args.asr_en, args.asr_indic, args.threads) if args.asr_en or args.asr_indic else None

    results: list[VoiceResult] = []
    for voice in catalog["voices"]:
        if only and voice["id"] not in only:
            continue
        texts = [t for t in catalog["texts"] if t["lang"] == voice["lang"]]
        for sid in sweeps.get(voice["id"], [voice.get("sid", 0)]):
            result = run_voice(voice, sid, texts, args, transcriber)
            print(
                f"[{result.voice_id}] load {result.load_s:.2f}s first {result.first_sentence_s:.2f}s "
                f"rtf {result.rtf_mean:.3f} cer {result.cer_scored} {result.error}",
                flush=True,
            )
            results.append(result)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "desktop-results.json").write_text(
        json.dumps([asdict(r) for r in results], ensure_ascii=False, indent=2), encoding="utf-8",
    )
    (out / "desktop-results.md").write_text(markdown(results), encoding="utf-8")
    print(markdown(results))
    return 1 if any(r.error for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
