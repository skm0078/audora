// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SleepMenuTest.kt
// Purpose: The sleep timer menu's choices and the bedtime window's clock steps (plan item 37).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepMenuTest {
    private val config = ReaderConfig(sleepMinutes = listOf(15), sleepChapterCounts = listOf(2, 3))

    @Test
    fun `chapter choices only for books with chapters`() {
        assertEquals(
            listOf(SleepTimerSetting.Off, SleepTimerSetting.AfterMinutes(15), SleepTimerSetting.EndOfPage),
            sleepTimerChoices(hasChapters = false, config),
        )
        assertEquals(
            listOf(
                SleepTimerSetting.Off,
                SleepTimerSetting.AfterMinutes(15),
                SleepTimerSetting.EndOfPage,
                SleepTimerSetting.EndOfChapter,
                SleepTimerSetting.AfterChapters(2),
                SleepTimerSetting.AfterChapters(3),
            ),
            sleepTimerChoices(hasChapters = true, config),
        )
    }

    @Test
    fun `bedtime steps wrap around midnight`() {
        assertEquals(30, stepMinute(23 * 60 + 30, 60))
        assertEquals(23 * 60 + 30, stepMinute(0, -30))
        assertEquals(22 * 60 + 30, stepMinute(22 * 60, 30))
    }

    @Test
    fun `clock text is 24-hour`() {
        assertEquals("22:00", clockText(22 * 60))
        assertEquals("06:05", clockText(6 * 60 + 5))
        assertEquals("00:00", clockText(0))
    }
}
