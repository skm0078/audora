# File: scripts/tests/test_build_voice_mirror.py
# Purpose: Checks that build_voice_mirror.py picks the right files and writes the same bytes on every run (ADR-022).

from __future__ import annotations

import hashlib
import io
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import build_voice_mirror as mirror  # noqa: E402


def voice(**overrides) -> mirror.Voice:
    values = dict(
        id="piper-en-test",
        family="vits",
        model="en.onnx",
        tokens="tokens.txt",
        voices=None,
        lexicon=None,
        data_dir="espeak-ng-data",
        archive="vits-piper-en-test",
        source_sha256="0" * 64,
        file="",
        sha256="",
        size_bytes=0,
    )
    values.update(overrides)
    return mirror.Voice(**values)


def make_archive(path: Path, files: dict[str, bytes], mtime: int) -> None:
    with tarfile.open(path, "w:bz2") as tar:
        for name, data in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mtime = mtime
            tar.addfile(info, io.BytesIO(data))


FILES = {
    "vits-piper-en-test/en.onnx": b"weights",
    "vits-piper-en-test/en.onnx.json": b"{}",
    "vits-piper-en-test/tokens.txt": b"a 1\n",
    "vits-piper-en-test/MODEL_CARD": b"card",
    "vits-piper-en-test/espeak-ng-data/phontab": b"ph",
    "vits-piper-en-test/espeak-ng-data/en_dict": b"en",
    "vits-piper-en-test/espeak-ng-data/fr_dict": b"fr",
    "vits-piper-en-test/espeak-ng-data/voices/!v/zac": b"zac",
}


class WantedTest(unittest.TestCase):
    def test_keeps_voice_files_and_spoken_dictionaries_only(self):
        dictionaries = {"en_dict", "hi_dict", "bn_dict"}
        v = voice()
        self.assertTrue(mirror.wanted(v, "en.onnx", dictionaries))
        self.assertTrue(mirror.wanted(v, "tokens.txt", dictionaries))
        self.assertTrue(mirror.wanted(v, "MODEL_CARD", dictionaries))
        self.assertTrue(mirror.wanted(v, "espeak-ng-data/en_dict", dictionaries))
        self.assertTrue(mirror.wanted(v, "espeak-ng-data/voices/!v/zac", dictionaries))
        self.assertFalse(mirror.wanted(v, "espeak-ng-data/fr_dict", dictionaries))
        self.assertFalse(mirror.wanted(v, "en.onnx.json", dictionaries))

    def test_kitten_keeps_its_voices_file(self):
        self.assertTrue(mirror.wanted(voice(voices="voices.bin"), "voices.bin", set()))

    def test_kokoro_keeps_its_lexicon_and_drops_the_other_languages(self):
        v = voice(family="kokoro", model="model.int8.onnx", voices="voices.bin", lexicon="lexicon-us-en.txt")
        self.assertTrue(mirror.wanted(v, "lexicon-us-en.txt", set()))
        self.assertTrue(mirror.wanted(v, "voices.bin", set()))
        self.assertFalse(mirror.wanted(v, "lexicon-zh.txt", set()))
        self.assertFalse(mirror.wanted(v, "lexicon-gb-en.txt", set()))
        self.assertFalse(mirror.wanted(v, "date-zh.fst", set()))
        self.assertFalse(mirror.wanted(v, "dict/jieba.dict.utf8", set()))

    def test_supertonic_keeps_its_parts_and_needs_no_tokens(self):
        v = voice(family="supertonic", model="tts.json", voices="voice.bin", data_dir=None)
        for name in ("tts.json", "voice.bin", *mirror.SUPERTONIC_PARTS):
            self.assertTrue(mirror.wanted(v, name, set()), name)
        self.assertNotIn("tokens.txt", v.required_files())


