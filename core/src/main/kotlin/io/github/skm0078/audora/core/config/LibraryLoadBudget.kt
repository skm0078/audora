// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LibraryLoadBudget.kt
// Purpose: The performance budget a big library must stay inside (ADR 2026-10-28-performance-budget): the numbers the
//          JVM load test, the emulator list test and the release journey's step 40 check, in one place.

package io.github.skm0078.audora.core.config

/**
 * Read by tests only, so R8 leaves it out of the app. Kept here, not in a test folder, because three test sets (JVM,
 * emulator, journey) check the same numbers and the ADR points at one file. CI machines are slower and noisier than a
 * phone, so each JVM limit is about ten times what a CI machine took when it was set (ADR): a limit catches a design
 * mistake (work done per keystroke, an O(n²) loop, I/O on the main thread), not a few percent; [maxGrowth] catches a
 * loop that compares every book with every other on any machine.
 *
 * @property largeLibraryBooks the size the library is tested at: a big reader's whole collection.
 * @property smallLibraryBooks the size [maxGrowth] is measured against.
 * @property firstListMs from the books arriving to the library's first list (index, filters, sort) at
 *   [largeLibraryBooks], on the JVM.
 * @property queryChangeMs a new sort or shelf applied to [largeLibraryBooks].
 * @property searchMs a typed search narrowing [largeLibraryBooks], once typing pauses.
 * @property availabilityMs the file check over [largeLibraryBooks], with checks that cost nothing: what the library
 *   adds on top of the provider's own time.
 * @property maxGrowth how much slower [largeLibraryBooks] may be than [smallLibraryBooks]. Linear work grows 5 times
 *   and sorting a little more; work that compares every book with every other grows 25 times.
 * @property growthFloorMs below this a time is noise, so its growth is not judged.
 * @property firstFrameMs the emulator draws the first screen of a [largeLibraryBooks] list within this.
 * @property scrollToEndMs the emulator scrolls from the first book to the last within this.
 * @property frozenFrameMs a frame longer than this is frozen (Android vitals' line); none may happen while scrolling.
 * @property journeyBooks the release journey seeds this many books into the installed app.
 * @property journeySearchMs on the installed app, a typed title shows its book within this.
 */
data class LibraryLoadBudget(
    val largeLibraryBooks: Int = 5_000,
    val smallLibraryBooks: Int = 1_000,
    val firstListMs: Long = 500,
    val queryChangeMs: Long = 200,
    val searchMs: Long = 200,
    val availabilityMs: Long = 200,
    val maxGrowth: Double = 12.0,
    val growthFloorMs: Long = 40,
    val firstFrameMs: Long = 4_000,
    val scrollToEndMs: Long = 6_000,
    val frozenFrameMs: Long = 700,
    val journeyBooks: Int = 1_000,
    val journeySearchMs: Long = 5_000,
) {
    init {
        require(smallLibraryBooks in 1 until largeLibraryBooks) {
            "smallLibraryBooks ($smallLibraryBooks) must be at least 1 and below largeLibraryBooks ($largeLibraryBooks)"
        }
        require(maxGrowth >= largeLibraryBooks.toDouble() / smallLibraryBooks) {
            "maxGrowth ($maxGrowth) must allow at least linear growth"
        }
        require(journeyBooks > 0) { "journeyBooks must be > 0, was $journeyBooks" }
        val limits = listOf(
            firstListMs, queryChangeMs, searchMs, availabilityMs, growthFloorMs, firstFrameMs, scrollToEndMs,
            frozenFrameMs, journeySearchMs,
        )
        require(limits.all { it > 0 }) { "every time limit must be > 0, was $limits" }
    }
}
