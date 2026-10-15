#!/usr/bin/env python3
# File: research/spikes/S1-voice-benchmark/scripts/test_catalog.py
# Purpose: Consistency checks on spikes/voices/src/main/assets/s1-voices.json, the catalog shared by the phone app,
#          the desktop pre-run and the s1-voices workflow. Mirrors CatalogParser's validation (which needs Android's
#          org.json and so has no JVM unit test) so a bad catalog fails in CI before it reaches a phone.
#
# Usage: python3 -m unittest discover -s research/spikes/S1-voice-benchmark/scripts -p "test_*.py" -v

from __future__ import annotations

import json
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
CATALOG = REPO / "spikes/voices/src/main/assets/s1-voices.json"
MODEL_BASE_URL = "https://github.com/skm0078/audora/releases/download/s1-voices/"
FAMILIES = {"vits", "kitten", "kokoro", "supertonic"}
TEXT_KINDS = {"first", "prose", "formal", "hard"}
# Built by convert_bn_voices.py in CI; every other archive comes prebuilt from sherpa-onnx's tts-models release.
CONVERTED_ARCHIVES = {"vits-piper-bn_BD-google-medium", "vits-mms-ben"}


class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        cls.voices = cls.catalog["voices"]
        cls.texts = cls.catalog["texts"]

    def test_header(self) -> None:
        self.assertEqual(self.catalog["version"], 1)
        self.assertEqual(self.catalog["modelBaseUrl"], MODEL_BASE_URL)

    def test_ids_are_unique(self) -> None:
        for items in (self.voices, self.texts):
            ids = [item["id"] for item in items]
            self.assertEqual(len(ids), len(set(ids)), f"duplicate ids in {ids}")

    def test_every_language_has_every_text_kind(self) -> None:
        for lang in {v["lang"] for v in self.voices}:
            kinds = {t["kind"] for t in self.texts if t["lang"] == lang}
            self.assertEqual(kinds, TEXT_KINDS, f"text kinds for {lang}")

    def test_voice_fields(self) -> None:
        for voice in self.voices:
            with self.subTest(voice=voice["id"]):
                self.assertIn(voice["family"], FAMILIES)
                self.assertTrue(voice["label"].strip())
                self.assertTrue(voice["license"].strip(), "every voice records its licence")
                self.assertTrue(voice["archive"].strip())
                if voice["family"] == "supertonic":
                    self.assertTrue(voice.get("extraLang"), "Supertonic needs extraLang")
                else:
                    self.assertTrue(voice.get("model"), "non-Supertonic voices name their model file")
                if voice["family"] == "kokoro":
                    self.assertTrue(voice.get("kokoroLang"), "Kokoro needs kokoroLang")

    def test_converted_archives_are_in_the_catalog(self) -> None:
        archives = {v["archive"] for v in self.voices}
        self.assertTrue(CONVERTED_ARCHIVES <= archives, "the workflow converts archives the catalog no longer uses")

    def test_no_research_only_voices(self) -> None:
        # The mirror redistributes every catalog archive. Voices trained on (or fine-tuned from) the Lessac/Blizzard
        # data are research-only with no redistribution, so they stay out (ADR-012); their desktop numbers are in
        # REPORT.md as a reference only.
        for voice in self.voices:
            with self.subTest(voice=voice["id"]):
                self.assertNotIn("lessac", voice["archive"])
                self.assertNotIn("lessac", voice["license"].lower())
                self.assertNotIn("research-only", voice["license"].lower())


if __name__ == "__main__":
    unittest.main()
