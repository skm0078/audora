#!/usr/bin/env python3
# File: scripts/build_acted_pack.py
# Purpose: Builds the acted Hindi voice's source zip (ADR-059) from the onnx-community export of Chatterbox
#          Multilingual (Resemble AI, MIT) at a pinned revision: the text embedder, the language model (as exported
#          in fp32, which alone reads every test line right; int8 for trials), the audio decoder (the multilingual one, or Chatterbox Turbo's
#          one-step decoder, also MIT, about six times faster), the tokenizer, and the reference voice encoded once
#          (voice.bin), so the phone needs neither a speech encoder nor the recording. Also writes tokenizer_cases.json, the tokenizer's ids for a set
#          of sentences, which ChatterboxTokenizerTest checks the Kotlin port against.
#
#   python3 scripts/build_acted_pack.py --work work --out dist --lm fp32 --decoder turbo
#
# Two zips, <archive>-core and <archive>-lm, stored (the weights do not compress), with fixed timestamps.

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
TURBO_ID = "ResembleAI/chatterbox-turbo-ONNX"
TURBO_REVISION = "d21799bd0354adb85e348b8a0442a8405110a2cf"
TURBO_SILENCE = 4299  # Turbo's reference script ends the speech tokens with three of these
TURBO_TAIL = 3
SAMPLE_RATE = 24000
VOICE_MAGIC = b"ACTVOIC1"
ONNX_FLOAT, ONNX_INT64 = 1, 7
# The language model as shipped: always named language_model.onnx, with its weights in language_model.onnx_data.
LM_GRAPH, LM_DATA = "language_model.onnx", "language_model.onnx_data"
LM_CHOICES = ("fp32", "int8h", "int8")
GITHUB_FILE_LIMIT = 2 * 1024**3
DECODER_CHOICES = ("turbo", "multilingual")

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


def head_nodes(model_path: Path) -> list[str]:
    """The MatMul nodes that make the logits (the speech head), walking back from the 'logits' output."""
    import onnx

    graph = onnx.load(str(model_path), load_external_data=False).graph
    producer = {out: node for node in graph.node for out in node.output}
    found, frontier = [], ["logits"]
    for _ in range(4):
        nodes = [producer[name] for name in frontier if name in producer]
        found += [n.name for n in nodes if n.op_type in ("MatMul", "Gemm")]
        if found:
            break
        frontier = [i for n in nodes for i in n.input]
    if not found:
        raise SystemExit("no MatMul found before the logits: the export changed")
    print(f"Speech head kept in fp32: {found}", flush=True)
    return found


def language_model(onnx_dir: Path, work: Path, lm: str) -> dict[str, Path]:
    """The language model's graph and weights under their shipped names. Quantized models are rebuilt with their
    data saved as LM_DATA, so the graph's external data reference matches the name in the zip."""
    import onnx

    if lm == "fp32":
        source = onnx.load(str(onnx_dir / "language_model.onnx"), load_external_data=True)
    else:
        quantized = work / "lm" / "quantized.onnx"
        quantized.parent.mkdir(parents=True, exist_ok=True)
        if not quantized.exists():
            start = time.perf_counter()
            quantize_dynamic(onnx_dir / "language_model.onnx", quantized, weight_type=QuantType.QInt8,
                             per_channel=True, use_external_data_format=True,
                             nodes_to_exclude=head_nodes(onnx_dir / "language_model.onnx") if lm == "int8h" else [])
            print(f"Quantized the language model ({lm}) in {time.perf_counter() - start:.0f} s", flush=True)
        source = onnx.load(str(quantized), load_external_data=True)
    shipped = work / "lm-shipped"
    shipped.mkdir(parents=True, exist_ok=True)
    onnx.save_model(source, str(shipped / LM_GRAPH), save_as_external_data=True, all_tensors_to_one_file=True,
                    location=LM_DATA, size_threshold=1024)
    return {LM_GRAPH: shipped / LM_GRAPH, LM_DATA: shipped / LM_DATA}


