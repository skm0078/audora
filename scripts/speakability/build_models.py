#!/usr/bin/env python3
# File: scripts/speakability/build_models.py
# Purpose: Builds the word lists the speakability score reads (RI-2a, ADR-035) from wordfreq's openly licensed data.
#
# One gzipped list per language in pdf/src/main/resources/.../speakability/, most frequent word first:
#
#   # audora-words 1 <lang> wordfreq <version> CC-BY-SA-4.0
#   <word>\t<Zipf frequency × 10, rounded>
#
# :pdf's SpeakabilityModels reads them: the set of words is the dictionary signal, and the letter-trigram model is
# trained from the same words when a language is first needed, so there is one file per language and no second model
# to keep in step. RI-3 (OCR repair) ranks corrections by the frequency column.
#
# Words are NFC, without zero-width joiners, made only of the language's letters and marks (English also keeps an
# inner apostrophe), English lower-cased. Output is byte-for-byte reproducible for a given wordfreq version.
#
#   python3 -m venv /tmp/wf && /tmp/wf/bin/pip install wordfreq==3.1.1
#   /tmp/wf/bin/python scripts/speakability/build_models.py
#
# Data: wordfreq (https://github.com/rspeer/wordfreq), data CC BY-SA 4.0, built from Wikipedia, OpenSubtitles,
# news, books and web text; credited in docs/adr/035-speakability-layer.md and the app's licence notices.

import gzip
import re
import sys
import unicodedata
from pathlib import Path

WORDFREQ_VERSION = "3.1.1"
REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "pdf" / "src" / "main" / "resources" / "io" / "github" / "skm0078" / "audora" / "pdf" / "speakability"

# How many of the most frequent words each list keeps. Coverage of the golden corpus's expected texts (share of their
# words found): English 80k ≈ 95–100%; Bengali 100k ≈ 91–97%; Hindi has only wordfreq's small list (26.6k, ≈ 92%).
TOP_N = {"en": 80_000, "hi": 100_000, "bn": 100_000}
WORD = {
    "en": re.compile(r"[a-z]+(?:'[a-z]+)*"),
    "hi": re.compile(r"[ऀ-ॣ०-ॿ]+"),
    "bn": re.compile(r"[ঀ-৿]+"),
}
# Digits are numbers, not words: the scorer treats them separately.
DIGITS = re.compile(r"[०-९০-৯]")
JOINERS = re.compile("[‌‍]")


def words(lang):
    import wordfreq
    best = {}
    for word in wordfreq.top_n_list(lang, TOP_N[lang], wordlist="best"):
        clean = JOINERS.sub("", unicodedata.normalize("NFC", word))
        if lang == "en":
            clean = clean.lower()
        if not WORD[lang].fullmatch(clean) or DIGITS.search(clean):
            continue
        zipf = round(wordfreq.zipf_frequency(word, lang, wordlist="best") * 10)
        if zipf > best.get(clean, -1):
            best[clean] = zipf
    # Most frequent first; ties in a fixed order so the file is reproducible.
    return sorted(best.items(), key=lambda item: (-item[1], item[0]))


def main():
    from importlib.metadata import version
    found = version("wordfreq")
    if found != WORDFREQ_VERSION:
        sys.exit(f"wordfreq {found} found; the lists are built with {WORDFREQ_VERSION}")
    OUT.mkdir(parents=True, exist_ok=True)
    for lang in TOP_N:
        entries = words(lang)
        text = f"# audora-words 1 {lang} wordfreq {WORDFREQ_VERSION} CC-BY-SA-4.0\n"
        text += "".join(f"{word}\t{zipf}\n" for word, zipf in entries)
        target = OUT / f"{lang}.words.gz"
        with gzip.GzipFile(target, mode="wb", compresslevel=9, mtime=0) as out:
            out.write(text.encode("utf-8"))
        print(f"wrote {target.relative_to(REPO)}: {len(entries)} words, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
