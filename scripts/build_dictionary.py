#!/usr/bin/env python3
# File: scripts/build_dictionary.py
# Purpose: Builds the downloadable dictionaries (plan item 33, ADR-045): English from the SHA-pinned Open English
#          Wordnet release, Hindi and Bengali from Wiktionary as extracted by kaikki.org (ADR-047), byte-for-byte
#          reproducibly, and checks each against its pin in speech/models.json.
#
# Output: one zip holding
#   dictionary.tsv  one line per headword, sorted by code point so the app finds a word by binary search in the file
#                   without loading it:  headword TAB sense TAB sense ...  where a sense is  pos|definition|syn,syn
#                   and an inflected form the wordnet lists (abaci, went) is  form TAB =headword,headword
#   LICENSE         the wordnet's licence texts (CC BY 4.0, and Princeton WordNet's notice, which must appear on all
#                   copies, modifications included), joined.
# Why a text file: the app parses it with a few lines of Kotlin (no SQLite asset, no XML parser on the phone), and a
# binary search over sorted lines needs no index and no memory; the app unpacks it once after the download. Why
# deflated (unlike the voice zips): text shrinks from 16 MB to about 5 MB, which matters on a phone's data plan.
# Deflate's bytes depend on the zlib build, so the pin was computed with Python 3.13 and zlib 1.3 on Ubuntu 24.04,
# as CI runs; entries are dated 1980 with fixed permissions, so the same bytes come out on every run and the pin can be
# written before CI publishes the asset (as build_voice_mirror.py does). Standard library only.
#
#   python3 scripts/build_dictionary.py --out dist               # build and verify against the pin
#   python3 scripts/build_dictionary.py --out dist --print-pin   # build and print file/sha256/sizeBytes
#   python3 scripts/build_dictionary.py --out dist --published https://github.com/<repo>/releases/download/voices/
#                                                                # keep zips already published, build the rest
#
# Wiktionary sources ("format": "kaikki") are kaikki.org's per-language JSONL files, which are regenerated about
# weekly under the same name. A spec marked "measure": true is fetched without a source pin and only printed (source
# sha256 and size, then the built zip's pin), never checked, so CI can measure a snapshot this container cannot reach;
# the printed values are then written into the spec and "measure" is removed.

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sys
import unicodedata
import urllib.error
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
HTTP_NOT_FOUND = 404
# Wordnet parts of speech as the app shows them; "s" (adjective satellite) reads as an adjective.
POS = {"n": "n", "v": "v", "a": "a", "s": "a", "r": "r"}
FIELD_SEPARATORS = str.maketrans({"\t": " ", "|": "/", "\n": " ", "\r": " "})
# Wiktionary parts of speech as the app labels them (core/dictionary/Dictionary.kt PartOfSpeech); others read "x".
KAIKKI_POS = {
    "noun": "n", "name": "n", "verb": "v", "adj": "a", "adv": "r", "pron": "p", "postp": "o", "prep": "o",
    "conj": "c", "intj": "i", "num": "u",
}
# Form tags that are not another spelling of the word in its own script.
SKIPPED_FORM_TAGS = {"romanization", "transliteration", "table-tags", "inflection-template", "class", "canonical"}
# Joiners change only how a word is drawn; the app drops them from keys too (WordKey).
JOINERS = str.maketrans({"\u200c": None, "\u200d": None})
MAX_GLOSS_CHARS = 300
# The app reads a line of at most 64 KiB (DictionaryConfig.maxLineBytes); a word's later senses go past this.
MAX_LINE_BYTES = 60_000


def clean(text: str) -> str:
    return " ".join(text.translate(FIELD_SEPARATORS).split())


def key_of(word: str) -> str:
    """A word as the app keys it: NFC, joiners removed, lower case (WordKey.of)."""
    return unicodedata.normalize("NFC", clean(word)).translate(JOINERS).lower()


def fetch(url: str, sha256: str | None, cache: Path) -> Path:
    """Downloads [url] once into [cache]; with no [sha256] (a measuring run) it is fetched fresh and not checked."""
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / f"{(sha256 or 'unpinned')[:SHA_PREFIX_CHARS]}-{url.rsplit('/', 1)[-1]}"
    if sha256 is None:
        target.unlink(missing_ok=True)
    if not target.is_file():
        partial = target.with_suffix(target.suffix + ".partial")
        with urllib.request.urlopen(url) as response, partial.open("wb") as out:
            while chunk := response.read(CHUNK_BYTES):
                out.write(chunk)
        partial.rename(target)
    actual = sha256_of(target)
    if sha256 is not None and actual != sha256:
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


def read_kaikki(source: Path, lang: str) -> tuple[dict[str, list[str]], dict[str, set[str]]]:
    """Key -> senses (pos|gloss|syn,...) in Wiktionary's order; form -> keys of the words it is a form of."""
    senses: dict[str, list[str]] = {}
    forms: dict[str, set[str]] = {}
    with source.open(encoding="utf-8") as stream:
        for line in stream:
            entry = json.loads(line)
            if entry.get("lang_code") != lang or not entry.get("word"):
                continue
            key = key_of(entry["word"])
            pos = KAIKKI_POS.get(entry.get("pos", ""), "x")
            entry_synonyms = [s.get("word", "") for s in entry.get("synonyms", [])]
            for sense in entry.get("senses", []):
                read_sense(key, pos, sense, entry_synonyms, senses, forms)
            for form in entry.get("forms", []):
                written = key_of(form.get("form", ""))
                if written and written != key and not SKIPPED_FORM_TAGS & set(form.get("tags", [])) \
                        and not written.isascii():
                    forms.setdefault(written, set()).add(key)
    return senses, forms


