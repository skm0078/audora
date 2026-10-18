# File: scripts/tests/test_build_asr_mirror.py
# Purpose: Checks that build_asr_mirror.py renames the wanted members, adds the licences and builds the same bytes.

from __future__ import annotations

import hashlib
import io
import json
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import build_asr_mirror as asr  # noqa: E402

TOP = "model-2026"


def tar_bytes(files: dict[str, bytes]) -> bytes:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:bz2") as tar:
        for name, data in files.items():
            info = tarfile.TarInfo(f"{TOP}/{name}")
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return buffer.getvalue()


class BuildAsrMirrorTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        self.cache = self.root / "cache"
        self.cache.mkdir()
        archive = tar_bytes({"enc.int8.onnx": b"E", "tokens.txt": b"T", "test_wavs/0.wav": b"W"})
        single = b"VAD"
        self.archive_sha = self.cached(archive)
        self.single_sha = self.cached(single)

    def tearDown(self):
        self.folder.cleanup()

    def cached(self, data: bytes) -> str:
        """Puts [data] in the download cache under its SHA-256, so nothing is fetched."""
        sha = hashlib.sha256(data).hexdigest()
        (self.cache / sha).write_bytes(data)
        return sha

    def spec(self, files: dict[str, str]) -> dict:
        return {
            "id": "asr-test",
            "sources": [
                {"url": f"https://example.org/{TOP}.tar.bz2", "sha256": self.archive_sha, "files": files},
                {"url": "https://example.org/vad.onnx", "sha256": self.single_sha, "as": "vad.onnx"},
            ],
            "licenceFiles": ["speech/licences/apache-2.0.txt"],
        }

    def test_wanted_members_are_renamed_and_the_licence_added(self):
        spec = self.spec({f"{TOP}/enc.int8.onnx": "encoder.onnx", f"{TOP}/tokens.txt": "tokens.txt"})
        built, _, size, unpacked = asr.build(spec, self.cache, self.root / "out")

        with zipfile.ZipFile(built) as archive:
            self.assertEqual(["LICENSE", "encoder.onnx", "tokens.txt", "vad.onnx"], archive.namelist())
            self.assertEqual(b"E", archive.read("encoder.onnx"))
            self.assertIn(b"Apache License", archive.read("LICENSE"))
            self.assertTrue(all(info.compress_type == zipfile.ZIP_STORED for info in archive.infolist()))
        self.assertEqual(built.stat().st_size, size)
        licence = (asr.repo_root() / "speech/licences/apache-2.0.txt").read_bytes().rstrip() + b"\n"
        self.assertEqual(len(b"E" + b"T" + b"VAD" + licence), unpacked)

    def test_the_same_bytes_come_out_every_run(self):
        spec = self.spec({f"{TOP}/tokens.txt": "tokens.txt"})
        first = asr.build(spec, self.cache, self.root / "a")
        second = asr.build(spec, self.cache, self.root / "b")
        self.assertEqual(first[1], second[1])
        self.assertEqual(first[0].name, second[0].name)

    def test_a_missing_member_fails(self):
        spec = self.spec({f"{TOP}/joiner.onnx": "joiner.onnx"})
        with self.assertRaises(ValueError):
            asr.build(spec, self.cache, self.root / "out")

    def test_a_licence_check_must_still_hold(self):
        spec = {"licenceCheck": {"url": "https://example.org/README.md", "contains": "license: apache-2.0"}}
        self.assertEqual([], asr.licence_problems(spec, read=lambda url: "---\nLicense: Apache-2.0\n---"))
        self.assertEqual(1, len(asr.licence_problems(spec, read=lambda url: "license: cc-by-nc-4.0")))

        def unreachable(url):
            raise OSError("blocked")

        self.assertEqual(1, len(asr.licence_problems(spec, read=unreachable)))
        self.assertEqual([], asr.licence_problems({}))

    def test_the_catalog_pins_are_well_formed(self):
        specs = json.loads((asr.repo_root() / "speech/models.json").read_text(encoding="utf-8"))["asr"]
        self.assertEqual({"en", "bn", "hi"}, {spec["lang"] for spec in specs})
        for spec in specs:
            self.assertEqual(f"{spec['id']}-{spec['sha256'][:asr.SHA_PREFIX_CHARS]}.zip", spec["file"])
            self.assertTrue(all((asr.repo_root() / name).is_file() for name in spec["licenceFiles"]))


if __name__ == "__main__":
    unittest.main()
