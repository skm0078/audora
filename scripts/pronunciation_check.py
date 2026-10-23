#!/usr/bin/env python3
# File: scripts/pronunciation_check.py
# Purpose: Reads research/pronunciation/corpus.json with every downloadable voice of speech/models.json (the same
#          zips the app downloads, the same sherpa-onnx version), transcribes each sentence back with ASR and reports,
#          per voice and per pronunciation rule, which target words were heard as written. Writes WAV samples, one
#          MP3 per voice, results.json and a Markdown table.
#
# An ASR round trip hears garbled or wrong words; it cannot judge Indic pronunciation finely. It does not hear a
# kept schwa, and where Bengali sound and spelling differ (পদ্মা said "podda") it often writes the sound, so a
# rightly said word can show as missed. Read "missed" as "listen to this one", and the samples as the judge. Words are compared
# without nukta and punctuation, like bench_desktop's CER, and a target word counts as heard when it appears among
# the transcript's words.
#
#   python3 scripts/pronunciation_check.py --models-dir models --out out \
#       --asr-indic asr/<omnilingual dir> [--asr-en asr/<whisper dir>] [--only id1,id2] [--download]

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "research" / "spikes" / "S1-voice-benchmark" / "scripts"))

import bench_desktop as bench  # noqa: E402 (path set above)

CORPUS = REPO / "research" / "pronunciation" / "corpus.json"
CATALOG = REPO / "speech" / "models.json"
END_MARKS = re.compile(r"[.?!]+$")
DEVANAGARI = (0x0900, 0x097F)
BENGALI_SHIFT = 0x80
WORD_CER_MAX = 0.25


def word_key(word: str) -> str:
    """A word as compared: NFC, lower case, no nukta, no punctuation or danda."""
    text = unicodedata.normalize("NFC", word).lower()
    text = re.sub(r"[।॥़়]", "", text)
    return "".join(ch for ch in text if not unicodedata.category(ch).startswith("P"))


def to_script(text: str, lang: str) -> str:
    """Omnilingual ASR often writes Bengali speech in Devanagari; the two blocks line up letter for letter."""
    if lang != "bn":
        return text
    return "".join(chr(ord(ch) + BENGALI_SHIFT) if DEVANAGARI[0] <= ord(ch) <= DEVANAGARI[1] else ch for ch in text)


def heard_as(word: str, transcript: list[str]) -> bool:
    """Whether a transcript word is within [WORD_CER_MAX] of [word]: one letter in four may differ (an o-kar the
    voice rightly says, an ASR spelling), a garbled or missing word may not."""
    keys = [word_key(part) for part in word.split("-")]  # "चलते-चलते" is heard as two words
    return all(any(bench.cer(key, word_key(heard)) <= WORD_CER_MAX for heard in transcript) for key in keys)


def bench_voice(entry: dict) -> dict:
    """A models.json voice as bench_desktop builds it; a styled voice reads its NEUTRAL style."""
    voice = {
        "id": entry["id"], "label": entry["label"], "lang": entry["lang"], "family": entry["family"],
        "archive": entry["id"], "model": entry["model"], "sid": entry.get("speakerId", 0),
    }
    for key in ("voices", "dataDir", "lexicon", "numSteps"):
        if key in entry:
            voice[key] = entry[key]
    if "engineLang" in entry:
        voice["kokoroLang"] = voice["extraLang"] = entry["engineLang"]
    if "styles" in entry:
        voice["extra"] = {"emotion_id": str(entry["styles"]["NEUTRAL"])}
    return voice


def download(entry: dict, base_url: str, models_dir: Path) -> None:
    target = models_dir / entry["id"]
    if target.is_dir():
        return
    archive = models_dir / entry["file"]
    print(f"Downloading {entry['file']}", flush=True)
    urllib.request.urlretrieve(base_url + entry["file"], archive)  # noqa: S310 (pinned https release asset)
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(target)
    archive.unlink()


