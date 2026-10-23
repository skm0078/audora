#!/usr/bin/env python3
# File: research/spikes/indic-voices/export_coqui_vits.py
# Purpose: Converts a Coqui TTS character-based VITS checkpoint (SYSPIN and Coqui's own Bengali voices) into a
#          sherpa-onnx model directory: model.onnx with sherpa's "coqui" metadata, plus tokens.txt.
#          Runs in the indic-voices CI workflow (Hugging Face is reachable there, not from the agent container).
#
# The metadata mirrors sherpa-onnx's own exporter (scripts/vits/vits-coqui.py, Apache-2.0), so the runtime treats
# the result like its prebuilt vits-coqui-* models: comment=coqui, frontend=characters.
#
# Usage: python3 export_coqui_vits.py --src <dir with config.json + checkpoint> --checkpoint best_model.pth --out <dir>

from __future__ import annotations

import argparse
from pathlib import Path

import onnx
from TTS.tts.configs.vits_config import VitsConfig
from TTS.tts.models.vits import Vits

LANGUAGE = "Bangla"  # sherpa-onnx only logs this value for character-frontend models


def export(src: Path, checkpoint: str, out: Path) -> dict:
    config = VitsConfig()
    config.load_json(str(src / "config.json"))
    vits = Vits.init_from_config(config)
    if vits.config.use_phonemes:
        raise SystemExit(f"{src}: phoneme models need espeak data; only character models are supported")
    vits.load_checkpoint(config, str(src / checkpoint))
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "model.onnx"
    vits.export_onnx(output_path=str(model_path), verbose=False)

    chars = vits.tokenizer.characters
    speakers = getattr(vits.config.model_args, "num_speakers", 0) or 0
    meta = {
        "model_type": "vits",
        "comment": "coqui",
        "language": LANGUAGE,
        "frontend": "characters",
        "add_blank": int(vits.config.add_blank),
        "blank_id": chars.blank_id,
        "n_speakers": speakers,
        "use_eos_bos": int(vits.tokenizer.use_eos_bos),
        "bos_id": chars.bos_id,
        "eos_id": chars.eos_id,
        "pad_id": chars.pad_id,
        "sample_rate": int(vits.ap.sample_rate),
    }
    model = onnx.load(str(model_path))
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = str(value)
    onnx.save(model, str(model_path))

    with open(out / "tokens.txt", "w", encoding="utf-8") as f:
        for token, idx in chars._char_to_id.items():  # noqa: SLF001 - the exporter upstream reads it the same way
            f.write(f"{token} {idx}\n")
    return meta


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", required=True, type=Path)
    parser.add_argument("--checkpoint", default="model_file.pth")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    print("metadata", export(args.src, args.checkpoint, args.out))


if __name__ == "__main__":
    main()
