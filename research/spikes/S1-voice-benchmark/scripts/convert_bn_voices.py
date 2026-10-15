#!/usr/bin/env python3
# File: research/spikes/S1-voice-benchmark/scripts/convert_bn_voices.py
# Purpose: Builds sherpa-onnx model directories for the two Bengali candidates sherpa-onnx does not ship prebuilt:
#            piper: rhasspy/piper-voices bn_BD/google/medium  -> vits-piper-bn_BD-google-medium/
#            mms:   facebook/mms-tts-ben (Transformers VITS) -> vits-mms-ben/
#          Runs in the s1-voices CI workflow (Hugging Face is reachable there, not from the agent container).
#
# The metadata written here mirrors sherpa-onnx's own converters (Apache-2.0), so the runtime treats the
# results like its prebuilt Piper and MMS models:
#   scripts/piper/add_meta_data.py                     (Piper: comment=piper, espeak voice, tokens from phoneme_id_map)
#   docs tts/mms.html "vits-mms.py"                    (MMS: comment=mms, frontend=characters, tokens from the vocab)
#
# Usage:
#   python3 convert_bn_voices.py piper --src <dir with .onnx/.onnx.json/MODEL_CARD> --espeak-data <dir> --out <dir>
#   python3 convert_bn_voices.py mms --repo facebook/mms-tts-ben --out <dir>

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

import onnx

PIPER_NAME = "bn_BD-google-medium"
PIPER_SAMPLE_RATE_TYPO = 22500
PIPER_SAMPLE_RATE_FIXED = 22050
ONNX_OPSET = 15
SHERPA_BLANK_ID = 0


def add_meta_data(path: Path, meta: dict) -> None:
    model = onnx.load(str(path))
    while len(model.metadata_props):
        model.metadata_props.pop()
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = str(value)
    onnx.save(model, str(path))


def convert_piper(src: Path, espeak_data: Path, out: Path) -> None:
    config = json.loads((src / f"{PIPER_NAME}.onnx.json").read_text(encoding="utf-8"))
    out.mkdir(parents=True, exist_ok=True)

    with open(out / "tokens.txt", "w", encoding="utf-8") as f:
        for symbol, ids in config["phoneme_id_map"].items():
            if symbol == "\n":
                continue
            f.write(f"{symbol} {ids[0] if isinstance(ids, list) else ids}\n")

    sample_rate = config["audio"]["sample_rate"]
    if sample_rate == PIPER_SAMPLE_RATE_TYPO:
        sample_rate = PIPER_SAMPLE_RATE_FIXED
    voice = config.get("lang_code") or config["espeak"]["voice"]

    model = out / f"{PIPER_NAME}.onnx"
    shutil.copy(src / f"{PIPER_NAME}.onnx", model)
    add_meta_data(model, {
        "model_type": "vits",
        "comment": "piper",
        "language": "Bengali",
        "voice": voice,
        "version": 1,
        "has_espeak": 1,
        "has_g2pw": 0,
        "n_speakers": config["num_speakers"],
        "sample_rate": sample_rate,
    })
    shutil.copy(src / f"{PIPER_NAME}.onnx.json", out / f"{PIPER_NAME}.onnx.json")
    if (src / "MODEL_CARD").exists():
        shutil.copy(src / "MODEL_CARD", out / "MODEL_CARD")
    shutil.copytree(espeak_data, out / "espeak-ng-data", dirs_exist_ok=True)
    print(f"piper: wrote {out} (espeak voice {voice}, {config['num_speakers']} speakers, {sample_rate} Hz)")


