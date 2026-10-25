# ADR 2026-10-25-parallel-prs-merge-cleanly: independent PRs merge in any order

**Status:** Accepted (2026-10-25). Replaces the "What's new" numbering in ADR-070 and the "add a line" rules in
`docs/README.md` and `docs/adr/README.md`.

## Context
On 2026-10-25 nearly every open PR conflicted as soon as another one merged, although the features themselves did
not overlap. Of the last 40 merges into `dev`, 33 edited `docs/PROGRESS.md` (always the same top lines), 18 appended
to `values/strings.xml` and its Hindi and Bengali copies, 12 edited `BUILD-SPEC.md` (the ADR table), and 9 appended to
the ADR list in `docs/README.md`. Every listener-facing PR replaced the same `whats_new_items` array and bumped the
same `WHATS_NEW_ID`. ADR numbers were picked by hand, so 073 to 077 were taken twice by branches in flight. On top of
that, branches stacked on other branches broke when the bottom one was squash-merged: its commits come back as
"new" changes that clash with their own squashed copy.

None of these are real conflicts. They are two branches each adding something at the same spot of a shared file.

## Decision
Each change adds its own file, named by date and slug; nothing shared is edited to record it.

1. **What's new** is one file per item: `app/src/main/assets/whatsnew/<yyyy-mm-dd>-<slug>.txt`, with an `en:`, `hi:`
   and `bn:` line. The app shows the entries a listener has not seen (newest first, at most 8) and stores the names
   it showed, so there is no number to bump. A listener who saw the last numbered list (10) has seen the nine entries
   carried over from it (`WhatsNewEntries.LEGACY_ENTRIES`). A new listener's entries are marked seen, as before.
2. **Progress** is one file per change: `docs/progress/<yyyy-mm-dd>-<slug>.md`. `PROGRESS.md` keeps the history up to
   this change and is frozen.
3. **New ADRs** are named `docs/adr/<yyyy-mm-dd>-<slug>.md` and cited by that name. The numbered ADRs keep their
   numbers. The folder is the list: no line in `docs/README.md`, no row in `BUILD-SPEC.md` §11.
4. **New strings** go into a `strings_<feature>.xml` of their own (with its Hindi and Bengali copies). Changing an
   existing string in `strings.xml` is fine.
5. **One branch per feature, off `dev`**; no stacked branches. Before asking for a merge, merge the latest `dev` in.
6. **CI enforces 1 to 4** (`scripts/check_hotspots.py`, first step of "Check, test, assemble"), so a rule slip fails
   in a minute instead of at merge time. A PR that changes the check itself is not checked: the rules are then under
   the maintainer's review in that PR.

## Alternatives considered
- **`merge=union` in `.gitattributes`** for `PROGRESS.md`: GitHub's server-side merge, which squash-merge uses, does not
  apply merge drivers from `.gitattributes`, so the merge button still reports a conflict. Rejected.
- **Generate What's new resources at build time** from fragments: keeps Android's resource localisation, but adds a
  Gradle task to `build-logic/` and a generated registry. Assets plus a parse test give the same safety with less.
- **Number assigned at merge time** (ADRs, What's new): needs a bot or a merge queue. Dates and slugs need nothing.
- **A merge queue / "require up to date"**: serialises merges and costs a CI round per PR; it hides conflicts instead
  of removing them.

## Consequences
- Two PRs that each add a What's new item, a progress entry, an ADR and strings merge in either order.
- Two entries from the same day show in name order, not merge order; harmless.
- Still shared, and still a real conflict when two PRs change them: code such as `MainActivity.kt`, and the
  generated score files `QUALITY.md` / `LISTENING.md` when two PRs change `:pdf` or speech output (re-run the score
  task after merging `dev`). Identical one-line bumps of `PIPELINE_VERSION` or `STUDIO_AUDIO_VERSION` merge cleanly.
- Branches made before this change that edit `PROGRESS.md` or `whats_new_items` fail the check after merging `dev`;
  move the entry into its own file.
