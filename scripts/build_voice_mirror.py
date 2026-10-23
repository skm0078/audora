#!/usr/bin/env python3
# File: scripts/build_voice_mirror.py
# Purpose: Builds the downloadable voice zips named in speech/models.json from their SHA-pinned upstream
#          sherpa-onnx archives, byte-for-byte reproducibly, and checks them against the catalog's pins (ADR-022).
#          A source is a sherpa-onnx .tar.bz2 by default; a voice sherpa-onnx does not publish (the Bengali Piper and MMS
#          conversions, P3-06) names a pinned .zip on this repo's s1-voices release instead (source.url, ADR-030).
#          The catalog's "models" are built the same way: the opt-in English punctuation model (RI-11) comes from
#          sherpa-onnx's punctuation-models release (source.url to a .tar.bz2) and keeps only its int8 weights;
#          the voice-command keyword spotter (plan item 38) comes from its kws-models release the same way.
#
# Why zips: the phone has no bzip2 (adding one is a new dependency) and java.util.zip reads zips. Why reproducible:
# the pins are written into the catalog before the release asset exists, and CI rebuilds the same bytes to publish.
# Entries are sorted, stored uncompressed (deflate output depends on the zlib build; the weights barely compress
# anyway), dated 1980-01-01 and carry fixed permissions. espeak-ng-data keeps only the dictionaries Audora speaks.
# Standard library only.
#
#   python3 scripts/build_voice_mirror.py --out dist              # build and verify every voice
#   python3 scripts/build_voice_mirror.py --out dist --print-pins # build and print file/sha256/sizeBytes

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tarfile
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path

ZIP_DATE = (1980, 1, 1, 0, 0, 0)
FILE_MODE = 0o100644 << 16
DICT_SUFFIX = "_dict"
ATTRIBUTION_FILES = ("MODEL_CARD", "LICENSE", "README.md")
DEFAULT_TOKENS = "tokens.txt"
SHA_PREFIX_CHARS = 12
# Supertonic's model is its tts.json (the catalog's "model") plus these files under fixed names (SupertonicParts in
# :speech); it has no tokens file.
SUPERTONIC = "supertonic"
SUPERTONIC_PARTS = (
    "duration_predictor.int8.onnx",
    "text_encoder.int8.onnx",
    "vector_estimator.int8.onnx",
    "vocoder.int8.onnx",
    "unicode_indexer.bin",
)
CHUNK_BYTES = 1 << 20
TAR_SUFFIX = ".tar.bz2"
ZIP_SUFFIX = ".zip"


@dataclass(frozen=True)
class Voice:
    id: str
    family: str
    model: str
    tokens: str | None
    voices: str | None
    lexicon: str | None
    data_dir: str | None
    archive: str
    source_sha256: str
    file: str
    sha256: str
    size_bytes: int
    # Full URL of a .zip or .tar.bz2 source; None means sourceBaseUrl + archive + ".tar.bz2".
    source_url: str | None = None
    # More single files a model needs ("extraFiles"): the keyword spotter's decoder and joiner (plan item 38).
    extra_files: tuple[str, ...] = ()

    def source(self, base_url: str) -> str:
        return self.source_url or f"{base_url}{self.archive}{TAR_SUFFIX}"

    def required_files(self) -> list[str]:
        """Single files the voice cannot load without (espeak-ng-data is checked separately)."""
        optional = [self.voices, self.lexicon]
        if self.family == SUPERTONIC:
            return [self.model, *SUPERTONIC_PARTS, *[name for name in optional if name]]
        # "tokens": null for a model with no token table in its zip (the acted voice's tokenizer is an app asset).
        return [self.model, *[name for name in (self.tokens, *optional) if name], *self.extra_files]


@dataclass(frozen=True)
class Built:
    voice: Voice
    path: Path
    sha256: str
    size_bytes: int

    @property
    def pinned_name(self) -> str:
        return f"{self.voice.id}-{self.sha256[:SHA_PREFIX_CHARS]}.zip"

    def problems(self) -> list[str]:
        found = []
        if self.voice.sha256 != self.sha256:
            found.append(f"sha256 is {self.sha256}, catalog pins {self.voice.sha256}")
        if self.voice.size_bytes != self.size_bytes:
            found.append(f"size is {self.size_bytes}, catalog pins {self.voice.size_bytes}")
        if self.voice.file != self.pinned_name:
            found.append(f"file should be {self.pinned_name}, catalog says {self.voice.file}")
        return found


