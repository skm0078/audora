# The ListenLock Cookbook, inside the app (2026-10-29, ADR 2026-10-29-in-app-cookbook)
- **From ListenLock**, a new part of the library's home (last by default; Make it yours can move it or turn it off),
  lists the documents that come with the app, starting with the **ListenLock Cookbook**. Its card (also Library ⋮,
  and the empty library under Try a sample) opens the site's recipes as a book in the app's language: a first chapter
  on how to use it, then one chapter per recipe with what you need, set-up, features combined, phone cost, steps, why
  it works and what to do if it goes wrong.
- Built from `docs/_recipes/` by `CookbookAssetsTask` into `cookbook/en.md`, `hi.md` and `bn.md` in the APK's assets.
  Untranslated recipes appear in English in the Hindi and Bengali books.
- "What's new" entry: `2026-10-29-in-app-cookbook`. `:pdf` not changed. No new dependency. No network.
- **Could affect:** the library's home and Make it yours (one more part; a home saved by an older version gets it
  last), the library ⋮ menu (one more entry, last), the empty library (one more button), library search (finds the
  new entry), shared-text books (the cookbook is saved the same way).
- **Verified in CI:** `CookbookTextTest` (unit: introduction first, one chapter per recipe, no markup symbols left),
  `HomeLayoutTest` (an older saved home gets From ListenLock last), `LibraryHomeTest` (it shows last and can be
  turned off), `ReleaseJourneyTest` step 49 (every language's book is in the APK with the same recipes and no site
  markup; the home card and the ⋮ entry open it in the reader).

**Real-device checks (the test phone):**
1. Library home, scroll to From ListenLock: one row labelled "From ListenLock" with "ListenLock Cookbook" under it
   shows, in light and dark; at the largest text size the first book stays clear of Add book. Tap the row: the
   reader opens on "How to use this cookbook"; play reads it, then the first recipe. The chapter list shows every recipe.
2. Switch the app to Hindi, open it again: the first chapter is in Hindi and the recipes are in English (not translated
   yet), with English labels.
3. Tap it a second time: the same book opens; the library does not get a second copy.
4. Remove it from the library, then open it again from ⋮: it comes back.
5. On an empty library, the button under Try a sample opens it.
6. You › Make it yours: From ListenLock can be moved and turned off; off, it leaves the home.
