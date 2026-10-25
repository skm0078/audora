# Parallel PRs merge in any order (2026-10-25, ADR 2026-10-25-parallel-prs-merge-cleanly)
- Every change now adds its own small file instead of editing a shared one: a What's new item is
  `app/src/main/assets/whatsnew/<date>-<slug>.txt` (en/hi/bn lines), a progress entry is `docs/progress/<date>-<slug>.md`,
  a new ADR is `docs/adr/<date>-<slug>.md`, new strings go into `strings_<feature>.xml`. `WHATS_NEW_ID` and the
  `whats_new_items` arrays are gone; `PROGRESS.md` is frozen.
- What's new shows only the entries a listener has not seen yet, newest first, at most 8, and remembers them by name.
  Someone updating from the build with list 9 sees nothing again; a new listener sees nothing at all.
- CI's first step (`scripts/check_hotspots.py`) fails a branch that edits a frozen file, adds a numbered ADR or an
  oddly named entry, adds a string to `strings.xml`, or brings back `WHATS_NEW_ID`.
- "What's new" entry: none (nothing a listener hears). No `:pdf` change. No new dependency.
- **Could affect:** the What's new dialog after an update; first-run marking it seen.
- **Verified in CI:** `WhatsNewEntriesTest` (every shipped file parses in en/hi/bn; a new build shows only its own
  entries, once; two branches' entries give the same list in either order; an update from list 9 shows nothing old),
  `WhatsNewStartTest`, `test_check_hotspots.py`, `ReleaseJourneyTest`.

**Real-device checks (the test phone):** install this build over the current dev build (which showed list 9): no
What's new dialog. Then install a later build that adds one entry: the dialog shows that one item once, in the app's
language, and not again after "Got it".