def load_catalog(path: Path) -> tuple[dict, list[Voice]]:
    """The voices, then the other models (root "models", RI-11); both are built and pinned alike."""
    root = json.loads(path.read_text(encoding="utf-8"))
    voices = []
    for entry in [*root["voices"], *root.get("models", [])]:
        source = entry["source"]
        voices.append(
            Voice(
                id=entry["id"],
                family=entry["family"],
                model=entry["model"],
                tokens=entry.get("tokens", DEFAULT_TOKENS),
                voices=entry.get("voices"),
                lexicon=entry.get("lexicon"),
                data_dir=entry.get("dataDir"),
                archive=source["archive"],
                source_sha256=source["sha256"].lower(),
                file=entry["file"],
                sha256=entry["sha256"].lower(),
                size_bytes=int(entry["sizeBytes"]),
                source_url=source.get("url"),
                extra_files=tuple(entry.get("extraFiles", [])),
            )
        )
    ids = [v.id for v in voices]
    if len(set(ids)) != len(ids):
        raise ValueError(f"duplicate voice ids in {path}: {ids}")
    for v in voices:
        url = v.source_url
        if url is not None and not (url.startswith("https://") and url.endswith((ZIP_SUFFIX, TAR_SUFFIX))):
            raise ValueError(f"{v.id}: source.url must be an https .zip or .tar.bz2, is {url}")
    return root, voices


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(CHUNK_BYTES):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_source(voice: Voice, base_url: str, cache: Path) -> Path:
    """Downloads the upstream archive once (cached by its SHA-256) and fails on a mismatch."""
    url = voice.source(base_url)
    cached = cache / (voice.source_sha256 + (ZIP_SUFFIX if url.endswith(ZIP_SUFFIX) else TAR_SUFFIX))
    if cached.is_file() and sha256_of(cached) == voice.source_sha256:
        return cached
    cache.mkdir(parents=True, exist_ok=True)
    partial = cached.with_suffix(".partial")
    print(f"Downloading {url}", flush=True)
    with urllib.request.urlopen(url) as response, partial.open("wb") as out:  # noqa: S310 (pinned https URL)
        while chunk := response.read(CHUNK_BYTES):
            out.write(chunk)
    actual = sha256_of(partial)
    if actual != voice.source_sha256:
        partial.unlink()
        raise ValueError(f"{url} has SHA-256 {actual}, expected {voice.source_sha256}")
    partial.rename(cached)
    return cached


def wanted(voice: Voice, relative: str, dictionaries: set[str]) -> bool:
    """Whether a path inside the archive (without its top folder) goes into the zip."""
    singles = {*voice.required_files(), *ATTRIBUTION_FILES}
    if relative in singles:
        return True
    if voice.data_dir and relative.startswith(voice.data_dir + "/"):
        name = relative.rsplit("/", 1)[-1]
        return not name.endswith(DICT_SUFFIX) or name in dictionaries
    return False


def read_members(voice: Voice, archive: Path, dictionaries: set[str]) -> dict[str, bytes]:
    """The wanted files under the archive's top folder (named like the archive), from a .tar.bz2 or a .zip."""
    prefix = voice.archive + "/"
    members: dict[str, bytes] = {}
    if archive.name.endswith(ZIP_SUFFIX):
        with zipfile.ZipFile(archive) as zf:
            for info in zf.infolist():
                relative = info.filename[len(prefix):]
                if not info.is_dir() and info.filename.startswith(prefix) and wanted(voice, relative, dictionaries):
                    members[relative] = zf.read(info)
        return members
    with tarfile.open(archive, mode="r:bz2") as tar:
        for member in tar:
            # Some upstream archives name their members "./folder/...".
            name = member.name.removeprefix("./")
            if not member.isfile() or not name.startswith(prefix):
                continue
            relative = name[len(prefix):]
            if wanted(voice, relative, dictionaries):
                extracted = tar.extractfile(member)
                if extracted is None:
                    raise ValueError(f"{member.name} unreadable")
                members[relative] = extracted.read()
    return members


def build_zip(voice: Voice, archive: Path, out_dir: Path, espeak_dictionaries: list[str]) -> Built:
    dictionaries = {f"{lang}{DICT_SUFFIX}" for lang in espeak_dictionaries}
    members = read_members(voice, archive, dictionaries)

    missing = [name for name in voice.required_files() if name not in members]
    if voice.data_dir and not any(name.startswith(voice.data_dir + "/") for name in members):
        missing.append(voice.data_dir)
    if missing:
        raise ValueError(f"{voice.archive} lacks {missing}")

    out_dir.mkdir(parents=True, exist_ok=True)
    staging = out_dir / f"{voice.id}.zip.partial"
    with zipfile.ZipFile(staging, "w", compression=zipfile.ZIP_STORED) as zf:
        for name in sorted(members):
            info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = FILE_MODE
            info.create_system = 3  # Unix, whatever the host
            zf.writestr(info, members[name])
    digest = sha256_of(staging)
    built = Built(voice, staging, digest, staging.stat().st_size)
    final = out_dir / built.pinned_name
    staging.replace(final)
    return Built(voice, final, digest, built.size_bytes)


def main(argv: list[str]) -> int:
    repo = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=repo / "speech" / "models.json")
    parser.add_argument("--out", type=Path, required=True, help="where the zips and SHA256SUMS.txt go")
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "audora-voice-sources")
    parser.add_argument("--print-pins", action="store_true", help="print the pins instead of checking them")
    args = parser.parse_args(argv)

    root, voices = load_catalog(args.catalog)
    built = []
    for voice in voices:
        archive = fetch_source(voice, root["sourceBaseUrl"], args.cache)
        built.append(build_zip(voice, archive, args.out, root["espeakDictionaries"]))

    sums = "".join(f"{b.sha256}  {b.path.name}\n" for b in built)
    (args.out / "SHA256SUMS.txt").write_text(sums, encoding="utf-8")

    failed = False
    for b in built:
        if args.print_pins:
            print(json.dumps({"id": b.voice.id, "file": b.pinned_name, "sha256": b.sha256, "sizeBytes": b.size_bytes}))
            continue
        problems = b.problems()
        status = "ok" if not problems else "; ".join(problems)
        print(f"{b.voice.id}: {status}")
        failed = failed or bool(problems)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
