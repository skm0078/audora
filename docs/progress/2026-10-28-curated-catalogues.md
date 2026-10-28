# Curated book catalogues, checked every week (2026-10-28, ADR 2026-10-28-curated-catalogues)
- **Free book catalogues** (Library ⋮) now lists a curated set of free, no-account catalogues of public-domain and
  openly licensed books, each with a line on what it offers in English, Hindi or Bengali. The field to type an OPDS
  address is gone: typed links mostly failed on the test phone. Catalogues added by earlier versions stay listed, with
  Remove, until the listener removes them.
- **Weekly CI check** (`.github/workflows/catalogue-check.yml`, Mondays and on any change to the list or the catalogue
  reader): opens each curated catalogue with the app's own fetcher and parser, walks a few sections to a book ListenLock
  reads, starts that book's download (first bytes only), and searches. A broken catalogue fails the run by name; the
  run's summary has a table per catalogue. "Run workflow" can try other addresses before one is curated.
- Removed: `WebCatalogues.add`, `OpdsCatalogues.add`, `CatalogueAdd`, `WebBooksConfig.maxCatalogues` and their
  strings. No `:pdf` change, PIPELINE_VERSION unchanged. Privacy page and README say "one of the catalogues listed in
  the app"; what is contacted is unchanged in kind (the catalogue's own site, only on a tap).
- "What's new" entry: `2026-10-28-curated-catalogues`.
- **Could affect:** the Free book catalogues screen, the library ⋮ menu's hint, search results for "catalogue".
- **Verified in CI:** `CuratedCataloguesTest`, `WebBooksTest` (unit), `CuratedCataloguesLiveTest` (catalogue-check
  workflow only), `WebBooksScreenTest` (device: every curated catalogue with its description, no Remove, no address
  field), `ReleaseJourneyTest` step 41.

**Real-device checks (the test phone, with internet):**
1. Library ⋮ > Free book catalogues: every catalogue shows its name, a line on what it offers and its site; there is no
   field to type an address. Switch the app to Hindi and Bengali: the lines are translated, the names are not.
2. Open each catalogue, open a section, Download a book: it is added and opens; play reads its first page.
3. Project Gutenberg > search "Gitanjali": results show; Download adds the book.
4. If you had added a catalogue yourself before this version, it is still listed under the curated ones, with Remove.
5. Aeroplane mode, open a catalogue: "Could not reach the site" with Try again; nothing added.
