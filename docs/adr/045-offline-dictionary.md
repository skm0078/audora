# ADR-045: Offline dictionary on tap

**Status:** Accepted (2026-10-17). Plan item 33.

## Context
Listeners meet words they do not know and want a meaning without leaving the book or going online. Audora is offline
first, adds no library before the user's call 3, downloads data only through its pinned model downloads (ADR-022) and
ships only data it may redistribute under GPL-3.0. The reader has no text selection; the sentence menu (long-press)
is where sentence actions live.

## Decision
1. **Data: Open English Wordnet 2025** (CC BY 4.0, derived from Princeton WordNet under the WordNet License):
   definitions, parts of speech and up to five synonyms per sense, plus the inflected forms the wordnet lists ("mice",
   "went"). Examples and semantic relations are left out to keep the download small.
2. **Built on the mirror, downloaded on request.** `scripts/build_dictionary.py` turns the SHA-pinned LMF release
   into one sorted text file (`headword TAB pos|definition|synonyms ...`, or `form TAB =headword`) in a zip with the
   licences (CC BY 4.0 and Princeton WordNet's notice, which must appear on all copies), reproducibly; the
   voice-mirror workflow checks it against the pin in `speech/models.json`
   (`dictionaries`) and publishes it from `dev` to the "voices" release. The app downloads it through WorkManager with
   the voices' resumable, SHA-checked downloader (`Dictionaries`, `DictionaryFiles`, `DictionaryDownloadJob`), into
   the no-backup folder. 5.4 MB to download, 16.2 MB on the phone.
3. **In-house look-up, no table in memory.** `SortedLines` binary-searches the file (about 34 block reads), and
   `Dictionary` tries the word, then the forms it lists, then WordNet's "morphy" suffix rules (`EnglishBaseForms`:
   "boxes" → "box", "studied" → "study", "running" → "run"). Pure Kotlin in `:core`, unit-tested on the JVM.
4. **Entry point:** *Look up a word* in the sentence menu opens a dialog with the sentence's words as buttons and a
   field to type any word; a tap shows the entries. Before the download, the dialog offers it with its two sizes.
   Words looked up are never logged.
5. **Deflate, not stored.** Unlike the voice zips, the text is deflated (16 MB to 5.4 MB). Deflate output depends on
   the zlib build, so the pin was computed with Python 3.13 and zlib 1.3 on Ubuntu 24.04, as CI runs; a CI image
   change that alters the bytes fails the mirror job loudly and the pin is recomputed.

## Hindi and Bengali
- **Hindi WordNet and IndoWordNet are not usable.** The Hindi WordNet licence (IIT Bombay, distributed by the LDC)
  forbids redistribution and limits use to research; the IndoWordNet licences found are research-only too.
- **Wiktionary-derived dictionaries are usable**: English Wiktionary's Hindi and Bengali entries (for example the
  kaikki.org extracts) are CC BY-SA 4.0, which allows redistribution with credit and share-alike, as Audora already
  does for wordfreq. Not built yet: the extract host is unreachable from the build sandbox, so the build must run in
  CI; size is not measured yet. The look-up and download code is language-neutral; a Hindi or Bengali file needs its
  own build script, pin and base-form rules (or none).

## Alternatives considered
- **SQLite asset or Room table:** a 30–40 MB database or an import step; the sorted file is smaller and needs no
  schema. Rejected.
- **Bundling in the APK:** 16 MB more for every install, used by some listeners only. Rejected for an opt-in download.
- **Online dictionary APIs:** network on every tap and a third party seeing the book's words. Rejected (zero-network
  rule).
- **Princeton WordNet 3.1 alone:** unmaintained since 2011; Open English Wordnet fixes and extends it under a
  compatible licence.

## Consequences
- The dictionary is English only until a Wiktionary build lands; on a Hindi or Bengali sentence the dialog says so.
- The licence screen credits Open English Wordnet and carries Princeton's copyright line; the zip carries both
  licence texts in full.
- The download works once the branch is on `dev` and the mirror workflow has published the zip.
