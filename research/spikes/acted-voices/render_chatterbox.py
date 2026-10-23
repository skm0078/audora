#!/usr/bin/env python3
# File: research/spikes/acted-voices/render_chatterbox.py
# Purpose: Spike (no app change): renders Hindi story lines with Resemble AI's Chatterbox Multilingual (MIT) on CPU at
#          two emotion strengths and times each, so the maintainer can judge acted Hindi by ear before any engine
#          work. Writes WAVs and results.md (render seconds, audio seconds, real-time factor).
#
#   python3 render_chatterbox.py --out out [--threads 4]

from __future__ import annotations

import argparse
import time
from pathlib import Path

import torch
import torchaudio as ta
from chatterbox.mtl_tts import ChatterboxMultilingualTTS

LINES = [
    ("narration", "उस रात गाँव में तेज़ आँधी चल रही थी, और सब लोग अपने घरों में बंद थे।"),
    ("scared", "मीना ने काँपती आवाज़ में पूछा, कौन है वहाँ?"),
    ("surprised", "अरे भैया, तुम यहाँ! मुझे तो विश्वास ही नहीं हो रहा।"),
    ("happy", "मैं वापस आ गया हूँ, मीना! अब हम फिर से साथ रहेंगे।"),
    ("sad", "इतने साल बाद तुम्हें देखकर मेरी आँखें भर आईं।"),
    ("angry", "दरवाज़ा बंद करो, आँधी अंदर आ रही है!"),
]
STRENGTHS = (0.5, 0.9)  # Chatterbox's "exaggeration": 0.5 is its default, higher is more acted


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    torch.set_num_threads(args.threads)
    args.out.mkdir(parents=True, exist_ok=True)

    start = time.perf_counter()
    model = ChatterboxMultilingualTTS.from_pretrained(device="cpu")
    load_s = time.perf_counter() - start
    rows = [f"Model load: {load_s:.1f} s, {args.threads} threads, CPU.", "",
            "| Line | Strength | Render s | Audio s | RTF |", "|---|---:|---:|---:|---:|"]
    for strength in STRENGTHS:
        for name, text in LINES:
            start = time.perf_counter()
            wav = model.generate(text, language_id="hi", exaggeration=strength, cfg_weight=0.5)
            render_s = time.perf_counter() - start
            audio_s = wav.shape[-1] / model.sr
            ta.save(str(args.out / f"hi-{name}-{int(strength * 10)}.wav"), wav, model.sr)
            rows.append(f"| {name} | {strength} | {render_s:.1f} | {audio_s:.1f} | {render_s / audio_s:.2f} |")
            print(rows[-1], flush=True)
    (args.out / "results.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
