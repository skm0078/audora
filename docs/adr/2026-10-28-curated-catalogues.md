# ADR 2026-10-28-curated-catalogues: a curated list of free book catalogues, checked every week, and no typed addresses

**Status:** Accepted (2026-10-28). Supersedes the "the listener can add any https OPDS address" part of
[ADR 2026-10-26-web-books](2026-10-26-web-books.md) decision 4. The rest of that ADR stands (only on a tap, https
only, OPDS 1 Atom, free downloads only).

## Context
The maintainer asked on 2026-10-27 for curated catalogues instead of relying on listeners typing OPDS links: no typed
link worked on the test phone, Open Library included. The live check below, run on CI against each address, found
why:

| What was typed | What came back | Why ListenLock could not read it |
|---|---|---|
| Open Library `/opds` | 308 to `/opds/`, which is OPDS 2 (`application/opds+json`), mostly lending | HttpURLConnection does not follow 307/308; OPDS 2 JSON is not parsed |
| Standard Ebooks | 401 | Feeds need a paid Patrons Circle login |
| Wikisource export (`ws-export.wmcloud.org`) | an HTML "Making sure you're not a bot!" page | A bot wall in front of the feed |
| Feedbooks public domain | 404 (other addresses 403/404, empty index) | Gone |
| archive.org bookserver, DPLA Open Bookshelf, Minitex, Gallica, Global Grey | timeout, unknown host, 403, 404 | Dead or closed |
| Project Gutenberg search | "not an address" | Its OpenSearch description names an `http://` template; the fetcher is https only |

A typed address also gave the listener nothing to go on when it failed, and a catalogue that silently moved stayed in
their list. Curating moves that work to the project: each source is checked once, its licence basis written down, and
a weekly check catches it moving.

## Decision
1. **The list is code.** `CuratedCatalogue` (playback/web) names each source: its own name, its address and its main
   book languages. The app shows a line of its own on what each offers, in en, hi and bn
   (`strings_catalogues_curated.xml`). The field to add an address is removed. Catalogues added by earlier versions
   stay listed after the curated ones, with Remove, until the listener removes them; none is lost on update.
2. **Sources, and why each is allowed:**

   | Source | Address | Licence basis |
   |---|---|---|
   | Project Gutenberg | `https://www.gutenberg.org/ebooks.opds/` | Public-domain (US) books; its OPDS feed is offered for reading apps, no account |
   | Unglue.it | `https://unglue.it/api/opds/` | Books released by their rights holders under Creative Commons licences; free download, no account |

   Rejected: Standard Ebooks (login), Wikisource export (bot wall; would be a good source if it opens to apps),
   Feedbooks public domain (gone), Open Library (OPDS 2, and mostly borrowing rather than free downloads), the dead
   feeds in the table above. ebooksgratuits.com (French public domain) passed every check, search included, but its
   terms may limit use to personal, non-commercial reading; it is not added without the maintainer's OK. No Hindi or
   Bengali OPDS catalogue was found (ws-export has none for hi or bn; Gutenberg's language filter is not offered in
   its OPDS search).
3. **http links inside a feed are tried as https** (`OpdsParser`), as `WebAddress` already does for typed links. The
   fetcher still never sends plain http. This fixes Project Gutenberg search, broken in the shipped app.
4. **Weekly live check on CI** (`.github/workflows/catalogue-check.yml`, Mondays 04:17 UTC, on any change to the
   list or the catalogue reader, and by hand). `CuratedCataloguesLiveTest`, skipped in normal test runs, opens each
   source with the app's own `WebCatalogues`, `WebFetcher` and `OpdsParser`: first page, then breadth first at most
   3 sections deep and 12 pages to a book ListenLock reads, that book's download started with a range request
   (first KB only), and search for "love". It writes a table to the run summary and fails naming the broken source.
   "Run workflow" takes extra addresses, which are reported and never fail the run: that is how a new source is tried.
   A source that fails is fixed or removed here, with the reason added to this ADR.

## Alternatives considered
- **Keep the typed field and explain failures better.** Most failures (OPDS 2, auth, bot walls, dead feeds) are not
  fixable by a better message; the listener still ends with nothing.
- **Read OPDS 2 and follow 307/308.** Would open Open Library's feed, but its books are mostly borrowed (needs an
  account and DRM), so it would add a parser (JSON, a second model of feeds, its own tests) for few readable books.
  Cost: a second parser of about the size of `OpdsParser` plus a manual redirect loop in `WebFetcher`. Left for the
  maintainer to ask for.
- **Check on the phone in the background.** ADR 2026-10-26-web-books decision 1 forbids background network.

## Consequences
- The list is short (two sources) and mostly English. Adding a source is one enum entry, one string in three
  languages, and a passing check.
- The weekly run needs the sites to answer GitHub's runners; a site blocking CI shows as a failure to look at, not a
  silent pass.
- No new dependency; no `:pdf` change. Privacy page and README now say the catalogues are the ones listed in the app.
