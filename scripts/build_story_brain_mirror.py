#!/usr/bin/env python3
# File: scripts/build_story_brain_mirror.py
# Purpose: Builds the story brain's download parts (ADR 2026-10-25-story-brain) from speech/story-brain.json: fetches the GGUF pinned to a
#          Hugging Face commit, checks it against the pins, and cuts it into parts under GitHub's 2 GiB asset limit.
#
# The parts are plain byte ranges of the one file (the phone joins them back), named by the whole file's hash, so the
# same pins always name the same bytes. With --print-pins it prints what to paste into the catalog instead of failing
# on a pin that is still empty (a new model). Standard library only.
#
#   python3 scripts/build_story_brain_mirror.py --out dist               # build and verify
#   python3 scripts/build_story_brain_mirror.py --out dist --print-pins  # build and print the pins

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

HF = "https://huggingface.co"
CHUNK = 1 << 20
SHA_PREFIX = 12


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        while block := f.read(CHUNK):
            digest.update(block)
    return digest.hexdigest()


def resolve_revision(repo: str) -> str:
    with urllib.request.urlopen(f"{HF}/api/models/{repo}", timeout=60) as resp:
        info = json.load(resp)
    licence = (info.get("cardData") or {}).get("license") or [t for t in info.get("tags", []) if "license" in t]
    print(f"{repo}: main is {info['sha']}, licence {licence}")
    return info["sha"]


def fetch(repo: str, revision: str, file: str, into: Path) -> Path:
    target = into / file
    if target.is_file():
        return target
    into.mkdir(parents=True, exist_ok=True)
    url = f"{HF}/{repo}/resolve/{revision}/{file}"
    print(f"Downloading {url}")
    partial = target.with_suffix(".partial")
    with urllib.request.urlopen(url, timeout=600) as resp, partial.open("wb") as out:
        while block := resp.read(CHUNK):
            out.write(block)
    partial.rename(target)
    return target


def part_name(entry: dict, whole_sha: str, index: int) -> str:
    return f"{entry['id']}-{whole_sha[:SHA_PREFIX]}.part{index + 1}"


def cut(model: Path, entry: dict, whole_sha: str, out: Path) -> list[dict]:
    out.mkdir(parents=True, exist_ok=True)
    size, step, parts = model.stat().st_size, int(entry["partBytes"]), []
    with model.open("rb") as f:
        for index, start in enumerate(range(0, size, step)):
            path = out / part_name(entry, whole_sha, index)
            digest, left = hashlib.sha256(), min(step, size - start)
            with path.open("wb") as part:
                while left > 0:
                    block = f.read(min(CHUNK, left))
                    digest.update(block)
                    part.write(block)
                    left -= len(block)
            parts.append({"file": path.name, "sha256": digest.hexdigest(), "sizeBytes": path.stat().st_size})
    return parts


def problems(entry: dict, revision: str, whole_sha: str, size: int, parts: list[dict]) -> list[str]:
    found = []
    if entry["source"]["revision"] != revision:
        found.append(f"source.revision is '{entry['source']['revision']}', the pinned commit is {revision}")
    if entry["sha256"] != whole_sha:
        found.append(f"sha256 is '{entry['sha256']}', the file has {whole_sha}")
    if entry["sizeBytes"] != size:
        found.append(f"sizeBytes is {entry['sizeBytes']}, the file has {size}")
    if entry["parts"] != parts:
        found.append("parts do not match the file's parts")
    if "NC" in entry["licence"].upper().replace("-", " ").split() or "non-commercial" in entry["licence"].lower():
        found.append(f"licence {entry['licence']} does not allow commercial use")
    return found


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=Path("speech/story-brain.json"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=Path("story-brain-cache"))
    parser.add_argument("--print-pins", action="store_true")
    args = parser.parse_args(argv)

    entry = json.loads(args.catalog.read_text(encoding="utf-8"))
    source = entry["source"]
    revision = source["revision"] or resolve_revision(source["repo"])
    model = fetch(source["repo"], revision, source["file"], args.cache)
    whole_sha, size = sha256_of(model), model.stat().st_size
    parts = cut(model, entry, whole_sha, args.out)
    found = problems(entry, revision, whole_sha, size, parts)
    if args.print_pins or found:
        print(json.dumps({"revision": revision, "sha256": whole_sha, "sizeBytes": size, "parts": parts}, indent=2))
    for problem in found:
        print(f"PIN: {problem}", file=sys.stderr)
    return 1 if found and not args.print_pins else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
