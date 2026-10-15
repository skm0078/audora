// File: core/src/test/kotlin/io/github/skm0078/audora/core/result/OutcomeTest.kt
// Purpose: Outcome combinators.

package io.github.skm0078.audora.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {
    private val ok: Outcome<Int, String> = Outcome.Success(2)
    private val failed: Outcome<Int, String> = Outcome.Failure("boom")

    @Test
    fun `map and flatMap transform only successes`() {
        assertEquals(Outcome.Success(4), ok.map { it * 2 })
        assertEquals(failed, failed.map { it * 2 })
        assertEquals(
            Outcome.Failure("odd"),
            ok.flatMap {
                if (it % 2 ==
                    1
                ) {
                    Outcome.Success(it)
                } else {
                    Outcome.Failure("odd")
                }
            },
        )
    }

    @Test
    fun `accessors`() {
        assertEquals(2, ok.getOrNull())
        assertNull(failed.getOrNull())
        assertEquals("boom", failed.errorOrNull())
        assertNull(ok.errorOrNull())
    }
}
