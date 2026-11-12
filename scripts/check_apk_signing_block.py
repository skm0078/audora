#!/usr/bin/env python3
# File: scripts/check_apk_signing_block.py
# Purpose: Fails if a signed APK carries Google's encrypted dependency-info block in its APK Signing Block. F-Droid
#          and IzzyOnDroid reject APKs with it, and app/build.gradle.kts turns it off (dependenciesInfo.includeInApk);
#          this check keeps it off (ADR 2026-10-31-fdroid-ready-build).
#
# Usage: python3 scripts/check_apk_signing_block.py APK [APK ...]
# An unsigned APK has no signing block, so there is nothing to check and it passes with a note.
# Standard library only, so it runs the same in CI and on a Windows laptop.

from __future__ import annotations

import struct
import sys
from pathlib import Path

# The APK Signing Block sits just before the ZIP central directory and ends with this magic
# (source.android.com/docs/security/features/apksigning/v2#apk-signing-block).
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
# ID-value pair IDs found in real signing blocks. Only DEPENDENCY_INFO fails the check; the rest name what was found.
DEPENDENCY_INFO_BLOCK_ID = 0x504B4453
KNOWN_BLOCK_IDS = {
    0x7109871A: "v2 signature",
    0xF05368C0: "v3 signature",
    0x1B93AD61: "v3.1 signature",
    0x42726577: "verity padding",
    0x6DFF800D: "source stamp",
    DEPENDENCY_INFO_BLOCK_ID: "dependency info (Google-only, encrypted)",
}
EOCD_SIGNATURE = b"PK\x05\x06"
EOCD_MIN_SIZE = 22
EOCD_CD_OFFSET_FIELD = 16
MAX_ZIP_COMMENT = 0xFFFF
# Footer = size of block (uint64) + magic (16 bytes).
FOOTER_SIZE = 8 + len(SIGNING_BLOCK_MAGIC)


class ApkFormatError(Exception):
    """The file is not a ZIP this check can read."""


def central_directory_offset(data: bytes) -> int:
    """Finds the End of Central Directory record (searching back past any comment) and returns the CD offset."""
    search_from = max(0, len(data) - EOCD_MIN_SIZE - MAX_ZIP_COMMENT)
    eocd = data.rfind(EOCD_SIGNATURE, search_from)
    if eocd < 0 or eocd + EOCD_MIN_SIZE > len(data):
        raise ApkFormatError("no ZIP end-of-central-directory record")
    (offset,) = struct.unpack_from("<I", data, eocd + EOCD_CD_OFFSET_FIELD)
    if offset > eocd:
        raise ApkFormatError(f"central directory offset {offset} is past its end record at {eocd}")
    return offset


def signing_block_ids(data: bytes) -> list[int] | None:
    """The IDs of every ID-value pair in the APK Signing Block, or None when the APK has no signing block."""
    cd_offset = central_directory_offset(data)
    if cd_offset < FOOTER_SIZE or data[cd_offset - len(SIGNING_BLOCK_MAGIC) : cd_offset] != SIGNING_BLOCK_MAGIC:
        return None
    (block_size,) = struct.unpack_from("<Q", data, cd_offset - FOOTER_SIZE)
    block_start = cd_offset - block_size - 8
    if block_start < 0:
        raise ApkFormatError(f"signing block size {block_size} runs past the start of the file")
    (leading_size,) = struct.unpack_from("<Q", data, block_start)
    if leading_size != block_size:
        raise ApkFormatError(f"signing block sizes disagree ({leading_size} vs {block_size})")
    ids = []
    pos = block_start + 8
    pairs_end = cd_offset - FOOTER_SIZE
    while pos < pairs_end:
        if pos + 12 > pairs_end:
            raise ApkFormatError("truncated ID-value pair in the signing block")
        (pair_len,) = struct.unpack_from("<Q", data, pos)
        (pair_id,) = struct.unpack_from("<I", data, pos + 8)
        if pair_len < 4 or pos + 8 + pair_len > pairs_end:
            raise ApkFormatError(f"ID-value pair 0x{pair_id:08x} has a bad length {pair_len}")
        ids.append(pair_id)
        pos += 8 + pair_len
    return ids


def check(apk: Path) -> list[str]:
    """Problems found in one APK; empty when it passes."""
    ids = signing_block_ids(apk.read_bytes())
    if ids is None:
        print(f"{apk.name}: unsigned (no APK Signing Block), nothing to check")
        return []
    names = ", ".join(KNOWN_BLOCK_IDS.get(i, f"0x{i:08x}") for i in ids)
    print(f"{apk.name}: signing block holds {names or 'nothing'}")
    if DEPENDENCY_INFO_BLOCK_ID in ids:
        return [
            f"{apk.name}: carries Google's dependency-info block. Keep android.dependenciesInfo.includeInApk = false "
            "in app/build.gradle.kts; F-Droid and IzzyOnDroid reject APKs with it."
        ]
    return []


def main(argv: list[str]) -> int:
    if not argv:
        print("usage: check_apk_signing_block.py APK [APK ...]", file=sys.stderr)
        return 2
    problems: list[str] = []
    for arg in argv:
        try:
            problems += check(Path(arg))
        except (OSError, ApkFormatError) as error:
            problems.append(f"{arg}: {error}")
    for problem in problems:
        print(f"::error::{problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