def convert_mms(repo: str, out: Path) -> None:
    # Imported here so the piper path does not need torch.
    import torch
    from transformers import VitsModel, VitsTokenizer

    tokenizer = VitsTokenizer.from_pretrained(repo)
    if getattr(tokenizer, "is_uroman", False):
        raise SystemExit(f"{repo} needs uroman romanization, which sherpa-onnx's character frontend cannot do")
    model = VitsModel.from_pretrained(repo).eval()
    out.mkdir(parents=True, exist_ok=True)

    # sherpa-onnx's character frontend: one Unicode code point per token, id = model input id. It
    # lowercases ASCII itself and intersperses blank id 0 when add_blank=1 (as VitsTokenizer does).
    vocab = tokenizer.get_vocab()
    with open(out / "tokens.txt", "w", encoding="utf-8") as f:
        for symbol, idx in sorted(vocab.items(), key=lambda kv: kv[1]):
            if len(symbol) != 1:
                print(f"mms: skipping multi-character token {symbol!r} (id {idx})", file=sys.stderr)
                continue
            f.write(f"{symbol} {idx}\n")
    # VitsTokenizer intersperses token id 0 as the blank, whatever its symbol; sherpa-onnx does the same.
    if SHERPA_BLANK_ID not in vocab.values():
        raise SystemExit(f"vocab has no id {SHERPA_BLANK_ID}, which add_blank uses as the blank")

    class Wrapper(torch.nn.Module):
        """Exposes the input signature sherpa-onnx's non-Piper VITS path feeds (x, x_length, 3 scales)."""

        def __init__(self, vits):
            super().__init__()
            self.vits = vits

        def forward(self, x, x_length, noise_scale, length_scale, noise_scale_w):
            # VitsModel reads these as attributes; assigning the input tensors makes them graph inputs.
            self.vits.noise_scale = noise_scale
            self.vits.noise_scale_duration = noise_scale_w
            self.vits.speaking_rate = 1.0 / length_scale
            mask = (torch.arange(x.shape[1]).unsqueeze(0) < x_length.unsqueeze(1)).long()
            waveform = self.vits(input_ids=x, attention_mask=mask).waveform
            return waveform.unsqueeze(1)

    wrapper = Wrapper(model)
    x = torch.randint(low=1, high=10, size=(1, 50), dtype=torch.int64)
    x_length = torch.tensor([x.shape[1]], dtype=torch.int64)
    scales = (torch.tensor([0.667]), torch.tensor([1.0]), torch.tensor([0.8]))
    onnx_path = out / "model.onnx"
    with torch.no_grad():
        torch.onnx.export(
            wrapper,
            (x, x_length, *scales),
            str(onnx_path),
            opset_version=ONNX_OPSET,
            input_names=["x", "x_length", "noise_scale", "length_scale", "noise_scale_w"],
            output_names=["y"],
            dynamic_axes={"x": {0: "N", 1: "L"}, "x_length": {0: "N"}, "y": {0: "N", 2: "T"}},
            dynamo=False,
        )
    add_meta_data(onnx_path, {
        "model_type": "vits",
        "comment": "mms",
        "url": f"https://huggingface.co/{repo}",
        "add_blank": int(bool(tokenizer.add_blank)),
        "language": "Bengali",
        "frontend": "characters",
        "n_speakers": 1,
        "sample_rate": model.config.sampling_rate,
    })
    (out / "README.md").write_text(
        f"Converted from https://huggingface.co/{repo} for the Audora S1 voice benchmark. "
        "Licence: CC BY-NC 4.0 (Meta MMS).\n",
        encoding="utf-8",
    )
    print(f"mms: wrote {out} ({model.config.sampling_rate} Hz, add_blank={tokenizer.add_blank})")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="cmd", required=True)
    piper = sub.add_parser("piper")
    piper.add_argument("--src", required=True)
    piper.add_argument("--espeak-data", required=True)
    piper.add_argument("--out", required=True)
    mms = sub.add_parser("mms")
    mms.add_argument("--repo", default="facebook/mms-tts-ben")
    mms.add_argument("--out", required=True)
    args = parser.parse_args()
    if args.cmd == "piper":
        convert_piper(Path(args.src), Path(args.espeak_data), Path(args.out))
    else:
        convert_mms(args.repo, Path(args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
