#!/usr/bin/env python3
# File: scripts/build_asr_mirror.py
# Purpose: Builds the reading tutor's downloadable speech recognition models (plan item 67, ADR-050) named under
#          speech/models.json "asr", from SHA-pinned upstream files, byte-for-byte reproducibly, and checks each zip
#          against its pin.
#
# Each entry lists its sources: a sherpa-onnx .tar.bz2 whose wanted members are renamed to the fixed names the app
# loads ("files": {member path: name in the zip}), or a single file ("as": name in the zip). The entry's licence
# texts are joined into LICENSE. Zips are stored, sorted, dated 1980-01-01 with fixed permissions, like the voice zips
# (build_voice_mirror.py): the weights barely compress, and stored output does not depend on the zlib build.
# An entry may name a "licenceCheck" ({"url", "contains"}): the upstream page that states the licence must still say
# so, or the build fails, so a model whose licence was taken from its card is re-checked on every mirror run.
# Standard library only.
#
#   python3 scripts/build_asr_mirror.py --out dist               # build and verify every model against its pin
#   python3 scripts/build_asr_mirror.py --out dist --print-pins  # build and print file/sha256/sizeBytes/unpackedBytes

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

ZIP_DATE = (1980, 1, 1, 0, 0, 0)
FILE_MODE = 0o100644 << 16
UNIX = 3
SHA_PREFIX_CHARS = 12
CHUNK_BYTES = 1 << 20
TAR_SUFFIX = ".tar.bz2"
LICENCE_NAME = "LICENSE"


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(CHUNK_BYTES):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str, sha256: str, cache: Path) -> Path:
    """Downloads [url] once (cached by its SHA-256) and fails on a mismatch."""
    if not url.startswith("https://"):
        raise ValueError(f"source must be https: {url}")
    cached = cache / sha256
    if cached.is_file() and sha256_of(cached) == sha256:
        return cached
    cache.mkdir(parents=True, exist_ok=True)
    partial = cache / (sha256 + ".partial")
    print(f"Downloading {url}", flush=True)
    with urllib.request.urlopen(url) as response, partial.open("wb") as out:  # noqa: S310 (pinned https URL)
        while chunk := response.read(CHUNK_BYTES):
            out.write(chunk)
    actual = sha256_of(partial)
    if actual != sha256:
        partial.unlink()
        raise ValueError(f"{url} has SHA-256 {actual}, expected {sha256}")
    partial.rename(cached)
    return cached


def members_of(source: dict, cache: Path) -> dict[str, bytes]:
    """The zip entries one source gives: renamed archive members, or the single file under its "as" name."""
    path = fetch(source["url"], source["sha256"].lower(), cache)
    if "as" in source:
        return {source["as"]: path.read_bytes()}
    if not source["url"].endswith(TAR_SUFFIX):
        raise ValueError(f"an archive source must be a .tar.bz2: {source['url']}")
    wanted: dict[str, str] = source["files"]
    found: dict[str, bytes] = {}
    with tarfile.open(path, mode="r:bz2") as tar:
        for member in tar:
            name = member.name.removeprefix("./")
            if member.isfile() and name in wanted:
                extracted = tar.extractfile(member)
                if extracted is None:
                    raise ValueError(f"{name} unreadable")
                found[wanted[name]] = extracted.read()
    missing = sorted(member for member, name in wanted.items() if name not in found)
    if missing:
        raise ValueError(f"{source['url']} lacks {missing}")
    return found


def entry(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = FILE_MODE
    info.create_system = UNIX
    return info


def build(spec: dict, cache: Path, out: Path) -> tuple[Path, str, int, int]:
    """Builds one model's zip; returns its path, SHA-256, size and the bytes it unpacks to."""
    members: dict[str, bytes] = {}
    for source in spec["sources"]:
        for name, data in members_of(source, cache).items():
            if name in members:
                raise ValueError(f"{spec['id']}: two sources give {name}")
            members[name] = data
    root = repo_root()
    members[LICENCE_NAME] = b"\n\n".join((root / name).read_bytes().rstrip() for name in spec["licenceFiles"]) + b"\n"
    out.mkdir(parents=True, exist_ok=True)
    staging = out / f"{spec['id']}.zip.partial"
    with zipfile.ZipFile(staging, "w", compression=zipfile.ZIP_STORED) as archive:
        for name in sorted(members):
            archive.writestr(entry(name), members[name])
    digest = sha256_of(staging)
    final = out / f"{spec['id']}-{digest[:SHA_PREFIX_CHARS]}.zip"
    staging.replace(final)
    return final, digest, final.stat().st_size, sum(len(data) for data in members.values())


def licence_problems(spec: dict, read=None) -> list[str]:
    """An entry's "licenceCheck": its page must contain the text, ignoring case; [read] fetches a URL's text."""
    check = spec.get("licenceCheck")
    if check is None:
        return []
    if not check["url"].startswith("https://"):
        return [f"licence check must be https: {check['url']}"]
    fetch_text = read or (lambda url: urllib.request.urlopen(url).read().decode("utf-8", "replace"))  # noqa: S310
    try:
        text = fetch_text(check["url"])
    except OSError as error:
        return [f"licence page unreachable ({error.__class__.__name__}): {check['url']}"]
    if check["contains"].lower() not in text.lower():
        return [f"licence page no longer says {check['contains']!r}: {check['url']}"]
    return []


def problems(spec: dict, built: Path, digest: str, size: int, unpacked: int) -> list[str]:
    found = []
    if spec["sha256"].lower() != digest:
        found.append(f"sha256 is {digest}, catalog pins {spec['sha256']}")
    if int(spec["sizeBytes"]) != size:
        found.append(f"size is {size}, catalog pins {spec['sizeBytes']}")
    if int(spec["unpackedBytes"]) != unpacked:
        found.append(f"unpacked size is {unpacked}, catalog pins {spec['unpackedBytes']}")
    if spec["file"] != built.name:
        found.append(f"file should be {built.name}, catalog says {spec['file']}")
    return found


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=repo_root() / "speech" / "models.json")
    parser.add_argument("--out", type=Path, required=True, help="where the zips go")
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "audora-asr-sources")
    parser.add_argument("--print-pins", action="store_true", help="print the pins instead of checking them")
    args = parser.parse_args(argv)

    specs = json.loads(args.catalog.read_text(encoding="utf-8")).get("asr", [])
    ids = [spec["id"] for spec in specs]
    if len(set(ids)) != len(ids):
        raise ValueError(f"duplicate asr ids: {ids}")
    failed = False
    for spec in specs:
        built, digest, size, unpacked = build(spec, args.cache, args.out)
        if args.print_pins:
            pin = {"id": spec["id"], "file": built.name, "sha256": digest, "sizeBytes": size, "unpackedBytes": unpacked}
            print(json.dumps(pin))
            continue
        found = problems(spec, built, digest, size, unpacked) + licence_problems(spec)
        print(f"{spec['id']}: {'ok' if not found else '; '.join(found)}")
        failed = failed or bool(found)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
