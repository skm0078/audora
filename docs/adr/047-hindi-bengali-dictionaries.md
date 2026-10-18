# ADR-047: Hindi and Bengali dictionaries from Wiktionary

**Status:** Accepted (2026-10-17). Plan item 33, second part; follows ADR-045.

## Context
ADR-045 shipped the English dictionary on tap and found no redistributable Hindi or Bengali wordnet: Hindi WordNet
(IIT Bombay, through LDC) forbids redistribution and IndoWordNet's terms are research-only. Wiktionary's Hindi and
Bengali entries are CC BY-SA 4.0 (and GFDL), and Wiktextract publishes them as structured JSONL at kaikki.org. Those
files are not versioned: each language's file is regenerated about weekly under the same URL, and kaikki.org is not
reachable from the build threads' containers (GitHub Actions reaches it).

## Decision
1. **Source:** kaikki.org's per-language files from the English Wiktionary
   (`kaikki.org-dictionary-Hindi.jsonl`, `…-Bengali.jsonl`), so each word has English glosses, parts of speech,
   synonyms and inflected forms. Snapshot pinned by SHA-256 in `speech/models.json` (extracted 2026-10-03 from the
   enwiktionary dump of 2026-09-02): Hindi 165,746,593 bytes, Bengali 39,482,695 bytes.
2. **Build:** `scripts/build_dictionary.py` (standard library only) keeps entries of the language, a sense's own gloss
   (Wiktextract repeats the parent gloss first), up to 5 synonyms, and turns "form of" senses and inflection-table
   forms into redirect lines (romanizations and table metadata left out). Keys are NFKC without zero-width joiners or soft hyphens,
   as the app's `WordKey` keys a tapped word; a word that has senses and is also a form of another word lists both.
   A line stays under 60,000 bytes (the app reads up to 64 KiB) by dropping a word's last senses. Same reproducible
   zip as English (deflate 9, fixed dates and permissions), with the CC BY-SA 4.0 text as `LICENSE`.
3. **Measured sizes:** Hindi 1,792,646 bytes to download, 15,563,758 on the phone (279,064 lines with the forms; kaikki.org
   lists 35,881 Hindi words); Bengali 556,861 to download, 3,341,270 on the phone (58,102 lines).
4. **Measuring and keeping the snapshot:** a spec marked `"measure": true` is fetched unpinned and only printed in the
   voice-mirror log (source and zip pins), so a thread can pin a snapshot it cannot download. Because the upstream
   file is replaced in place, the mirror workflow keeps any dictionary whose pinned zip is already on the "voices"
   release (`--published`: downloaded and checked byte for byte) instead of rebuilding it; the snapshot is needed
   only until the first publish from dev. If kaikki.org moved on before that, the pin fails loudly on dev (which
   publishes) and is re-measured (one more CI round), never silently replaced; on other branches it is a warning and
   that zip is not rebuilt, so a feature branch's CI does not go red over an upstream refresh. A pin covers the zip's
   name, SHA-256, size and the unpacked text's size.
5. **App:** one optional download per language (`Dictionaries.forLanguage`, `DictionaryPin.HINDI`/`BENGALI`, folders
   `noBackupFilesDir/dictionary-hi` and `dictionary-bn`, their own WorkManager names). A tapped word goes to its
   script's dictionary (`DictionaryLanguage`: Devanagari to Hindi, Bengali script to Bengali, anything else English);
   the offer names the language, size and source. Hindi and Bengali take words only as written (their form lines
   cover inflections); English keeps WordNet's base-form rules. Parts of speech gain pronoun, postposition,
   conjunction, interjection, number and "word" for the rest. A dictionary counts as installed only while its text
   has the pinned unpacked size (files synced before the marker), so a power cut or a damaged file reads as not
   installed; deleting one waits for a cancelled download to stop. A word with a zero-width joiner inside stays one
   word; a form line is followed to at most as many headwords as a look-up shows. The builder's multi-word filtering
   is unchanged, since changing it would change the published pins for no reader-visible gain.
6. **Licence:** CC BY-SA 4.0. The dictionary files are adapted material and are shared under the same licence; the
   app's code stays GPL-3.0 (the data is a separate download, not compiled in). Credit in the licences screen, with the
   licence's link and a note that Wiktionary text from before June 2023 was published under CC BY-SA 3.0 and the GNU
   FDL and is used under CC BY-SA (3.0 allows adaptations under 4.0), and the full licence text in each zip.

## Alternatives considered
- **Hindi WordNet / IndoWordNet:** better sense structure, but no redistribution (ADR-045). Rejected.
- **The native Hindi and Bengali Wiktionaries (definitions in Hindi or Bengali):** far smaller than the English
  Wiktionary's entries for these languages, and kaikki.org's extracts of them are marked work in progress. Rejected
  for now; the build accepts another source later.
- **Building from the Wikimedia XML dump with Wiktextract in CI:** dated and checksummed upstream, but a full parse of
  the English Wiktionary takes hours and a large dependency. Rejected.
- **Re-hosting the 166 MB source on a release:** keeps the snapshot forever but publishes a large third-party file
  from a branch. Rejected; keeping the built zip (decision 4) is enough.

## Consequences
- Hindi and Bengali meanings are in English, which suits learners and bilingual readers; a reader who wants Hindi
  definitions in Hindi is not served yet.
- kaikki.org marks the per-language files deprecated. When they go, the build moves to the all-languages file
  (2.8 GB compressed) filtered by language code; the app does not change.
- A Wiktionary entry can be thin or missing for rarer words: Wiktionary covers far fewer Hindi and Bengali words than
  the English wordnet covers English ones.
