// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/LibraryLoadBudgetTest.kt
// Purpose: The performance budget refuses limits that could never catch anything (ADR 2026-10-28-performance-budget).

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLoadBudgetTest {
    @Test
    fun `the default budget tests a big library against a smaller one`() {
        val budget = LibraryLoadBudget()
        assertTrue(budget.largeLibraryBooks >= 5 * budget.smallLibraryBooks)
    }

    @Test
    fun `nonsense budgets are refused`() {
        assertThrows(IllegalArgumentException::class.java) { LibraryLoadBudget(smallLibraryBooks = 5_000) }
        assertThrows(IllegalArgumentException::class.java) { LibraryLoadBudget(maxGrowth = 2.0) }
        assertThrows(IllegalArgumentException::class.java) { LibraryLoadBudget(searchMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { LibraryLoadBudget(journeyBooks = 0) }
    }
}
