#!/usr/bin/env python3
# File: scripts/acted_tokenizer.py
# Purpose: Converts Chatterbox's tokenizer.json (Hugging Face tokenizers, BPE) into the plain table the app's
#          ChatterboxTokenizer reads (ADR-059), and the pack builder's reference ids into the test's case table, so the
#          app needs no JSON parser and the Kotlin port is checked against the original.
#
#   python3 scripts/acted_tokenizer.py --tokenizer chatterbox-tokenizer.json --cases tokenizer_cases.json
#
# tokenizer.tsv, one entry per line, tab-separated: "template", the ids before and after the text; "added", id,
# 1 if matched after normalization else 0, content; "vocab", id, token; "merge", left, right (in rank order).
# Vocabulary tokens holding whitespace are left out: the whitespace pre-tokenizer never hands the model one.

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TABLE = REPO / "speech" / "src" / "main" / "assets" / "acted" / "tokenizer.tsv"
CASES = REPO / "speech" / "src" / "test" / "resources" / "acted" / "tokenizer_cases.tsv"
HEADER = "# Chatterbox Multilingual tokenizer (Resemble AI, MIT), from scripts/acted_tokenizer.py. Do not edit.\n"


def template_ids(spec: dict) -> tuple[list[int], list[int]]:
    post = spec["post_processor"]
    if post.get("type") != "TemplateProcessing":
        raise ValueError(f"unexpected post-processor {post.get('type')}")
    special = post["special_tokens"]
    before, after, seen_text = [], [], False
    for piece in post["single"]:
        if "Sequence" in piece:
            seen_text = True
        else:
            (after if seen_text else before).extend(special[piece["SpecialToken"]["id"]]["ids"])
    return before, after


def check_supported(spec: dict) -> None:
    normalizers = spec["normalizer"]["normalizers"]
    expected = [{"type": "Replace", "pattern": {"String": " "}, "content": "[SPACE]"}]
    if normalizers != expected:
        raise ValueError(f"unexpected normalizer {normalizers}")
    if spec["pre_tokenizer"] != {"type": "Whitespace"}:
        raise ValueError(f"unexpected pre-tokenizer {spec['pre_tokenizer']}")
    model = spec["model"]
    if model["type"] != "BPE" or model.get("continuing_subword_prefix") or model.get("end_of_word_suffix") \
            or model.get("fuse_unk") or model.get("byte_fallback"):
        raise ValueError("only plain BPE is supported")


def clean(token: str) -> bool:
    return not any(ch.isspace() for ch in token)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--cases", type=Path, required=True)
    args = parser.parse_args()
    spec = json.loads(args.tokenizer.read_text(encoding="utf-8"))
    check_supported(spec)
    before, after = template_ids(spec)
    model = spec["model"]
    lines = [HEADER, f"template\t{','.join(map(str, before))}\t{','.join(map(str, after))}\n",
             f"unknown\t{model['vocab'][model['unk_token']]}\n"]
    for added in spec["added_tokens"]:
        if not clean(added["content"]):
            raise ValueError(f"added token with whitespace: {added['content']!r}")
        lines.append(f"added\t{added['id']}\t{1 if added['normalized'] else 0}\t{added['content']}\n")
    for token, index in sorted(model["vocab"].items(), key=lambda item: item[1]):
        if clean(token):
            lines.append(f"vocab\t{index}\t{token}\n")
    for merge in model["merges"]:
        left, right = merge.split(" ") if isinstance(merge, str) else merge
        lines.append(f"merge\t{left}\t{right}\n")
    TABLE.parent.mkdir(parents=True, exist_ok=True)
    TABLE.write_text("".join(lines), encoding="utf-8")

    cases = json.loads(args.cases.read_text(encoding="utf-8"))
    CASES.parent.mkdir(parents=True, exist_ok=True)
    CASES.write_text("".join(f"{case['text']}\t{','.join(map(str, case['ids']))}\n" for case in cases),
                     encoding="utf-8")
    print(f"Wrote {TABLE} ({len(lines)} lines) and {CASES} ({len(cases)} cases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
