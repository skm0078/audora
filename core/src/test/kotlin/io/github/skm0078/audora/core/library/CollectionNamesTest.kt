// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/CollectionNamesTest.kt
// Purpose: A shelf or collection name cut at its length limit keeps whole characters: an emoji or a Bengali conjunct
//          at the limit is left out, never cut in half into a broken box.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionNamesTest {
    private val limit = CollectionNames.MAX_LENGTH

    @Test
    fun `an emoji that would cross the limit is left out whole`() {
        val name = "a".repeat(limit - 1) + "📚"

        val cleaned = CollectionNames.clean(name)

        assertEquals("a".repeat(limit - 1), cleaned)
    }

    @Test
    fun `a Bengali conjunct at the limit is not split after its virama`() {
        // ক্ষ is ক + virama + ষ: a cut after the virama would leave a dangling half letter.
        val name = "a".repeat(limit - 2) + "ক্ষ"

        val cleaned = CollectionNames.clean(name)

        assertEquals("a".repeat(limit - 2), cleaned)
    }

    @Test
    fun `a name within the limit is only trimmed and its spaces joined`() {
        assertEquals("Bedtime stories 🌙", CollectionNames.clean("  Bedtime   stories 🌙 "))
    }

    @Test
    fun `a blank name is no name`() {
        assertNull(CollectionNames.clean("   "))
    }
}
