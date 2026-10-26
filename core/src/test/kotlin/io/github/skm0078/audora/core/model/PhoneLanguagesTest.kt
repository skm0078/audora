// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/PhoneLanguagesTest.kt
// Purpose: Phone-language tags and the scripts they are written in (ADR 2026-10-26).

package io.github.skm0078.audora.core.model

import java.lang.Character.UnicodeScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLanguagesTest {
    @Test
    fun `tags are reduced to today's primary language`() {
        mapOf("fr-FR" to "fr", "PT_br" to "pt", " ta " to "ta", "iw" to "he", "in-ID" to "id", "ji" to "yi")
            .forEach { (raw, expected) -> assertEquals(raw, expected, PhoneLanguages.canonical(raw)) }
        assertNull(PhoneLanguages.canonical(""))
        assertNull(PhoneLanguages.canonical(null))
    }

    @Test
    fun `the three languages are not phone languages`() {
        listOf("en", "en-GB", "HI", "bn-IN").forEach { tag ->
            assertTrue(tag, PhoneLanguages.isTuned(tag))
            assertNull(tag, PhoneLanguages.phoneLanguage(tag))
        }
        assertFalse(PhoneLanguages.isTuned("fr"))
        assertEquals("fr", PhoneLanguages.phoneLanguage("fr-CA"))
    }

    @Test
    fun `each language's script`() {
        mapOf(
            "fr" to UnicodeScript.LATIN,
            "sw" to UnicodeScript.LATIN,
            "mr" to UnicodeScript.DEVANAGARI,
            "as" to UnicodeScript.BENGALI,
            "ta" to UnicodeScript.TAMIL,
            "ur" to UnicodeScript.ARABIC,
            "uk" to UnicodeScript.CYRILLIC,
            "ja" to UnicodeScript.HAN,
            "he" to UnicodeScript.HEBREW,
            "iw" to UnicodeScript.HEBREW,
            "el" to UnicodeScript.GREEK,
        ).forEach { (tag, script) -> assertEquals(tag, script, PhoneLanguages.scriptOf(tag)) }
    }

    @Test
    fun `scripts read by the three languages have no default`() {
        // Greek too: outside a Greek book its letters are maths.
        listOf(
            UnicodeScript.LATIN,
            UnicodeScript.DEVANAGARI,
            UnicodeScript.BENGALI,
            UnicodeScript.GREEK,
            UnicodeScript.COMMON,
        )
            .forEach { assertNull(it.name, PhoneLanguages.defaultFor(it)) }
        assertEquals("ta", PhoneLanguages.defaultFor(UnicodeScript.TAMIL))
        assertEquals("zh", PhoneLanguages.defaultFor(UnicodeScript.KATAKANA))
    }
}
