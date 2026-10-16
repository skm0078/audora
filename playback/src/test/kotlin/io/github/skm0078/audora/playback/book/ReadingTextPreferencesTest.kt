// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/book/ReadingTextPreferencesTest.kt
// Purpose: The reading text settings: defaults when missing or unreadable, saved and read back, applied to cleanup.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.config.CleanupConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingTextPreferencesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file by lazy { File(temp.root, "settings/${ReadingTextPreferences.FILE_NAME}") }
    private val preferences by lazy { ReadingTextPreferences(file) }

    @Test
    fun `no file means citation markers are skipped, as the spec says`() {
        assertTrue(preferences.skipCitationMarkers())
        assertEquals(CleanupConfig(), preferences.cleanupConfig())
        assertFalse(file.exists())
    }

    @Test
    fun `a choice is saved and read back by a new instance`() {
        assertTrue(preferences.setSkipCitationMarkers(false))

        assertFalse(ReadingTextPreferences(file).skipCitationMarkers())
        assertFalse(ReadingTextPreferences(file).cleanupConfig().skipCitationMarkers)

        assertTrue(preferences.setSkipCitationMarkers(true))
        assertTrue(ReadingTextPreferences(file).skipCitationMarkers())
    }

    @Test
    fun `the choice is applied over the given config and changes nothing else`() {
        preferences.setSkipCitationMarkers(false)
        val base = CleanupConfig(maxMarginLines = 2)

        assertEquals(base.copy(skipCitationMarkers = false), preferences.cleanupConfig(base))
    }

    @Test
    fun `an unreadable or nonsense file means the default`() {
        file.parentFile.mkdirs()
        val cases = listOf(
            "skipCitationMarkers=\\uZZZZ\n", // a malformed escape: Properties.load throws
            "skipCitationMarkers=maybe\n",
            "something else entirely\n",
        )
        for (content in cases) {
            file.writeText(content)

            assertTrue(content, preferences.skipCitationMarkers())
        }
    }

    @Test
    fun `a failed save returns false and keeps the old value`() {
        preferences.setSkipCitationMarkers(false)
        File(file.path + ".tmp").mkdirs() // the temp file cannot be written

        assertFalse(preferences.setSkipCitationMarkers(true))
        assertFalse(preferences.skipCitationMarkers())
    }
}