def spoken(text: str, voice: dict) -> str:
    """As the app gives it: a styled voice ends on the danda (StyledText, ADR-057)."""
    if "extra" in voice and voice["lang"] != "en" and END_MARKS.search(text):
        return END_MARKS.sub("", text).rstrip() + "।"
    return text


def check_voice(voice: dict, texts: list[dict], args, transcriber) -> dict:
    model_dir = Path(args.models_dir) / voice["archive"]
    tts = bench.build_tts(voice, model_dir, args.threads)
    wav_dir = Path(args.out) / "wav" / voice["id"]
    rows, written = [], []
    for text in texts:
        samples, sr, _ = bench.synthesize(tts, voice, voice["sid"], spoken(text["text"], voice))
        wav = wav_dir / f"{text['id']}.wav"
        bench.write_wav(wav, samples, sr)
        written.append(wav)
        asr = transcriber.transcribe(samples, sr, voice["lang"]) if transcriber.available(voice["lang"]) else ""
        heard_text = to_script(asr, voice["lang"])
        # A transcript in another script (Hindi heard as Urdu) says nothing about the voice: not scored.
        scored = bool(heard_text) and bench.script_share(heard_text, voice["lang"]) >= bench.SCRIPT_MATCH_MIN
        missed = [w for w in text["words"] if not heard_as(w, heard_text.split())] if scored else []
        rows.append({
            "id": text["id"], "rule": text["rule"], "asr": asr, "scored": scored, "missed": missed,
            "words": len(text["words"]) if scored else 0,
            "cer": round(bench.cer(text["text"], heard_text), 3) if scored else None,
        })
    if args.mp3:
        mp3_dir = Path(args.out) / "mp3"
        mp3_dir.mkdir(parents=True, exist_ok=True)
        bench.concat_mp3(written, tts.sample_rate, mp3_dir / f"{voice['id']}.mp3")
    return {"voice": voice["id"], "label": voice["label"], "lang": voice["lang"], "texts": rows}


def markdown(results: list[dict]) -> str:
    lines = ["| Voice | Rule | Words heard | Missed |", "|---|---|---:|---|"]
    for result in results:
        by_rule: dict[str, list[dict]] = defaultdict(list)
        for row in result["texts"]:
            by_rule[row["rule"]].append(row)
        for rule, rows in by_rule.items():
            total = sum(r["words"] for r in rows)
            missed = [w for r in rows for w in r["missed"]]
            unscored = sum(not r["scored"] for r in rows)
            note = f" ({unscored} sentence(s) not scored: ASR wrote another script)" if unscored else ""
            lines.append(f"| {result['voice']} | {rule} | {total - len(missed)}/{total} | {' '.join(missed)}{note} |")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=CATALOG)
    parser.add_argument("--models-dir", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--asr-en", default="")
    parser.add_argument("--asr-indic", default="")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--only", default="", help="comma-separated voice ids")
    parser.add_argument("--download", action="store_true", help="fetch missing voice zips from the voices release")
    parser.add_argument("--mp3", action="store_true")
    args = parser.parse_args(argv)

    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    texts = json.loads(CORPUS.read_text(encoding="utf-8"))["texts"]
    only = {v for v in args.only.split(",") if v}
    entries = [e for e in catalog["voices"] if not only or e["id"] in only]
    transcriber = bench.Transcriber(args.asr_en, args.asr_indic, args.threads)
    models_dir = Path(args.models_dir)
    models_dir.mkdir(parents=True, exist_ok=True)

    results = []
    for entry in entries:
        if args.download:
            download(entry, catalog["baseUrl"], models_dir)
        voice = bench_voice(entry)
        mine = [t for t in texts if t["lang"] == voice["lang"]]
        if mine:
            print(f"Checking {voice['id']}", flush=True)
            results.append(check_voice(voice, mine, args, transcriber))

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    table = markdown(results)
    (out / "results.md").write_text(table, encoding="utf-8")
    print(table)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