def read_sense(key, pos, sense, entry_synonyms, senses, forms) -> None:
    """Adds one Wiktionary sense: a "form of" or "alternative of" sense indexes the form, any other its last gloss."""
    targets = sense.get("form_of", []) + sense.get("alt_of", [])
    if targets:
        for target in targets:
            word = key_of(target.get("word", ""))
            if word and word != key:
                forms.setdefault(key, set()).add(word)
        return
    glosses = sense.get("glosses") or []
    if not glosses:
        return
    # Wiktextract repeats a parent sense's gloss before a sub-sense's own; the last one is the sense itself.
    text = clean(glosses[-1])[:MAX_GLOSS_CHARS]
    words = [s.get("word", "") for s in sense.get("synonyms", [])] or entry_synonyms
    synonyms = [clean(w).replace(",", " ") for w in dict.fromkeys(words) if w and key_of(w) != key][:MAX_SYNONYMS]
    part = f"{pos}|{text}|{','.join(synonyms)}"
    entry = senses.setdefault(key, [])
    if part not in entry:
        entry.append(part)


def kaikki_lines(senses: dict[str, list[str]], forms: dict[str, set[str]]) -> list[str]:
    """Sorted lines: a word's senses, then (when it is also a form of other words) a last field pointing to them."""
    lines = []
    for key in sorted(set(senses) | set(forms)):
        headwords = sorted(word for word in forms.get(key, ()) if word in senses and word != key)
        redirect = [f"={','.join(headwords)}"] if headwords else []
        kept = list(senses.get(key, []))
        while kept and len("\t".join([key, *kept, *redirect]).encode("utf-8")) > MAX_LINE_BYTES:
            kept.pop()
        if kept or redirect:
            lines.append("\t".join([key, *kept, *redirect]))
    return lines


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def entry(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
    info.external_attr = FILE_MODE
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def build(spec: dict, cache: Path, out: Path) -> Path:
    pinned = None if spec.get("measure") else spec["source"]["sha256"]
    source = fetch(spec["source"]["url"], pinned, cache)
    if spec.get("measure"):
        print(json.dumps({"id": spec["id"], "source": {"sha256": sha256_of(source), "sizeBytes": source.stat().st_size}}))
    if spec["source"].get("format") == "kaikki":
        lines = kaikki_lines(*read_kaikki(source, spec["lang"]))
    else:
        senses, synsets, forms = read_wordnet(source)
        lines = lines_of(senses, synsets, forms)
    text = "\n".join(lines) + "\n"
    out.mkdir(parents=True, exist_ok=True)
    staging = out / f"{spec['id']}.zip.partial"
    with zipfile.ZipFile(staging, "w", compresslevel=DEFLATE_LEVEL) as archive:
        licence = b"\n\n".join((repo_root() / name).read_bytes().rstrip() for name in spec["licenceFiles"]) + b"\n"
        archive.writestr(entry("LICENSE"), licence)
        archive.writestr(entry(spec["file"]), text.encode("utf-8"))
    sha = sha256_of(staging)
    final = out / f"{spec['id']}-{sha[:SHA_PREFIX_CHARS]}.zip"
    staging.rename(final)
    return final


def is_published(spec: dict, prefix: str, out: Path) -> bool:
    """True if the release already holds the pinned zip, byte for byte. A Wiktionary source is renamed in place about
    weekly, so once its zip is published the mirror never needs that snapshot again (ADR-047)."""
    out.mkdir(parents=True, exist_ok=True)
    target = out / spec["zip"]
    try:
        with urllib.request.urlopen(prefix + spec["zip"]) as response, target.open("wb") as stream:
            while chunk := response.read(CHUNK_BYTES):
                stream.write(chunk)
    except urllib.error.HTTPError as error:
        target.unlink(missing_ok=True)
        if error.code == HTTP_NOT_FOUND:
            return False
        raise
    if sha256_of(target) == spec["sha256"]:
        return True
    target.unlink()
    raise SystemExit(f"{spec['id']}: the published {spec['zip']} does not match its pin")


def text_lines(built: Path, name: str) -> int:
    with zipfile.ZipFile(built) as archive:
        return archive.read(name).count(b"\n")


def main(argv: list[str]) -> int:
    repo = repo_root()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=repo / "speech" / "models.json")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=repo / "build" / "dictionary-sources")
    parser.add_argument("--print-pin", action="store_true")
    parser.add_argument("--published", help="the release download URL prefix; a pinned zip already there is kept")
    args = parser.parse_args(argv)
    problems = []
    for spec in json.loads(args.catalog.read_text(encoding="utf-8")).get("dictionaries", []):
        if args.published and not spec.get("measure") and is_published(spec, args.published, args.out):
            print(f"{spec['id']}: {spec['zip']} already published with its pin; not rebuilt")
            continue
        built = build(spec, args.cache, args.out)
        sha, size = sha256_of(built), built.stat().st_size
        if args.print_pin or spec.get("measure"):
            with zipfile.ZipFile(built) as archive:
                unpacked = archive.getinfo(spec["file"]).file_size
            print(json.dumps({"id": spec["id"], "zip": built.name, "sha256": sha, "sizeBytes": size,
                              "unpackedBytes": unpacked, "lines": text_lines(built, spec["file"])}))
        elif (built.name, sha, size) != (spec.get("zip"), spec.get("sha256"), spec.get("sizeBytes")):
            problems.append(f"{spec['id']}: built {built.name} {sha} {size}, pinned "
                            f"{spec.get('zip')} {spec.get('sha256')} {spec.get('sizeBytes')}")
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