def decoder_files(onnx_dir: Path, work: Path, decoder: str, reference: np.ndarray) -> tuple[dict[str, Path], list]:
    """The decoder's files and the conditioning it takes (prompt tokens, speaker embedding and features, and any
    tokens it needs after the speech). Turbo's decoder is conditioned by Turbo's own speech encoder."""
    if decoder == "multilingual":
        root = onnx_dir
    else:
        root = Path(snapshot_download(TURBO_ID, revision=TURBO_REVISION, local_dir=work / "turbo",
                                      allow_patterns=["onnx/speech_encoder.onnx*",
                                                      "onnx/conditional_decoder.onnx*"])) / "onnx"
    encoder = ort.InferenceSession(str(root / "speech_encoder.onnx"), providers=["CPUExecutionProvider"])
    _, prompt, embedding, features = encoder.run(None, {"audio_values": reference})
    conditioning = [prompt, embedding, features]
    if decoder == "turbo":
        conditioning.append(np.full((1, TURBO_TAIL), TURBO_SILENCE, dtype=np.int64))
    files = {path.name: path for path in sorted(root.glob("conditional_decoder.onnx*"))}
    if set(files) != {"conditional_decoder.onnx", "conditional_decoder.onnx_data"}:
        raise SystemExit(f"unexpected decoder files: {sorted(files)}")
    return files, conditioning


def zip_stored(files: dict[str, Path], target: Path, archive: str) -> None:
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as zf:
        for name in sorted(files):
            info = zipfile.ZipInfo(f"{archive}/{name}", date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            with files[name].open("rb") as src, zf.open(info, "w", force_zip64=True) as dst:
                while chunk := src.read(1 << 24):
                    dst.write(chunk)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--lm", choices=LM_CHOICES, default="fp32")
    parser.add_argument("--decoder", choices=DECODER_CHOICES, default="turbo")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    archive = f"chatterbox-hi-{args.lm}-{args.decoder}"

    root = Path(snapshot_download(
        REPO_ID, revision=REVISION, local_dir=args.work / "hf",
        allow_patterns=["README.md", "tokenizer.json", "default_voice.wav", "onnx/speech_encoder.onnx*",
                        "onnx/embed_tokens.onnx*", "onnx/conditional_decoder.onnx*", "onnx/language_model.onnx*"],
    ))
    onnx_dir = root / "onnx"
    reference = load_reference(root / "default_voice.wav")

    # The language model is conditioned by the multilingual encoder's features, whichever decoder speaks.
    encoder = ort.InferenceSession(str(onnx_dir / "speech_encoder.onnx"), providers=["CPUExecutionProvider"])
    cond_emb = encoder.run(None, {"audio_values": reference})[0]
    decoder, conditioning = decoder_files(onnx_dir, args.work, args.decoder, reference)
    voice = [cond_emb, *conditioning]
    voice_bin = args.work / "voice.bin"
    write_voice(voice_bin, voice)
    print("voice.bin: " + ", ".join(f"{v.shape}:{v.dtype}" for v in voice), flush=True)

    tokenizer = Tokenizer.from_file(str(root / "tokenizer.json"))
    cases = [{"text": text, "ids": tokenizer.encode(text_for_model(text)).ids} for text in TOKENIZER_CASES]
    (args.out / "tokenizer_cases.json").write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n",
                                                   encoding="utf-8")

    # Two zips: the full-precision language model alone is near GitHub's 2 GiB limit for one release file.
    parts = {
        f"{archive}-core": {
            "embed_tokens.onnx": onnx_dir / "embed_tokens.onnx",
            "embed_tokens.onnx_data": onnx_dir / "embed_tokens.onnx_data",
            **decoder,
            "voice.bin": voice_bin,
            "README.md": root / "README.md",
        },
        f"{archive}-lm": {**language_model(onnx_dir, args.work, args.lm), "README.md": root / "README.md"},
    }
    for name, files in parts.items():
        target = args.out / f"{name}.zip"
        zip_stored(files, target, name)
        size = target.stat().st_size
        if size >= GITHUB_FILE_LIMIT:
            raise SystemExit(f"{target.name} is {size} bytes, over GitHub's release file limit")
        for member, path in sorted(files.items()):
            print(f"  {name}/{member}: {path.stat().st_size / 1e6:.1f} MB", flush=True)
    (args.out / "archives.txt").write_text("".join(f"{name}\n" for name in parts), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
