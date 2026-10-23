#!/usr/bin/env python3
# File: research/spikes/acted-voices/time_chatterbox_onnx.py
# Purpose: Spike (no app change): runs the onnx-community ONNX export of Chatterbox Multilingual (MIT) with ONNX
#          Runtime on CPU, the way an Android build would, and times each stage per Hindi line: the reference-voice
#          encoder (once), the language model's token loop, and the audio decoder. It caps the token loop by the
#          line's length (the guard against the runaway babble the PyTorch spike heard at high exaggeration), records
#          peak memory, and writes WAVs, results.md and results.json. It also saves the reference voice's encoder
#          outputs (voice.npz), which an app can ship instead of running the encoder, and prints the four models'
#          inputs, outputs and sizes and the tokenizer's type for the Kotlin port.
#
#   python3 time_chatterbox_onnx.py --models models --out out --lm q4 [--threads 4]

from __future__ import annotations

import argparse
import json
import resource
import time
from math import gcd
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from huggingface_hub import snapshot_download
from scipy.signal import resample_poly
from tokenizers import Tokenizer

REPO_ID = "onnx-community/chatterbox-multilingual-ONNX"
REVISION = "452d3f434aa592098f1eedac9099f33642ab2da5"  # pinned: the files timed here are the files an app would ship
SAMPLE_RATE = 24000
START_SPEECH_TOKEN = 6561
STOP_SPEECH_TOKEN = 6562
SPEECH_TOKENS_PER_S = 25
REPETITION_PENALTY = 1.2
# Token cap: Hindi narration runs about 12 characters a second; the cap allows a slow, acted reading of the line
# (8 characters a second) plus a breath, and no more.
CAP_CHARS_PER_S = 8.0
CAP_EXTRA_S = 1.5
KV_HEADS, HEAD_DIM = 16, 64  # the export's Llama backbone, used when a model leaves the dimension symbolic

LINES = [
    ("narration", "उस रात गाँव में तेज़ आँधी चल रही थी, और सब लोग अपने घरों में बंद थे।"),
    ("scared", "मीना ने काँपती आवाज़ में पूछा, कौन है वहाँ?"),
    ("surprised", "अरे भैया, तुम यहाँ! मुझे तो विश्वास ही नहीं हो रहा।"),
    ("happy", "मैं वापस आ गया हूँ, मीना! अब हम फिर से साथ रहेंगे।"),
    ("sad", "इतने साल बाद तुम्हें देखकर मेरी आँखें भर आईं।"),
    ("angry", "दरवाज़ा बंद करो, आँधी अंदर आ रही है!"),
]
STRENGTHS = (0.5, 0.7)
LM_FILES = {"fp32": "language_model", "q4": "language_model_q4", "int8": "language_model_int8"}
SHORT_PROMPT_S = 3.0  # a shorter reference clip: the decoder re-renders the whole prompt on every call


def peak_rss_mb() -> float:
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024  # Linux reports KiB


def session(path: Path, threads: int) -> ort.InferenceSession:
    options = ort.SessionOptions()
    options.intra_op_num_threads = threads
    options.inter_op_num_threads = 1
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])


def describe(name: str, sess: ort.InferenceSession, path: Path) -> str:
    size = sum(p.stat().st_size for p in path.parent.glob(path.name + "*")) / 1e6
    ins = ", ".join(f"{i.name}{i.shape}:{i.type}" for i in sess.get_inputs() if "past_key_values" not in i.name)
    outs = ", ".join(f"{o.name}{o.shape}" for o in sess.get_outputs() if "present" not in o.name)
    return f"- {name}: {size:.0f} MB; in {ins}; out {outs}"


