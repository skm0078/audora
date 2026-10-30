# The ListenLock Cookbook is a book inside the app, built from the site's recipes (2026-10-29)

**Status:** accepted

## Context

The site's recipes (ADR 2026-10-29-site-recipes-and-blog) show how to combine features for real situations. The
maintainer wants them in the app too, as something people can listen to with ListenLock itself. The app is offline
first and sends nothing about the listener anywhere.

## Decision

- `CookbookAssetsTask` (build-logic) turns `docs/_recipes/*.md` into `cookbook/<lang>.md` in the APK's assets, one
  Markdown book for English, Hindi and Bengali. A translation in `docs/_recipes/hi/` or `bn/` replaces the English
  recipe of the same file name; a recipe not translated yet stays in English, so no book misses a recipe. A recipe
  missing a front-matter field fails the build with its file name.
- **From ListenLock** is a part of the library's home ("Make it yours" can move it or turn it off), last by default. It
  holds one slim full-width row per document the app provides (`AppDocument`), each labelled "From ListenLock" above its
  title; the cookbook is the first. The row is kept to two short lines: at 200% text a taller part (heading, explanatory
  line and card) pushed the first book under the Add book button, which the UI tour's TalkBack audit rejects. A card
  opens its document like the sample book: saved as a text book and read by the Markdown reader, where each recipe is a
  chapter. The cookbook's first chapter, from strings in the app's language, says how to use it. The side panel's Your
  library rows (and the empty library) open it too, and library search finds it.
- A document is added to the library when the listener opens it, not by itself at first launch, so it never appears
  uninvited in folder sync, backups or listening stats. Opening it again finds the same book (same content).

## Other people's books in From ListenLock (not in this change)

The section is built so a few promoted books by other authors could join it later. None goes in without the
maintainer's explicit OK for that book, and only after:

- **Rights:** public domain in India and worldwide, an open licence that allows redistribution in an app (for
  example CC BY, with the credit shown on the card and in the book), or the author's or publisher's written permission
  to bundle or offer it, kept on file. No book whose rights are unclear.
- **Play policy:** a promoted book is content, not an advert: no payment from the author for placement unless the
  listing and the app declare it as paid promotion, no links that take the listener out to buy, and the store
  listing's description stays accurate.
- **Labelling:** each card says who wrote it and, when it is promoted, says so ("Featured book"), so it is never
  mistaken for the listener's own or for the app's guides.
- **Size and offline:** a promoted book is a download on tap from the project's own site (the later Get new recipes
  path), not inside the APK, so the APK stays small.

## Later (not in this change)

**Get new recipes:** the site publishes the cookbook as one more curated OPDS catalogue and the book's menu downloads
the latest edition on a tap, with the privacy page updated in the same change. Until then new recipes arrive with app
updates.

## Consequences

- The site and the app always carry the same recipes for the same release; there is no second copy to keep in step.
- APK cost: text only, about 10 KB per language for six recipes, growing about 1.5 KB per recipe.
- No network use, no new dependency, no change to `:pdf`.
