# ADR 2026-10-29-share-credit-and-link: every share carries the book, ListenLock and its link; Share in sight

**Status:** Accepted (2026-10-29). Asked for by the maintainer: Share was hidden behind a long-press, sent pictures
with no words or link, and shared text with no credit at all.

## Context
Kindle, Goodreads, Spotify and Apple Books shares all carry three things: the work's credit, the app's name, and a
link back. ListenLock's shares carried the credit and a small mark on pictures only; clips and videos had the quote
and title but no link; text had none of it. Every Share lived inside the sentence long-press menu, which few find.

## Decision
1. **One caption for everything, built in `:core` (`ShareCaption`, unit tested):** the lines in quotes when the share
   has no picture of them (text, audio clip, video), then "— Title, Author, page N", then an invitation and the link
   on the last line, where chat apps make it tappable. `ShareWords` (`:playback`) supplies the translated pieces, so
   the app's pictures and the services' clips and videos read the same.
2. **The link lives in one place, `ShareLinkConfig.appLink`, and is the site's "get" page** (`docs/get/`, served
   from `prod`, never `dev`; never tracked; never a link to the book). The page sends an Android phone straight on to
   the Play listing once its `PLAY` is set, and everyone else to the site: most people never go from a site to a
   store, and links shared before the launch follow it. Before then it opens the site's home.
3. **Pictures carry the logo and name only, as Spotify, Kindle and Goodreads cards do** (the maintainer's pick,
   2026-10-29): the long github.io link cluttered the card. `ShareLinkConfig.cardLink` prints a short domain under
   the mark once one is live (one line); videos inherit it, their backdrop being the card.
4. **Shared text is capped at `ShareCardConfig.maxTextWords` (150)**, cut at a sentence end when one is close: longer
   than a card's 60-word quote, still a passage and never a page (the guard of ADR 2026-10-27-share-cards and
   2026-10-28-page-snippet).
5. **Share is in sight:** a Share button in the reader's top bar opens the Share choices for the line being read (else
   the book's card); the long-press menu lists Share first. The choices quote the lines they act on, most-shared
   first: picture, text, video, audio, part of the page, "Choose more lines…", "Share this book".
6. **"Choose more lines…"** reuses the passage picker of "Highlight from here" with its own bar and limit; it opens
   the Share choices for the lines and highlights nothing.
7. **A quote or page-part card opens in the reader's colours** (sepia, night, or paper/night by the app's theme)
   instead of a fixed default; a background the listener picks always wins. A book's card keeps its default.

## Alternatives
- **Free word-level selection (Moon+):** conflicts with tap-to-read, long-press and page swipes (as in the snippet
  ADR); lines snap to sentences, which also matches the audio clip.
- **The long link printed on every picture, or a QR code:** both reach Stories and Status, which drop the caption,
  but clutter the card; the short domain will do that cleanly.
- **A share button in the player row:** that row is being reworked (voice and speed); the findability work's player
  Share, if kept, calls the same `openShare`.

## Consequences
- Perf: nothing at cold start and nothing while reading. The card screen reads the reading look once (one DataStore
  read). No new dependency; strings and one small dialog only.
- The reader's top bar gains one icon, so a long title shows a little less on a narrow phone.
- When the domain or Play listing is live, change `ShareLinkConfig.APP_SITE` only.