def load_voice(path: Path) -> np.ndarray:
    audio, sr = sf.read(path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != SAMPLE_RATE:
        g = gcd(sr, SAMPLE_RATE)
        audio = resample_poly(audio, SAMPLE_RATE // g, sr // g).astype(np.float32)
    return audio[np.newaxis, :]


def penalise(scores: np.ndarray, seen: np.ndarray) -> np.ndarray:
    picked = np.take_along_axis(scores, seen, axis=1)
    picked = np.where(picked < 0, picked * REPETITION_PENALTY, picked / REPETITION_PENALTY)
    out = scores.copy()
    np.put_along_axis(out, seen, picked, axis=1)
    return out


def dim(value, fallback: int) -> int:
    return value if isinstance(value, int) else fallback


def token_cap(text: str) -> int:
    return int((len(text) / CAP_CHARS_PER_S + CAP_EXTRA_S) * SPEECH_TOKENS_PER_S)


def generate(models: dict, voice: tuple, tokenizer: Tokenizer, text: str, strength: float) -> dict:
    cond_emb, prompt_token, speaker_embeddings, speaker_features = voice
    input_ids = np.array([tokenizer.encode(f"[hi]{text}").ids], dtype=np.int64)
    position_ids = np.where(input_ids >= START_SPEECH_TOKEN, 0, np.arange(input_ids.shape[1])[np.newaxis, :] - 1)
    embed_inputs = {
        "input_ids": input_ids,
        "position_ids": position_ids.astype(np.int64),
        "exaggeration": np.array([strength], dtype=np.float32),
    }
    lm = models["lm"]
    kv_inputs = [i for i in lm.get_inputs() if "past_key_values" in i.name]
    cap = token_cap(text)
    tokens = np.array([[START_SPEECH_TOKEN]], dtype=np.int64)
    start = time.perf_counter()
    stopped = False
    for step in range(cap):
        embeds = models["embed"].run(None, embed_inputs)[0]
        if step == 0:
            embeds = np.concatenate((cond_emb, embeds), axis=1)
            past = {
                i.name: np.zeros([1, dim(i.shape[1], KV_HEADS), 0, dim(i.shape[3], HEAD_DIM)],
                                 dtype=np.float16 if i.type == "tensor(float16)" else np.float32)
                for i in kv_inputs
            }
            mask = np.ones((1, embeds.shape[1]), dtype=np.int64)
        logits, *present = lm.run(None, {"inputs_embeds": embeds, "attention_mask": mask, **past})
        next_token = np.argmax(penalise(logits[:, -1, :].astype(np.float32), tokens), axis=-1, keepdims=True)
        tokens = np.concatenate((tokens, next_token.astype(np.int64)), axis=-1)
        if int(next_token[0, 0]) == STOP_SPEECH_TOKEN:
            stopped = True
            break
        embed_inputs["input_ids"] = next_token.astype(np.int64)
        embed_inputs["position_ids"] = np.full((1, 1), step + 1, dtype=np.int64)
        mask = np.concatenate([mask, np.ones((1, 1), dtype=np.int64)], axis=1)
        past = {i.name: present[j] for j, i in enumerate(kv_inputs)}
    lm_s = time.perf_counter() - start
    speech = tokens[:, 1:-1] if stopped else tokens[:, 1:]
    start = time.perf_counter()
    wav = decode(models, voice, speech)
    decode_s = time.perf_counter() - start
    return {"wav": wav, "speech": speech, "tokens": int(speech.shape[1]), "cap": cap, "capped": not stopped,
            "lm_s": lm_s, "decode_s": decode_s}


def decode(models: dict, voice: tuple, speech: np.ndarray) -> np.ndarray:
    _, prompt_token, speaker_embeddings, speaker_features = voice
    return models["decoder"].run(None, {
        "speech_tokens": np.concatenate([prompt_token, speech], axis=1),
        "speaker_embeddings": speaker_embeddings,
        "speaker_features": speaker_features,
    })[0].squeeze(axis=0)


def quantize_int8(root: Path) -> None:
    """Dynamic int8 (per channel) from the fp32 language model; the export ships no 8-bit model."""
    from onnxruntime.quantization import QuantType, quantize_dynamic

    target = root / "onnx" / "language_model_int8.onnx"
    if not target.exists():
        start = time.perf_counter()
        quantize_dynamic(root / "onnx" / "language_model.onnx", target, weight_type=QuantType.QInt8, per_channel=True,
                         use_external_data_format=True)
        print(f"Quantized to int8 in {time.perf_counter() - start:.0f} s", flush=True)


def timed(fn):
    start = time.perf_counter()
    value = fn()
    return value, time.perf_counter() - start


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--lm", choices=sorted(LM_FILES), required=True)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    out = args.out / args.lm
    out.mkdir(parents=True, exist_ok=True)

    root = Path(snapshot_download(
        REPO_ID, revision=REVISION, local_dir=args.models,
        allow_patterns=["tokenizer*.json", "default_voice.wav", "onnx/speech_encoder.onnx*", "onnx/embed_tokens.onnx*",
                        "onnx/conditional_decoder.onnx*", f"onnx/{LM_FILES['fp32' if args.lm == 'int8' else args.lm]}.onnx*"],
    ))
    if args.lm == "int8":
        quantize_int8(root)
    paths = {
        "encoder": root / "onnx" / "speech_encoder.onnx",
        "embed": root / "onnx" / "embed_tokens.onnx",
        "lm": root / "onnx" / f"{LM_FILES[args.lm]}.onnx",
        "decoder": root / "onnx" / "conditional_decoder.onnx",
    }
    start = time.perf_counter()
    models = {name: session(path, args.threads) for name, path in paths.items()}
    load_s = time.perf_counter() - start
    notes = [describe(name, models[name], path) for name, path in paths.items()]

    tokenizer = Tokenizer.from_file(str(root / "tokenizer.json"))
    spec = json.loads((root / "tokenizer.json").read_text(encoding="utf-8"))
    notes.append(f"- tokenizer: model {spec['model']['type']}, normalizer {spec.get('normalizer')}, "
                 f"pre_tokenizer {spec.get('pre_tokenizer')}, decoder {spec.get('decoder', {}) and spec['decoder'].get('type')}")
    notes.append(f"- tokens for '[hi]{LINES[1][1]}': {tokenizer.encode('[hi]' + LINES[1][1]).ids}")

    start = time.perf_counter()
    voice = tuple(models["encoder"].run(None, {"audio_values": load_voice(root / "default_voice.wav")}))
    encode_s = time.perf_counter() - start
    np.savez(out / "voice.npz", cond_emb=voice[0], prompt_token=voice[1], speaker_embeddings=voice[2],
             speaker_features=voice[3])
    notes.append("- voice.npz: " + ", ".join(f"{k}{v.shape}:{v.dtype}" for k, v in zip(
        ("cond_emb", "prompt_token", "speaker_embeddings", "speaker_features"), voice)))

    rows, records, results = [], [], []
    for strength in STRENGTHS:
        for name, text in LINES:
            result = generate(models, voice, tokenizer, text, strength)
            results.append({**result, "strength": strength})
            audio_s = len(result["wav"]) / SAMPLE_RATE
            total = result["lm_s"] + result["decode_s"]
            sf.write(out / f"hi-{name}-{int(strength * 10)}.wav", result["wav"], SAMPLE_RATE)
            record = {"line": name, "strength": strength, "audio_s": round(audio_s, 2), "lm_s": round(result["lm_s"], 2),
                      "decode_s": round(result["decode_s"], 2), "rtf": round(total / audio_s, 2),
                      "tokens": result["tokens"], "cap": result["cap"], "capped": result["capped"]}
            records.append(record)
            rows.append(f"| {name} | {strength} | {audio_s:.1f} | {result['lm_s']:.1f} | {result['decode_s']:.1f} | "
                        f"{total / audio_s:.2f} | {result['tokens']}/{result['cap']}{' CAPPED' if result['capped'] else ''} |")
            print(rows[-1], flush=True)

    # The decoder re-renders the reference prompt on every call, so its cost has a fixed part. Two ways around it:
    # decode a paragraph (all lines at the first strength) in one call, and a shorter reference clip.
    firsts = [r for r in results if r["strength"] == STRENGTHS[0]]
    joined = np.concatenate([r["speech"] for r in firsts], axis=1)
    batch_wav, batch_s = timed(lambda: decode(models, voice, joined))
    sf.write(out / "hi-paragraph-5.wav", batch_wav, SAMPLE_RATE)
    batch_audio = len(batch_wav) / SAMPLE_RATE
    reference = load_voice(root / "default_voice.wav")[:, : int(SHORT_PROMPT_S * SAMPLE_RATE)]
    short_voice = tuple(models["encoder"].run(None, {"audio_values": reference}))
    narration = generate(models, short_voice, tokenizer, LINES[0][1], STRENGTHS[0])
    sf.write(out / "hi-narration-short-prompt-5.wav", narration["wav"], SAMPLE_RATE)
    extra = [
        f"- One decoder call for the {len(firsts)} lines at {STRENGTHS[0]}: {batch_s:.1f} s for {batch_audio:.1f} s "
        f"(decoder RTF {batch_s / batch_audio:.2f}; one call per line took "
        f"{sum(r['decode_s'] for r in firsts):.1f} s).",
        f"- {SHORT_PROMPT_S:.0f} s reference ({short_voice[1].shape[1]} prompt tokens instead of {voice[1].shape[1]}): "
        f"narration decoder {narration['decode_s']:.1f} s, LM {narration['lm_s']:.1f} s, "
        f"{len(narration['wav']) / SAMPLE_RATE:.1f} s of audio.",
    ]

    audio = sum(r["audio_s"] for r in records)
    work = sum(r["lm_s"] + r["decode_s"] for r in records)
    summary = [
        f"## Chatterbox ONNX, language model {args.lm}, {args.threads} threads",
        f"Load {load_s:.1f} s; voice encoder {encode_s:.1f} s (once per voice); peak memory {peak_rss_mb():.0f} MB; "
        f"overall RTF {work / audio:.2f} ({work:.0f} s of work for {audio:.0f} s of audio).", "",
        "| Line | Strength | Audio s | LM s | Decoder s | RTF | Tokens/cap |", "|---|---:|---:|---:|---:|---:|---|",
        *rows, "", *extra, *notes, "",
    ]
    (out / "results.md").write_text("\n".join(summary), encoding="utf-8")
    (out / "results.json").write_text(json.dumps({"load_s": load_s, "encode_s": encode_s, "peak_mb": peak_rss_mb(),
                                                  "rtf": work / audio, "lines": records}, indent=1), encoding="utf-8")
    print("\n".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
