#!/usr/bin/env python3
# File: scripts/build_acted_pack.py
# Purpose: Builds the acted Hindi voice's source zip (ADR-059) from the onnx-community export of Chatterbox
#          Multilingual (Resemble AI, MIT) at a pinned revision: the text embedder, the language model quantized to
#          int8, the audio decoder, the tokenizer, and the reference voice encoded once (voice.bin), so the phone needs
#          neither the speech encoder nor the recording. Also writes tokenizer_cases.json, the tokenizer's ids for a set
#          of sentences, which ChatterboxTokenizerTest checks the Kotlin port against.
#
#   python3 scripts/build_acted_pack.py --work work --out dist
#
# The zip is stored, not deflated (the weights do not compress), with fixed timestamps.

from __future__ import annotations

import argparse
import json
import struct
import time
import unicodedata
import zipfile
from math import gcd
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from huggingface_hub import snapshot_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from scipy.signal import resample_poly
from tokenizers import Tokenizer

REPO_ID = "onnx-community/chatterbox-multilingual-ONNX"
REVISION = "452d3f434aa592098f1eedac9099f33642ab2da5"
ARCHIVE = "chatterbox-hi-onnx"
SAMPLE_RATE = 24000
VOICE_MAGIC = b"ACTVOIC1"
ONNX_FLOAT, ONNX_INT64 = 1, 7
INT8_MODEL = "language_model_int8.onnx"

# Sentences for the tokenizer's reference ids: Hindi prose and dialogue, nukta, conjuncts, digits, Latin words,
# punctuation and the danda, as the app's normalizer hands them over.
TOKENIZER_CASES = [
    "उस रात गाँव में तेज़ आँधी चल रही थी, और सब लोग अपने घरों में बंद थे।",
    "मीना ने काँपती आवाज़ में पूछा, कौन है वहाँ?",
    "अरे भैया, तुम यहाँ! मुझे तो विश्वास ही नहीं हो रहा।",
    "दरवाज़ा बंद करो, आँधी अंदर आ रही है!",
    "क़िस्मत, ख़ुशी, ग़ज़ल और फ़ैसला।",
    "राष्ट्रीय विद्यालय में श्रद्धा और क्षमा पढ़ाई जाती है।",
    "सन् 1947 में भारत स्वतंत्र हुआ; उस दिन 15 अगस्त था।",
    "उसने कहा — \"चलो, अब घर चलें।\"",
    "Audora ऐप PDF को हिंदी में पढ़ता है।",
    "कृपया ध्यान दें... यह अंतिम सूचना है।",
    "हाँ-हाँ, मैं आ रहा हूँ!",
    "ऋषि ने ॐ का उच्चारण किया॥",
]


def text_for_model(text: str) -> str:
    """As Chatterbox's own tokenizer prepares it: lower case, NFKD, then the language tag."""
    return "[hi]" + unicodedata.normalize("NFKD", text.lower())


def load_reference(path: Path) -> np.ndarray:
    audio, sr = sf.read(path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != SAMPLE_RATE:
        g = gcd(sr, SAMPLE_RATE)
        audio = resample_poly(audio, SAMPLE_RATE // g, sr // g).astype(np.float32)
    return audio[np.newaxis, :]


def write_voice(path: Path, tensors: list[np.ndarray]) -> None:
    with path.open("wb") as out:
        out.write(VOICE_MAGIC)
        for tensor in tensors:
            if tensor.dtype == np.float32:
                kind = ONNX_FLOAT
            elif tensor.dtype == np.int64:
                kind = ONNX_INT64
            else:
                raise ValueError(f"unexpected voice tensor type {tensor.dtype}")
            out.write(struct.pack("<ii", kind, tensor.ndim))
            out.write(struct.pack(f"<{tensor.ndim}q", *tensor.shape))
            out.write(np.ascontiguousarray(tensor).astype(tensor.dtype.newbyteorder("<")).tobytes())


def zip_stored(files: dict[str, Path], target: Path) -> None:
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as zf:
        for name in sorted(files):
            info = zipfile.ZipInfo(f"{ARCHIVE}/{name}", date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            with files[name].open("rb") as src, zf.open(info, "w", force_zip64=True) as dst:
                while chunk := src.read(1 << 24):
                    dst.write(chunk)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    root = Path(snapshot_download(
        REPO_ID, revision=REVISION, local_dir=args.work / "hf",
        allow_patterns=["README.md", "tokenizer.json", "default_voice.wav", "onnx/speech_encoder.onnx*",
                        "onnx/embed_tokens.onnx*", "onnx/conditional_decoder.onnx*", "onnx/language_model.onnx*"],
    ))
    onnx_dir = root / "onnx"
    lm_dir = args.work / "lm"
    lm_dir.mkdir(parents=True, exist_ok=True)
    int8 = lm_dir / INT8_MODEL
    if not int8.exists():
        start = time.perf_counter()
        quantize_dynamic(onnx_dir / "language_model.onnx", int8, weight_type=QuantType.QInt8, per_channel=True,
                         use_external_data_format=True)
        print(f"Quantized the language model to int8 in {time.perf_counter() - start:.0f} s", flush=True)

    encoder = ort.InferenceSession(str(onnx_dir / "speech_encoder.onnx"), providers=["CPUExecutionProvider"])
    voice = encoder.run(None, {"audio_values": load_reference(root / "default_voice.wav")})
    voice_bin = args.work / "voice.bin"
    write_voice(voice_bin, voice)
    print("voice.bin: " + ", ".join(f"{v.shape}:{v.dtype}" for v in voice), flush=True)

    tokenizer = Tokenizer.from_file(str(root / "tokenizer.json"))
    cases = [{"text": text, "ids": tokenizer.encode(text_for_model(text)).ids} for text in TOKENIZER_CASES]
    (args.out / "tokenizer_cases.json").write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n",
                                                   encoding="utf-8")
    spec = json.loads((root / "tokenizer.json").read_text(encoding="utf-8"))
    print("tokenizer: " + json.dumps({k: spec.get(k) for k in ("normalizer", "pre_tokenizer", "post_processor",
                                                                 "decoder")}, ensure_ascii=False), flush=True)
    print("added tokens: " + json.dumps(spec.get("added_tokens", [])[:40], ensure_ascii=False), flush=True)
    print(f"model: {spec['model']['type']}, {len(spec['model']['vocab'])} entries, "
          f"{len(spec['model'].get('merges', []))} merges, "
          + json.dumps({k: v for k, v in spec["model"].items() if k not in ("vocab", "merges")}), flush=True)

    files = {
        "embed_tokens.onnx": onnx_dir / "embed_tokens.onnx",
        "embed_tokens.onnx_data": onnx_dir / "embed_tokens.onnx_data",
        "conditional_decoder.onnx": onnx_dir / "conditional_decoder.onnx",
        "conditional_decoder.onnx_data": onnx_dir / "conditional_decoder.onnx_data",
        "tokenizer.json": root / "tokenizer.json",
        "voice.bin": voice_bin,
        "README.md": root / "README.md",
    }
    for path in sorted(lm_dir.iterdir()):
        # quantize_dynamic writes the graph and one external data file next to it.
        name = "language_model.onnx" if path.name == INT8_MODEL else path.name
        files[name] = path
    zip_stored(files, args.out / f"{ARCHIVE}.zip")
    for name, path in sorted(files.items()):
        print(f"  {name}: {path.stat().st_size / 1e6:.1f} MB", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
