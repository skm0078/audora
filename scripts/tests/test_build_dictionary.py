# File: scripts/tests/test_build_dictionary.py
# Purpose: Checks that build_dictionary.py writes sorted, well-formed dictionary lines from a small wordnet (ADR-045).

from __future__ import annotations

import gzip
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import build_dictionary as dictionary  # noqa: E402

WORDNET = b"""<?xml version="1.0" encoding="UTF-8"?>
<LexicalResource><Lexicon id="t">
  <LexicalEntry id="e1"><Lemma writtenForm="Mouse" partOfSpeech="n"/><Form writtenForm="mice"/>
    <Sense id="s1" synset="y1"/><Sense id="s2" synset="y2"/></LexicalEntry>
  <LexicalEntry id="e2"><Lemma writtenForm="computer mouse" partOfSpeech="n"/><Sense id="s3" synset="y2"/></LexicalEntry>
  <LexicalEntry id="e3"><Lemma writtenForm="blue" partOfSpeech="s"/><Sense id="s4" synset="y3"/></LexicalEntry>
  <Synset id="y1" partOfSpeech="n"><Definition>a small	rodent | with a tail</Definition></Synset>
  <Synset id="y2" partOfSpeech="n"><Definition>a pointing device</Definition></Synset>
  <Synset id="y3" partOfSpeech="s"><Definition>of the colour of the sky</Definition></Synset>
</Lexicon></LexicalResource>
"""


class BuildDictionaryTest(unittest.TestCase):
    def lines(self) -> list[str]:
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "wn.xml.gz"
            source.write_bytes(gzip.compress(WORDNET, mtime=0))
            return dictionary.lines_of(*dictionary.read_wordnet(source))

    def test_lines_are_sorted_by_code_point(self):
        lines = self.lines()
        self.assertEqual(sorted(lines), lines)
        self.assertEqual(["blue", "computer mouse", "mice", "mouse"], [line.split("\t")[0] for line in lines])

    def test_senses_keep_order_synonyms_and_clean_fields(self):
        mouse = next(line for line in self.lines() if line.startswith("mouse\t"))
        self.assertEqual(
            "mouse\tn|a small rodent / with a tail|\tn|a pointing device|computer mouse",
            mouse,
        )

    def test_satellites_read_as_adjectives_and_forms_point_to_headwords(self):
        lines = self.lines()
        self.assertIn("blue\ta|of the colour of the sky|", lines)
        self.assertIn("mice\t=mouse", lines)


if __name__ == "__main__":
    unittest.main()
