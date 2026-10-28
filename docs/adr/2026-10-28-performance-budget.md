# ADR 2026-10-28-performance-budget: a performance budget, a big-library load test and an APK size gate

**Status:** Accepted (2026-10-28). Asked for by the maintainer before v0.1.0: the free app runs on the phone, so it
must stay fast with a reader's whole collection (1,000 to 5,000 books), and every change must say what it costs.

## Context
Nothing tested more than a handful of books. ADR-069 chose no paging for the library (2,000 books are under 1 MB of
state) and folds search keys once per change, but no test held it to that. CI already times cold start, frames and
memory of the debug build against the dev build (ADR-072, `scripts/perf/time_builds.sh`), reported only. The APK
size was never checked.

## Decision
1. **One budget, in `LibraryLoadBudget`** (in `:core`'s test fixtures, shared by the JVM, emulator and journey tests and never in the app). Three
   test sets check the same numbers.
2. **JVM load test** (`LibraryLoadTest`): 1,000 and 5,000 books in three scripts, with authors, formats,
   collections and reading positions, through the real `Library` and `LibraryController` with in-memory stores. Best
   of 3 runs. Each step has a time limit at 5,000 books, and may grow at most 12 times from 1,000 to 5,000 (linear
   work grows 5 times, work that compares every book with every other 25 times). A second test proves typing narrows
   the list once, when typing pauses, not on every key. The numbers go on the CI run's summary page.
3. **Emulator list test** (`LibraryLoadScreenTest`): the library screen with 5,000 books, as covers and as a list.
   The first screen, 10 flings timed by the platform's own frame metrics (no new library), and a scroll to the last
   book. Numbers in logcat under `AudoraLoadTest`.
4. **Release journey step 40** (`LoadJourney`): 1,000 small text books added to the installed app through the real
   library, one at a time as a folder of books arrives; the search finds the last one; all are removed again. It runs
   last: run after step 19, the thousand books' background work left step 26's long-press without its menu on CI.
5. **APK size gate** (`scripts/perf/check_apk_size.py`, budget `scripts/perf/apk-budget.json`): the dev and release
   APKs fail the build over their budget; the dev APK's change from the published dev build shows on the summary
   page, with a warning past 1 MB.

## The budget

| What | Limit | Gated or reported | Where |
|---|---|---|---|
| First list, 5,000 books (index, filters, progress, file check) | 500 ms | Gated | JVM |
| Sort or shelf change, 5,000 books | 200 ms | Gated | JVM |
| Typed search, after the pause, 5,000 books | 200 ms | Gated | JVM |
| File check, 5,000 books, free checks (the library's own work) | 200 ms | Gated | JVM |
| Growth from 1,000 to 5,000 books, any step over 40 ms | 12 times | Gated | JVM |
| Search narrows once typing pauses, not per key | 1 narrowing | Gated | JVM |
| First screen of 5,000 books | 4 s | Gated | Emulator |
| Scroll to the last of 5,000 books | 6 s | Gated | Emulator |
| Frozen frames (over 700 ms) while flinging | 0 | Gated | Emulator |
| Janky frames (over their deadline), slowest frame | | Reported | Emulator |
| Search shows the last of 1,000 books in the installed app | 5 s | Gated | Journey |
| Adding 1,000 books one at a time | | Reported | Journey |
| Dev and release APK size | 125 MB each | Gated | CI build |
| Dev APK change from the published dev build | 1 MB | Warning | CI build |
| Cold start, first-screen frames, memory (debug vs dev build) | | Reported | Emulator (ADR-072) |

The JVM limits are about ten times what this machine took when they were set (32 ms for the first list of 5,000
books, 4 ms for a sort, 2 ms for a search): a limit catches a design mistake, not noise. The dev APK was 112.97 MB
(dev-build ce1caf2), so its budget leaves about 10 %.

**Emulator numbers are not phone numbers.** CI's emulator draws with a software GPU, so many frames miss their
deadline there; only frozen frames fail, and jank is a number to watch from run to run. The first CI run reported no
frame metrics at all (`frames=0`), so the test also logs each fling's time, and warns when no frames were measured
rather than reading "0 frozen" as a pass. A phone check is still the judge of smoothness (the progress entry lists it).

## Not done (flagged, for the maintainer to decide)
- **Baseline profile.** It would ship the app's hot code paths precompiled, usually 15 to 30 % faster cold start and
  first scroll on a fresh install. Cost: two new dependencies (`androidx.baselineprofile` Gradle plugin and
  `androidx.profileinstaller`, about 30 KB in the APK), a new benchmark module, and a CI job on the emulator that
  regenerates the profile (5 to 10 minutes). AGENTS.md asks before a new dependency, so it waits for a yes.
- **The real file check is one provider call per book**, every time the library is shown and when the app comes back
  to the front. The test measures only the library's own work (free checks). On a phone with 5,000 books from a cloud
  app this is thousands of calls, off the main thread but costing battery and delaying a "file missing" mark. A
  lighter design (check the books on screen first, the rest in the background once) is proposed, not made.

## Consequences
- No new dependency. Nothing in the app changes: the budget class is unused by app code.
- CI: the JVM test adds about 2 s, the emulator test about a minute, step 40 a minute or two, the size gate a second.
- A change that slows the library past the budget, or grows the APK past it, fails CI with the measured number.
