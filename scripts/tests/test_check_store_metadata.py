# File: scripts/tests/test_check_store_metadata.py
# Purpose: Proves the store metadata check passes the repo's own fastlane folder and fails over-long text, a missing
#          default locale, a graphic that drifted from its docs/store master, and a release without its changelog.

import shutil
import struct
import sys
import tempfile
import unittest
import zlib
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_store_metadata as gate  # noqa: E402

REPO = Path(__file__).resolve().parent.parent.parent


def png(width: int, height: int, seed: int = 0) -> bytes:
    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return gate.PNG_SIGNATURE + chunk(b"IHDR", ihdr) + chunk(b"tEXt", bytes([seed])) + chunk(b"IEND", b"")


class CheckStoreMetadataTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.en = self.root / gate.METADATA_DIR / gate.DEFAULT_LOCALE
        (self.en / "images").mkdir(parents=True)
        (self.root / gate.STORE_MASTERS_DIR).mkdir(parents=True)
        self.write_texts(self.en)
        for graphic in gate.GRAPHICS:
            data = png(graphic.width, graphic.height)
            (self.en / "images" / graphic.file).write_bytes(data)
            (self.root / gate.STORE_MASTERS_DIR / graphic.master).write_bytes(data)
        (self.root / gate.GRADLE_PROPERTIES).write_text("audora.versionName=0.1.0\n", encoding="utf-8")

    @staticmethod
    def write_texts(locale_dir: Path) -> None:
        locale_dir.mkdir(parents=True, exist_ok=True)
        (locale_dir / "title.txt").write_text("ListenLock\n", encoding="utf-8")
        (locale_dir / "short_description.txt").write_text("Reads PDFs aloud.\n", encoding="utf-8")
        (locale_dir / "full_description.txt").write_text("Reads PDFs aloud, offline.\n", encoding="utf-8")

    def run_gate(self, *args) -> int:
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return gate.main(["--root", str(self.root), *args])

    def test_the_repo_metadata_passes(self):
        self.assertEqual([], gate.check(REPO, require_changelog=False))

    def test_a_complete_folder_passes(self):
        self.assertEqual(0, self.run_gate())

    def test_a_title_over_the_play_limit_fails(self):
        (self.en / "title.txt").write_text("x" * 31, encoding="utf-8")
        self.assertEqual(1, self.run_gate())

    def test_another_locale_missing_its_full_description_fails(self):
        hindi = self.root / gate.METADATA_DIR / "hi-IN"
        self.write_texts(hindi)
        (hindi / "full_description.txt").unlink()
        self.assertEqual(1, self.run_gate())

    def test_a_missing_default_locale_fails(self):
        shutil.rmtree(self.en)
        self.assertEqual(1, self.run_gate())

    def test_a_graphic_that_drifted_from_its_master_fails(self):
        (self.en / "images" / "icon.png").write_bytes(png(512, 512, seed=1))
        self.assertEqual(1, self.run_gate())

    def test_a_graphic_of_the_wrong_size_fails(self):
        data = png(500, 500)
        (self.en / "images" / "icon.png").write_bytes(data)
        (self.root / gate.STORE_MASTERS_DIR / "icon.png").write_bytes(data)
        self.assertEqual(1, self.run_gate())

    def test_a_badly_named_or_long_changelog_fails(self):
        changelogs = self.en / "changelogs"
        changelogs.mkdir()
        (changelogs / "v0.1.0.txt").write_text("First release", encoding="utf-8")
        self.assertEqual(1, self.run_gate())
        (changelogs / "v0.1.0.txt").unlink()
        (changelogs / "1000.txt").write_text("x" * (gate.CHANGELOG_MAX_CHARS + 1), encoding="utf-8")
        self.assertEqual(1, self.run_gate())

    def test_a_release_needs_its_changelog(self):
        self.assertEqual(1, self.run_gate("--require-changelog"))
        changelogs = self.en / "changelogs"
        changelogs.mkdir()
        (changelogs / "1000.txt").write_text("First release.", encoding="utf-8")
        self.assertEqual(0, self.run_gate("--require-changelog"))

    def test_version_code_matches_the_gradle_formula(self):
        (self.root / gate.GRADLE_PROPERTIES).write_text("audora.versionName=2.13.7\n", encoding="utf-8")
        self.assertEqual(2_013_007, gate.version_code(self.root))


if __name__ == "__main__":
    unittest.main()
