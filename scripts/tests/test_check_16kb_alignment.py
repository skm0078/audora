# File: scripts/tests/test_check_16kb_alignment.py
# Purpose: Proves the 16 KB checker rejects bad APKs, so a green CI step means something
#          even while the APK has no native libraries.

import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_16kb_alignment as checker  # noqa: E402

ELF_HEADER_SIZE = 64
PHDR_SIZE = 56


def fake_elf(load_alignments: list[int]) -> bytes:
    """Minimal 64-bit LE ELF with one PT_LOAD program header per alignment."""
    header = bytearray(ELF_HEADER_SIZE)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    header[5] = 1  # little endian
    struct.pack_into("<Q", header, 0x20, ELF_HEADER_SIZE)  # e_phoff
    struct.pack_into("<HH", header, 0x36, PHDR_SIZE, len(load_alignments))
    phdrs = bytearray()
    for align in load_alignments:
        phdr = bytearray(PHDR_SIZE)
        struct.pack_into("<I", phdr, 0, 1)  # PT_LOAD
        struct.pack_into("<Q", phdr, 0x30, align)
        phdrs += phdr
    return bytes(header + phdrs)


class CheckApkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def apk(self, entries: dict, aligned: bool = True) -> Path:
        path = Path(self.tmp.name) / "test.apk"
        with zipfile.ZipFile(path, "w") as zf:
            for name, data in entries.items():
                info = zipfile.ZipInfo(name)
                info.compress_type = zipfile.ZIP_STORED
                if aligned:
                    # Pad the local header's extra field so data lands on a 16 KB boundary, like zipalign -P 16.
                    base = zf.fp.tell() + checker.ZIP_LOCAL_HEADER_SIZE + len(name.encode())
                    pad = (-(base + 4)) % checker.PAGE_SIZE
                    info.extra = struct.pack("<HH", 0xD935, pad) + bytes(pad)
                zf.writestr(info, data)
        return path

    def test_apk_without_native_libraries_passes(self):
        checked, problems = checker.check_apk(self.apk({"classes.dex": b"dex"}))
        self.assertEqual([], checked)
        self.assertEqual([], problems)

    def test_aligned_arm64_library_passes(self):
        checked, problems = checker.check_apk(self.apk({"lib/arm64-v8a/libok.so": fake_elf([16384, 65536])}))
        self.assertEqual(["lib/arm64-v8a/libok.so"], checked)
        self.assertEqual([], problems)

    def test_4kb_load_alignment_fails(self):
        _, problems = checker.check_apk(self.apk({"lib/arm64-v8a/libold.so": fake_elf([16384, 4096])}))
        self.assertEqual(1, len(problems))
        self.assertIn("4096", problems[0].reason)

    def test_unaligned_zip_offset_fails(self):
        _, problems = checker.check_apk(self.apk({"lib/arm64-v8a/libok.so": fake_elf([16384])}, aligned=False))
        self.assertEqual(1, len(problems))
        self.assertIn("zip offset", problems[0].reason)

    def test_other_abi_fails(self):
        _, problems = checker.check_apk(self.apk({"lib/x86_64/libok.so": fake_elf([16384])}))
        self.assertEqual(1, len(problems))
        self.assertIn("x86_64", problems[0].reason)

    def test_non_elf_library_fails(self):
        _, problems = checker.check_apk(self.apk({"lib/arm64-v8a/libjunk.so": b"not an elf"}))
        self.assertIn("unreadable ELF", problems[0].reason)

    def test_main_returns_nonzero_on_failure(self):
        path = self.apk({"lib/arm64-v8a/libold.so": fake_elf([4096])})
        self.assertEqual(1, checker.main(["checker", str(path)]))


if __name__ == "__main__":
    unittest.main()