class BuildZipTest(unittest.TestCase):
    def test_same_files_give_the_same_bytes_whatever_their_dates(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            digests = []
            for run, mtime in enumerate((1_600_000_000, 1_700_000_000)):
                archive = root / f"src{run}.tar.bz2"
                make_archive(archive, FILES, mtime)
                built = mirror.build_zip(voice(), archive, root / f"out{run}", ["en", "hi", "bn"])
                digests.append(hashlib.sha256(built.path.read_bytes()).hexdigest())
            self.assertEqual(digests[0], digests[1])

    def test_zip_is_flat_stored_and_trimmed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "src.tar.bz2"
            make_archive(archive, FILES, 1_600_000_000)
            built = mirror.build_zip(voice(), archive, root / "out", ["en"])
            self.assertEqual(built.path.name, f"piper-en-test-{built.sha256[:12]}.zip")
            with zipfile.ZipFile(built.path) as zf:
                names = zf.namelist()
                self.assertEqual(names, sorted(names))
                self.assertIn("en.onnx", names)
                self.assertIn("espeak-ng-data/en_dict", names)
                self.assertNotIn("espeak-ng-data/fr_dict", names)
                self.assertTrue(all(i.compress_type == zipfile.ZIP_STORED for i in zf.infolist()))

    def test_a_model_licence_from_the_repo_goes_in_as_model_license(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "src.tar.bz2"
            make_archive(archive, FILES, 0)
            licence = "speech/licences/bigscience-openrail-m.txt"
            built = mirror.build_zip(voice(model_licence=licence), archive, root / "out", ["en"])
            with zipfile.ZipFile(built.path) as zf:
                self.assertEqual(zf.read("MODEL_LICENSE"), (mirror.REPO_ROOT / licence).read_bytes())

    def test_supertonic_ships_its_model_licence_and_asks_for_its_terms(self):
        root, voices = mirror.load_catalog(mirror.REPO_ROOT / "speech" / "models.json")
        supertonic = next(v for v in voices if v.id == "supertonic-hi")
        self.assertEqual(supertonic.model_licence, "speech/licences/bigscience-openrail-m.txt")
        self.assertIn("Attachment A", (mirror.REPO_ROOT / supertonic.model_licence).read_text(encoding="utf-8"))
        entry = next(e for e in root["voices"] if e["id"] == "supertonic-hi")
        self.assertEqual(entry["useTerms"], "openrail-m")

    def test_missing_model_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "src.tar.bz2"
            make_archive(archive, {k: v for k, v in FILES.items() if not k.endswith("en.onnx")}, 0)
            with self.assertRaises(ValueError):
                mirror.build_zip(voice(), archive, root / "out", ["en"])

    def test_pins_are_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "src.tar.bz2"
            make_archive(archive, FILES, 0)
            built = mirror.build_zip(voice(), archive, root / "out", ["en"])
            self.assertEqual(len(built.problems()), 3)
            pinned = voice(file=built.pinned_name, sha256=built.sha256, size_bytes=built.size_bytes)
            self.assertEqual(mirror.Built(pinned, built.path, built.sha256, built.size_bytes).problems(), [])


class ZipSourceTest(unittest.TestCase):
    def test_a_zip_source_gives_the_same_bytes_as_the_tar_of_the_same_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            tar = root / "src.tar.bz2"
            make_archive(tar, FILES, 1_600_000_000)
            source_zip = root / "src.zip"
            with zipfile.ZipFile(source_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("vits-piper-en-test/", b"")
                for name, data in FILES.items():
                    zf.writestr(name, data)
            from_tar = mirror.build_zip(voice(), tar, root / "a", ["en"])
            from_zip = mirror.build_zip(voice(), source_zip, root / "b", ["en"])
            self.assertEqual(from_tar.sha256, from_zip.sha256)

    def test_source_url_defaults_to_the_sherpa_tarball(self):
        base = "https://example.test/tts-models/"
        self.assertEqual(voice().source(base), base + "vits-piper-en-test.tar.bz2")
        own = "https://example.test/s1-voices/x.zip"
        self.assertEqual(voice(source_url=own).source(base), own)


class TarballSourceTest(unittest.TestCase):
    def test_members_named_with_a_leading_dot_slash_are_found(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "punct.tar.bz2"
            make_archive(
                archive,
                {
                    "./punct-en/model.int8.onnx": b"int8",
                    "./punct-en/model.onnx": b"fp32",
                    "./punct-en/bpe.vocab": b"vocab",
                    "./punct-en/README.md": b"readme",
                },
                mtime=1,
            )
            punct = voice(
                id="punct-en", family="punct-cnn-bilstm", model="model.int8.onnx", tokens="bpe.vocab",
                data_dir=None, archive="punct-en",
            )
            built = mirror.build_zip(punct, archive, Path(tmp) / "out", [])
            with zipfile.ZipFile(built.path) as zf:
                self.assertEqual(zf.namelist(), ["README.md", "bpe.vocab", "model.int8.onnx"])

    def test_a_tarball_source_url_is_accepted(self):
        own = "https://example.test/punctuation-models/x.tar.bz2"
        self.assertEqual(voice(source_url=own).source("https://example.test/tts-models/"), own)


class CatalogTest(unittest.TestCase):
    def test_repo_catalog_parses_and_pins_look_valid(self):
        root, voices = mirror.load_catalog(Path(__file__).resolve().parents[2] / "speech" / "models.json")
        self.assertTrue(root["baseUrl"].startswith("https://"))
        for v in voices:
            self.assertEqual(len(v.sha256), 64, v.id)
            self.assertEqual(v.file, f"{v.id}-{v.sha256[:12]}.zip")
            self.assertGreater(v.size_bytes, 0)

    def test_models_are_loaded_after_the_voices_and_pinned_like_them(self):
        root, voices = mirror.load_catalog(Path(__file__).resolve().parents[2] / "speech" / "models.json")
        models = [v for v in voices if v.id in {entry["id"] for entry in root.get("models", [])}]
        self.assertEqual(
            [v.id for v in models],
            ["punct-en-cnn-bilstm", "kws-en-zipformer-gigaspeech", "acted-hi-chatterbox-core", "acted-hi-chatterbox-lm"],
        )
        # The acted Hindi voice has no token table in its zips ("tokens": null); its tokenizer is an app asset.
        self.assertEqual(models[3].required_files(), ["language_model.onnx", "language_model.onnx_data"])
        self.assertEqual(models[0].required_files(), ["model.int8.onnx", "bpe.vocab"])
        # The keyword spotter's decoder and joiner come in through extraFiles (plan item 38).
        self.assertEqual(
            models[1].required_files(),
            [
                "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                "tokens.txt",
                "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            ],
        )

    def test_every_voice_is_credited_and_recommendations_name_a_voice_of_their_language(self):
        root, _ = mirror.load_catalog(Path(__file__).resolve().parents[2] / "speech" / "models.json")
        langs = {entry["id"]: entry["lang"] for entry in root["voices"]}
        for entry in root["voices"]:
            self.assertTrue(entry["credit"].strip(), entry["id"])
            self.assertTrue(entry["licenceUrl"].startswith("https://"), entry["id"])
            self.assertTrue(entry["sourceUrl"].startswith("https://"), entry["id"])
        for lang, voice_id in root["recommended"].items():
            self.assertEqual(langs.get(voice_id), lang, voice_id)


if __name__ == "__main__":
    unittest.main()
