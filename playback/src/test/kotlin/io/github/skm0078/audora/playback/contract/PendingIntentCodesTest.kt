// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/contract/PendingIntentCodesTest.kt
// Purpose: No two PendingIntent purposes share a request code, so a careless merge fails here, not on a phone.

package io.github.skm0078.audora.playback.contract

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingIntentCodesTest {
    private val codes: Map<String, Int> = PendingIntentCodes::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .associate { it.name to it.getInt(null) }

    @Test
    fun everyCodeIsDistinct() {
        assertTrue("no codes found", codes.isNotEmpty())
        val shared = codes.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertEquals("codes shared: $shared", emptyMap<Int, List<String>>(), shared)
    }

    @Test
    fun noCodeIsZero() {
        // Zero is what a PendingIntent built without thought gets: keep it free so it never matches one of ours.
        assertTrue("zero used by ${codes.filterValues { it == 0 }.keys}", codes.values.none { it == 0 })
    }
}
