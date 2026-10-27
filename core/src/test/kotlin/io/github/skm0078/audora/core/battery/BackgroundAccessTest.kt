// File: core/src/test/kotlin/io/github/skm0078/audora/core/battery/BackgroundAccessTest.kt
// Purpose: Background reading counts as set up only for Unrestricted; "Allow background usage" alone is Optimized.

package io.github.skm0078.audora.core.battery

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundAccessTest {
    @Test
    fun `unrestricted only when Android ignores battery optimizations`() {
        assertEquals(BackgroundAccess.UNRESTRICTED, access(ignoring = true, restricted = false))
    }

    @Test
    fun `background usage allowed but optimized is not set up yet`() {
        // The phone-test bug: the switch was on, the warning stayed, and nothing said Optimized was the reason.
        assertEquals(BackgroundAccess.OPTIMIZED, access(ignoring = false, restricted = false))
    }

    @Test
    fun `background usage off is restricted whatever else Android says`() {
        assertEquals(BackgroundAccess.RESTRICTED, access(ignoring = false, restricted = true))
        assertEquals(BackgroundAccess.RESTRICTED, access(ignoring = true, restricted = true))
    }

    private fun access(ignoring: Boolean, restricted: Boolean) =
        BackgroundAccess.of(ignoringOptimizations = ignoring, backgroundRestricted = restricted)
}
