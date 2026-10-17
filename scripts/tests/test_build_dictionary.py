# File: scripts/tests/test_build_dictionary.py
# Purpose: Checks that build_dictionary.py writes sorted, well-formed dictionary lines from a small wordnet (ADR-045).

from __future__ import annotations

import gzip
import json
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
  <LexicalEntry id="e4"><Lemma writtenForm="see" partOfSpeech="v"/><Form writtenForm="saw"/><Sense id="s5" synset="y4"/></LexicalEntry>
  <LexicalEntry id="e5"><Lemma writtenForm="saw" partOfSpeech="n"/><Sense id="s6" synset="y5"/></LexicalEntry>
  <LexicalEntry id="e6"><Lemma writtenForm="&#xFB01;x" partOfSpeech="v"/><Sense id="s7" synset="y6"/></LexicalEntry>
  <Synset id="y1" partOfSpeech="n"><Definition>a small	rodent | with a tail</Definition></Synset>
  <Synset id="y2" partOfSpeech="n"><Definition>a pointing device</Definition></Synset>
  <Synset id="y3" partOfSpeech="s"><Definition>of the colour of the sky</Definition></Synset>
  <Synset id="y4" partOfSpeech="v"><Definition>perceive by sight</Definition></Synset>
  <Synset id="y5" partOfSpeech="n"><Definition>a cutting tool</Definition></Synset>
  <Synset id="y6" partOfSpeech="v"><Definition>repair</Definition></Synset>
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
        self.assertEqual(
            ["blue", "computer mouse", "fix", "mice", "mouse", "saw", "see"], [line.split("\t")[0] for line in lines]
        )

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

    def test_a_form_that_is_also_a_headword_keeps_its_link_and_keys_are_nfkc(self):
        lines = self.lines()
        self.assertIn("saw\tn|a cutting tool|\t=see", lines)
        self.assertIn("fix\tv|repair|", lines)


KAIKKI = [
    {"word": "कुत्ता", "lang_code": "hi", "pos": "noun",
     "forms": [{"form": "kutta", "tags": ["romanization"]}, {"form": "कुत्ते", "tags": ["oblique", "singular"]},
               {"form": "no-table-tags", "tags": ["table-tags"]}],
     "senses": [{"glosses": ["dog"], "synonyms": [{"word": "श्वान"}]},
                {"glosses": ["dog", "a contemptible person, a cur | rascal"]}]},
    {"word": "कुत्ते", "lang_code": "hi", "pos": "noun",
     "senses": [{"glosses": ["inflection of कुत्ता"], "form_of": [{"word": "कुत्ता"}], "tags": ["form-of"]}]},
    {"word": "\u0915\u093c\u093f\u0924\u093e\u092c", "lang_code": "hi", "pos": "noun", "senses": [{"glosses": ["book"]}]},
    {"word": "और", "lang_code": "hi", "pos": "conj", "senses": [{"glosses": ["and"]}]},
    {"word": "dog", "lang_code": "en", "pos": "noun", "senses": [{"glosses": ["a mammal"]}]},
    {"word": "ব\u200cই", "lang_code": "bn", "pos": "noun", "senses": [{"glosses": ["book"]}]},
]


class KaikkiTest(unittest.TestCase):
    def lines(self, lang: str) -> list[str]:
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "kaikki.jsonl"
            source.write_text("\n".join(json.dumps(e, ensure_ascii=False) for e in KAIKKI) + "\n", encoding="utf-8")
            return dictionary.kaikki_lines(*dictionary.read_kaikki(source, lang))

    def test_only_the_language_is_kept_sorted_with_its_parts_of_speech(self):
        lines = self.lines("hi")
        self.assertEqual(sorted(lines), lines)
        self.assertIn("और\tc|and|", lines)
        self.assertFalse(any(line.startswith("dog") for line in lines))

    def test_senses_take_the_last_gloss_and_synonyms_clean(self):
        dog = next(line for line in self.lines("hi") if line.startswith("कुत्ता\t"))
        self.assertEqual("कुत्ता\tn|dog|श्वान\tn|a contemptible person, a cur / rascal|", dog)

    def test_forms_point_to_headwords_never_romanized(self):
        lines = self.lines("hi")
        self.assertIn("कुत्ते\t=कुत्ता", lines)
        self.assertFalse(any(line.startswith(("kutta", "no-table-tags")) for line in lines))

    def test_keys_are_nfkc_without_joiners(self):
        # क़ (U+0915 U+093C) stays decomposed under NFKC; the app normalizes the same way.
        self.assertIn("\u0915\u093c\u093f\u0924\u093e\u092c\tn|book|", self.lines("hi"))
        self.assertEqual(["বই\tn|book|"], self.lines("bn"))


    def test_a_word_with_too_many_senses_keeps_the_first_within_the_line_limit(self):
        senses = {"क": [f"n|{'x' * 250} {i}|" for i in range(400)]}
        line = dictionary.kaikki_lines(senses, {})[0]
        self.assertLessEqual(len(line.encode("utf-8")), dictionary.MAX_LINE_BYTES)
        self.assertTrue(line.startswith("क\tn|" + "x" * 250 + " 0|"))


if __name__ == "__main__":
    unittest.main()
