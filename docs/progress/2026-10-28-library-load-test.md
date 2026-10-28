# Library load test and performance budget (2026-10-28, ADR 2026-10-28-performance-budget)
- `LibraryLoadBudget` (`:core` config, tests only): the limits for a 1,000 and 5,000-book library, the emulator list
  and the release journey's big-library step.
- `SyntheticLibrary` (`:core`, tests only): the one made-up library the three tests below share, with
  `SyntheticLibraryTest` holding its promises (unique titles, no read book finished).
- `LibraryLoadTest` (JVM): first list, sort and shelf, typed search and file check at 1,000 and 5,000 books, limits
  and growth checked, search narrowing only once typing pauses; numbers on the CI run's summary page.
- `LibraryLoadScreenTest` (emulator): 5,000 covers and 5,000 rows drawn, flung and scrolled to the last book, every
  frame timed with the platform's frame metrics; numbers in logcat (`AudoraLoadTest`).
- Release journey step 40 (`LoadJourney`): 1,000 books added to the installed app, search finds the last, all removed.
- APK size gate: `scripts/perf/check_apk_size.py` with `scripts/perf/apk-budget.json` (dev and release 125 MB), the
  dev APK's change from the published dev build on the summary page.
- **Could affect:** CI only. No app code changed; `:pdf` not changed (PIPELINE_VERSION unchanged).
- **Verified in CI:** the tests above, `scripts/tests/test_check_apk_size.py`, `LibraryLoadBudgetTest`.

**Phone checks** (the emulator cannot judge smoothness):
1. With a big library on the test phone (a folder of a few hundred books is enough), open the library: the books show
   within a second or two, covers fill in as you scroll, and a fast fling does not stutter.
2. Type a title into search: the list follows within a moment of stopping, and typing never lags.
3. Switch to List in the sort sheet and fling again.

**Cost:** nothing in the app; CI about 3 to 4 minutes more on the emulator job.
