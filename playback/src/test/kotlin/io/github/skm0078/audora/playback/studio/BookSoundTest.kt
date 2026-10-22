// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/BookSoundTest.kt
// Purpose: A book's own voices and pace reach every whole-book renderer; files keep the voice's own rate.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.notes.BookSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookSoundTest {
    private val global = SpeechStyle(rate = LISTENER_RATE, pace = Pace.NORMAL)
    private val voice = VoiceId(EngineId.SHERPA, "piper-hi-pratham")
    private val own = BookSettings(BookId("b"), rate = BOOK_RATE, pace = Pace.RELAXED, voices = mapOf(Lang.HI to voice))

    @Test
    fun `the book's pace and speed sit on the listener's style`() {
        assertEquals(global.copy(rate = BOOK_RATE, pace = Pace.RELAXED), BookSound.style(own, global, false))
        assertEquals(global, BookSound.style(null, global, false))
    }

    @Test
    fun `files keep the book's pace at the voice's own rate`() {
        val heard = BookSound.style(own, global, normalRate = true)
        assertEquals(SpeechStyle.NORMAL_RATE, heard.rate)
        assertEquals(Pace.RELAXED, heard.pace)
        assertEquals(SpeechStyle.NORMAL_RATE, BookSound.style(null, global, normalRate = true).rate)
    }

    @Test
    fun `the book's own voice reads its language and the rest follow the global choice`() {
        assertEquals(voice, BookSound.voices(own)(Lang.HI))
        assertNull(BookSound.voices(own)(Lang.EN))
        assertNull(BookSound.voices(null)(Lang.HI))
    }

    private companion object {
        const val LISTENER_RATE = 1.5f
        const val BOOK_RATE = 0.8f
    }
}
