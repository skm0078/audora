// File: app/src/test/kotlin/io/github/skm0078/audora/language/AppLanguageTest.kt
// Purpose: The app language choice (plan item 44): stored names read back, and English until a choice is made.

package io.github.skm0078.audora.language

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `stored names read back and anything else is English`() {
        val cases = listOf(
            null to AppLanguage.ENGLISH,
            "" to AppLanguage.ENGLISH,
            "KLINGON" to AppLanguage.ENGLISH,
            "hindi" to AppLanguage.ENGLISH,
            "HINDI" to AppLanguage.HINDI,
            "BENGALI" to AppLanguage.BENGALI,
            "PHONE" to AppLanguage.PHONE,
            "ENGLISH" to AppLanguage.ENGLISH,
        )
        cases.forEach { (stored, expected) -> assertEquals("$stored", expected, AppLanguage.fromName(stored)) }
    }

    @Test
    fun `only the phone's language has no tag`() {
        assertEquals(listOf(AppLanguage.PHONE), AppLanguage.entries.filter { it.tag == null })
    }
}
