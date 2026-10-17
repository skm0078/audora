#!/usr/bin/env python3
# File: scripts/build_dictionary.py
# Purpose: Builds the downloadable English dictionary (plan item 33, ADR-045) from the SHA-pinned Open English
#          Wordnet release, byte-for-byte reproducibly, and checks it against the pin in speech/models.json.
#
# Output: one zip holding
#   dictionary.tsv  one line per headword, sorted by code point so the app finds a word by binary search in the file
#                   without loading it:  headword TAB sense TAB sense ...  where a sense is  pos|definition|syn,syn
#                   and an inflected form the wordnet lists (abaci, went) is  form TAB =headword,headword
#   LICENSE         the wordnet's licence text (CC BY 4.0 and the Princeton WordNet License), as it requires.
# Why a text file: the app parses it with a few lines of Kotlin (no SQLite asset, no XML parser on the phone), and a
# binary search over sorted lines needs no index and no memory; the app unpacks it once after the download. Why
# deflated (unlike the voice zips): text shrinks from 16 MB to about 5 MB, which matters on a phone's data plan.
# Deflate's bytes depend on the zlib build, so the pin was computed with Python 3.13 and zlib 1.3 on Ubuntu 24.04,
# as CI runs; entries are dated 1980 with fixed permissions, so the same bytes come out on every run and the pin can be
# written before CI publishes the asset (as build_voice_mirror.py does). Standard library only.
#
#   python3 scripts/build_dictionary.py --out dist               # build and verify against the pin
#   python3 scripts/build_dictionary.py --out dist --print-pin   # build and print file/sha256/sizeBytes

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sys
import urllib.request
import xml.etree.ElementTree as ElementTree
import zipfile
from pathlib import Path

ZIP_DATE = (1980, 1, 1, 0, 0, 0)
FILE_MODE = 0o100644 << 16
SHA_PREFIX_CHARS = 12
MAX_SYNONYMS = 5
DEFLATE_LEVEL = 9
CHUNK_BYTES = 1 << 20
# Wordnet parts of speech as the app shows them; "s" (adjective satellite) reads as an adjective.
POS = {"n": "n", "v": "v", "a": "a", "s": "a", "r": "r"}
FIELD_SEPARATORS = str.maketrans({"\t": " ", "|": "/", "\n": " ", "\r": " "})


def clean(text: str) -> str:
    return " ".join(text.translate(FIELD_SEPARATORS).split())


def fetch(url: str, sha256: str, cache: Path) -> Path:
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / f"{sha256[:SHA_PREFIX_CHARS]}-{url.rsplit('/', 1)[-1]}"
    if not target.is_file():
        partial = target.with_suffix(target.suffix + ".partial")
        with urllib.request.urlopen(url) as response, partial.open("wb") as out:
            while chunk := response.read(CHUNK_BYTES):
                out.write(chunk)
        partial.rename(target)
    actual = sha256_of(target)
    if actual != sha256:
        target.unlink()
        raise SystemExit(f"{url}: sha256 {actual} does not match the pin {sha256}")
    return target


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(CHUNK_BYTES):
            digest.update(chunk)
    return digest.hexdigest()


def read_wordnet(source: Path) -> tuple[dict[str, list[str]], dict[str, tuple[str, str, list[str]]], dict[str, set[str]]]:
    """Headword -> synset ids in sense order; synset id -> (pos, definition, member headwords); form -> headwords."""
    senses: dict[str, list[str]] = {}
    members: dict[str, list[str]] = {}
    forms: dict[str, set[str]] = {}
    synsets: dict[str, tuple[str, str]] = {}
    with gzip.open(source, "rb") as stream:
        for _, element in ElementTree.iterparse(stream, events=("end",)):
            if element.tag == "LexicalEntry":
                lemma = element.find("Lemma")
                if lemma is not None:
                    word = clean(lemma.get("writtenForm", ""))
                    key = word.lower()
                    for sense in element.findall("Sense"):
                        synset = sense.get("synset", "")
                        senses.setdefault(key, []).append(synset)
                        members.setdefault(synset, []).append(word)
                    for form in element.findall("Form"):
                        written = clean(form.get("writtenForm", "")).lower()
                        if written and written != key:
                            forms.setdefault(written, set()).add(key)
                element.clear()
            elif element.tag == "Synset":
                definition = element.find("Definition")
                text = clean(definition.text or "") if definition is not None else ""
                synsets[element.get("id", "")] = (POS.get(element.get("partOfSpeech", ""), "n"), text)
                element.clear()
    joined = {sid: (pos, text, members.get(sid, [])) for sid, (pos, text) in synsets.items()}
    return senses, joined, forms


def lines_of(senses, synsets, forms) -> list[str]:
    lines = []
    for key in sorted(set(senses) | set(forms)):
        if key in senses:
            parts = []
            for synset in dict.fromkeys(senses[key]):  # Sense order, each synset once.
                pos, text, words = synsets.get(synset, ("n", "", []))
                if not text:
                    continue
                synonyms = [w for w in dict.fromkeys(words) if w.lower() != key][:MAX_SYNONYMS]
                parts.append(f"{pos}|{text}|{','.join(synonyms)}")
            if parts:
                lines.append(key + "\t" + "\t".join(parts))
                continue
        if key in forms:
            lines.append(f"{key}\t={','.join(sorted(forms[key]))}")
    return lines


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def entry(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
    info.external_attr = FILE_MODE
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def build(spec: dict, cache: Path, out: Path) -> Path:
    source = fetch(spec["source"]["url"], spec["source"]["sha256"], cache)
    senses, synsets, forms = read_wordnet(source)
    text = "\n".join(lines_of(senses, synsets, forms)) + "\n"
    out.mkdir(parents=True, exist_ok=True)
    staging = out / f"{spec['id']}.zip.partial"
    with zipfile.ZipFile(staging, "w", compresslevel=DEFLATE_LEVEL) as archive:
        archive.writestr(entry("LICENSE"), (repo_root() / spec["licenceFile"]).read_bytes())
        archive.writestr(entry(spec["file"]), text.encode("utf-8"))
    sha = sha256_of(staging)
    final = out / f"{spec['id']}-{sha[:SHA_PREFIX_CHARS]}.zip"
    staging.rename(final)
    return final


def main(argv: list[str]) -> int:
    repo = repo_root()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=repo / "speech" / "models.json")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=repo / "build" / "dictionary-sources")
    parser.add_argument("--print-pin", action="store_true")
    args = parser.parse_args(argv)
    problems = []
    for spec in json.loads(args.catalog.read_text(encoding="utf-8")).get("dictionaries", []):
        built = build(spec, args.cache, args.out)
        sha, size = sha256_of(built), built.stat().st_size
        if args.print_pin:
            print(json.dumps({"id": spec["id"], "zip": built.name, "sha256": sha, "sizeBytes": size}))
        elif (built.name, sha, size) != (spec.get("zip"), spec.get("sha256"), spec.get("sizeBytes")):
            problems.append(f"{spec['id']}: built {built.name} {sha} {size}, pinned "
                            f"{spec.get('zip')} {spec.get('sha256')} {spec.get('sizeBytes')}")
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
