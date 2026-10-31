#!/usr/bin/env python3
# File: scripts/check_store_metadata.py
# Purpose: Checks the store text and graphics under fastlane/metadata/android, the one copy that F-Droid,
#          IzzyOnDroid and the Play listing all read (ADR 2026-10-31-fdroid-ready-build).
#
# Checks: every locale has a title, short and full description within Play's limits (the strictest of the three
# stores); the default locale exists; changelogs fit; the icon and feature graphic have Play's sizes and are byte for
# byte the masters in docs/store/, so the two copies cannot drift. With --require-changelog, the version in
# gradle.properties must have a default-locale changelog (the release workflow passes it).
#
# Usage: python3 scripts/check_store_metadata.py [--root .] [--require-changelog]
# Standard library only.

from __future__ import annotations

import argparse
import re
import struct
import sys
from dataclasses import dataclass
from pathlib import Path

METADATA_DIR = Path("fastlane/metadata/android")
DEFAULT_LOCALE = "en-US"
STORE_MASTERS_DIR = Path("docs/store")
GRADLE_PROPERTIES = Path("gradle.properties")
VERSION_PROPERTY = re.compile(r"^audora\.versionName=(\d+)\.(\d+)\.(\d+)\s*$", re.MULTILINE)


@dataclass(frozen=True)
class TextLimit:
    file: str
    max_chars: int


# Play's limits (Play Console help, "Create and set up your app"); F-Droid allows a 50-character title and the same
# 80 / 4000 for the descriptions, so passing Play's limits passes all three stores.
TEXT_LIMITS = (
    TextLimit("title.txt", 30),
    TextLimit("short_description.txt", 80),
    TextLimit("full_description.txt", 4000),
)
# F-Droid and fastlane supply both cap a changelog at 500 characters.
CHANGELOG_MAX_CHARS = 500


@dataclass(frozen=True)
class Graphic:
    file: str
    width: int
    height: int
    master: str


GRAPHICS = (
    Graphic("icon.png", 512, 512, "icon.png"),
    Graphic("featureGraphic.png", 1024, 500, "feature-graphic.png"),
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_size(data: bytes) -> tuple[int, int] | None:
    """Width and height from a PNG's IHDR chunk, or None if this is not a PNG."""
    if not data.startswith(PNG_SIGNATURE) or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def version_code(root: Path) -> int:
    """The versionCode app/build.gradle.kts derives from audora.versionName: MAJOR * 1e6 + MINOR * 1e3 + PATCH."""
    match = VERSION_PROPERTY.search((root / GRADLE_PROPERTIES).read_text(encoding="utf-8"))
    if match is None:
        raise ValueError(f"{GRADLE_PROPERTIES} has no audora.versionName=MAJOR.MINOR.PATCH line")
    major, minor, patch = (int(part) for part in match.groups())
    return major * 1_000_000 + minor * 1_000 + patch


def check_texts(locale_dir: Path) -> list[str]:
    problems = []
    for limit in TEXT_LIMITS:
        path = locale_dir / limit.file
        if not path.is_file():
            problems.append(f"{path}: missing")
            continue
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            problems.append(f"{path}: empty")
        elif len(text) > limit.max_chars:
            problems.append(f"{path}: {len(text)} characters, the limit is {limit.max_chars}")
    return problems


def check_changelogs(locale_dir: Path) -> list[str]:
    problems = []
    changelogs = locale_dir / "changelogs"
    for path in sorted(changelogs.glob("*")) if changelogs.is_dir() else []:
        if not re.fullmatch(r"\d+\.txt", path.name):
            problems.append(f"{path}: name it <versionCode>.txt")
            continue
        text = path.read_text(encoding="utf-8").strip()
        if not text or len(text) > CHANGELOG_MAX_CHARS:
            problems.append(f"{path}: {len(text)} characters, must be 1 to {CHANGELOG_MAX_CHARS}")
    return problems


def check_graphics(root: Path, images_dir: Path) -> list[str]:
    problems = []
    for graphic in GRAPHICS:
        path = images_dir / graphic.file
        if not path.is_file():
            problems.append(f"{path}: missing")
            continue
        data = path.read_bytes()
        size = png_size(data)
        if size != (graphic.width, graphic.height):
            problems.append(f"{path}: must be a {graphic.width} x {graphic.height} PNG, found {size or 'not a PNG'}")
        master = root / STORE_MASTERS_DIR / graphic.master
        if not master.is_file() or master.read_bytes() != data:
            problems.append(
                f"{path}: must be a copy of {STORE_MASTERS_DIR / graphic.master}; re-copy it after changing the master"
            )
    return problems


def check(root: Path, require_changelog: bool) -> list[str]:
    base = root / METADATA_DIR
    if not (base / DEFAULT_LOCALE).is_dir():
        return [f"{base / DEFAULT_LOCALE}: the default locale is missing"]
    problems = []
    for locale_dir in sorted(p for p in base.iterdir() if p.is_dir()):
        problems += check_texts(locale_dir)
        problems += check_changelogs(locale_dir)
    problems += check_graphics(root, base / DEFAULT_LOCALE / "images")
    if require_changelog:
        code = version_code(root)
        changelog = base / DEFAULT_LOCALE / "changelogs" / f"{code}.txt"
        if not changelog.is_file():
            problems.append(f"{changelog}: missing; F-Droid shows it as this version's What's new")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--require-changelog", action="store_true")
    args = parser.parse_args(argv)
    try:
        problems = check(args.root, args.require_changelog)
    except (OSError, ValueError) as error:
        problems = [str(error)]
    for problem in problems:
        print(f"::error::{problem}", file=sys.stderr)
    if not problems:
        print("Store metadata OK")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
