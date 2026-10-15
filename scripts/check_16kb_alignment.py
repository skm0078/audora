#!/usr/bin/env python3
# File: scripts/check_16kb_alignment.py
# Purpose: Fails the build if an APK's native libraries would not load cleanly on 16 KB page-size phones,
#          or if the APK ships an ABI other than arm64-v8a (D5).
#
# Checks, per lib/<abi>/*.so in the APK:
#   1. ELF: every PT_LOAD segment's p_align is a multiple of 16384 (what NDK r28+ produces).
#   2. ZIP: a stored (uncompressed) .so starts at a 16384-aligned file offset, so the
#      platform can mmap it straight from the APK (AGP default for minSdk >= 23).
# Standard library only, so it runs the same in CI and on a Windows laptop.

from __future__ import annotations

import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

PAGE_SIZE = 16 * 1024
ALLOWED_ABIS = frozenset({"arm64-v8a"})
PT_LOAD = 1
ELF_MAGIC = b"\x7fELF"
ELFCLASS64 = 2
ELFDATA2LSB = 1
ZIP_LOCAL_HEADER_SIZE = 30
ZIP_LOCAL_HEADER_NAME_LEN_OFFSET = 26


@dataclass(frozen=True)
class Problem:
    entry: str
    reason: str


def elf_load_alignments(data: bytes) -> list[int]:
    """Returns p_align of every PT_LOAD segment of a 64-bit little-endian ELF file."""
    if data[:4] != ELF_MAGIC:
        raise ValueError("not an ELF file")
    if data[4] != ELFCLASS64 or data[5] != ELFDATA2LSB:
        raise ValueError("not a 64-bit little-endian ELF (arm64 expected)")
    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x36)
    alignments = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        p_type = struct.unpack_from("<I", data, offset)[0]
        if p_type == PT_LOAD:
            alignments.append(struct.unpack_from("<Q", data, offset + 0x30)[0])
    return alignments


def zip_data_offset(apk: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    """File offset of an entry's data: local header + name + extra (extra may differ from central dir)."""
    fp = apk.fp
    assert fp is not None
    fp.seek(info.header_offset + ZIP_LOCAL_HEADER_NAME_LEN_OFFSET)
    name_len, extra_len = struct.unpack("<HH", fp.read(4))
    return info.header_offset + ZIP_LOCAL_HEADER_SIZE + name_len + extra_len


def check_apk(path: Path) -> tuple[list[str], list[Problem]]:
    checked: list[str] = []
    problems: list[Problem] = []
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or not parts[2].endswith(".so"):
                continue
            abi = parts[1]
            if abi not in ALLOWED_ABIS:
                problems.append(Problem(info.filename, f"ABI '{abi}' is not allowed (arm64-v8a only)"))
                continue
            checked.append(info.filename)
            try:
                alignments = elf_load_alignments(apk.read(info))
            except (ValueError, struct.error) as error:
                problems.append(Problem(info.filename, f"unreadable ELF: {error}"))
                continue
            bad = [a for a in alignments if a % PAGE_SIZE != 0]
            if not alignments:
                problems.append(Problem(info.filename, "no PT_LOAD segments"))
            elif bad:
                problems.append(Problem(info.filename, f"LOAD segment alignment {bad} is not a multiple of {PAGE_SIZE}"))
            if info.compress_type == zipfile.ZIP_STORED:
                offset = zip_data_offset(apk, info)
                if offset % PAGE_SIZE != 0:
                    problems.append(Problem(info.filename, f"stored at zip offset {offset}, not {PAGE_SIZE}-aligned"))
    return checked, problems


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(f"usage: {argv[0]} <app.apk> [more.apk ...]", file=sys.stderr)
        return 2
    exit_code = 0
    for arg in argv[1:]:
        apk = Path(arg)
        if not apk.is_file():
            print(f"FAIL {apk}: file not found", file=sys.stderr)
            exit_code = 1
            continue
        checked, problems = check_apk(apk)
        for problem in problems:
            print(f"FAIL {apk.name}: {problem.entry}: {problem.reason}", file=sys.stderr)
        if problems:
            exit_code = 1
        else:
            summary = f"{len(checked)} native libraries" if checked else "no native libraries yet"
            print(f"OK   {apk.name}: {summary}, 16 KB aligned, arm64-v8a only")
            for entry in checked:
                print(f"     {entry}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main(sys.argv))
