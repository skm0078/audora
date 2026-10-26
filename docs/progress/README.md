# Progress entries

One file per merged change, named `<yyyy-mm-dd>-<slug>.md` (the date the branch was made ready, lowercase words).
Never edit another change's file. History up to 2026-10-25 is in [../PROGRESS.md](../PROGRESS.md), which is frozen.
Why: [ADR 2026-10-25-parallel-prs-merge-cleanly](../adr/2026-10-25-parallel-prs-merge-cleanly.md).

Each entry has the same shape as the old ones:

```markdown
# <What changed, in the listener's words> (<yyyy-mm-dd>, ADR <name or number>)
- What changed and why.
- "What's new" entry: `app/src/main/assets/whatsnew/<name>.txt` (or "none"). `:pdf` changed or not. New dependency or not.
- **Could affect:** features that share code with this change.
- **Verified in CI:** the tests that prove it.

**Real-device checks (the test phone):** what to try by hand.
```
