# File: scripts/tests/test_check_apk_signing_block.py
# Purpose: Proves the signing-block check fails an APK that carries Google's dependency-info block, passes a signed
#          APK without it and an unsigned one, and reports a damaged signing block instead of passing it.

import io
import struct
import sys
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_apk_signing_block as gate  # noqa: E402

V2_SIGNATURE_ID = 0x7109871A


def plain_zip() -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"manifest")
        archive.writestr("classes.dex", b"dex")
    return buffer.getvalue()


def signing_block(pair_ids: list[int]) -> bytes:
    pairs = b"".join(struct.pack("<QI", 4 + 4, pair_id) + b"\0\0\0\0" for pair_id in pair_ids)
    size = len(pairs) + 8 + len(gate.SIGNING_BLOCK_MAGIC)
    return struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + gate.SIGNING_BLOCK_MAGIC


def with_signing_block(zip_bytes: bytes, block: bytes) -> bytes:
    """Inserts a signing block before the central directory and moves the EOCD's CD offset past it (as apksigner)."""
    cd_offset = gate.central_directory_offset(zip_bytes)
    eocd = zip_bytes.rfind(gate.EOCD_SIGNATURE)
    patched = bytearray(zip_bytes[:cd_offset] + block + zip_bytes[cd_offset:])
    struct.pack_into("<I", patched, eocd + len(block) + gate.EOCD_CD_OFFSET_FIELD, cd_offset + len(block))
    return bytes(patched)


class CheckApkSigningBlockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def apk(self, data: bytes) -> Path:
        path = Path(self.tmp.name) / "app.apk"
        path.write_bytes(data)
        return path

    def run_gate(self, apk: Path) -> int:
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return gate.main([str(apk)])

    def test_a_signed_apk_without_the_dependency_block_passes(self):
        apk = self.apk(with_signing_block(plain_zip(), signing_block([V2_SIGNATURE_ID])))
        self.assertEqual(0, self.run_gate(apk))

    def test_an_apk_with_the_dependency_block_fails(self):
        block = signing_block([V2_SIGNATURE_ID, gate.DEPENDENCY_INFO_BLOCK_ID])
        self.assertEqual(1, self.run_gate(self.apk(with_signing_block(plain_zip(), block))))

    def test_the_block_ids_are_read_in_order(self):
        block = signing_block([V2_SIGNATURE_ID, gate.DEPENDENCY_INFO_BLOCK_ID])
        ids = gate.signing_block_ids(with_signing_block(plain_zip(), block))
        self.assertEqual([V2_SIGNATURE_ID, gate.DEPENDENCY_INFO_BLOCK_ID], ids)

    def test_an_unsigned_apk_passes_with_nothing_to_check(self):
        self.assertIsNone(gate.signing_block_ids(plain_zip()))
        self.assertEqual(0, self.run_gate(self.apk(plain_zip())))

    def test_a_damaged_signing_block_fails_instead_of_passing(self):
        block = bytearray(signing_block([V2_SIGNATURE_ID]))
        struct.pack_into("<Q", block, 0, 999)  # leading size no longer matches the trailing one
        self.assertEqual(1, self.run_gate(self.apk(with_signing_block(plain_zip(), bytes(block)))))

    def test_a_file_that_is_not_a_zip_fails(self):
        self.assertEqual(1, self.run_gate(self.apk(b"not an apk")))

    def test_no_arguments_is_a_usage_error(self):
        with redirect_stderr(StringIO()):
            self.assertEqual(2, gate.main([]))


if __name__ == "__main__":
    unittest.main()
