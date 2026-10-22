// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/BookSound.kt
// Purpose: How a book's own settings change the way it sounds, for every renderer that reads a whole book.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.notes.BookSettings

/**
 * Studio, the talking book and the audiobook export apply a book's own settings this one way, as playback does
 * (BookContext), so the files sound like listening: the book's voices for its languages and its pace on top of the
 * listener's style.
 */
object BookSound {
    /**
     * The listener's [global] style with [own] on top.
     * @param normalRate the voice's own rate instead of any speed, for files played in apps with their own speed
     *   control.
     */
    fun style(own: BookSettings?, global: SpeechStyle, normalRate: Boolean): SpeechStyle {
        val heard = own?.over(global) ?: global
        return if (normalRate) heard.copy(rate = SpeechStyle.NORMAL_RATE) else heard
    }

    /** The book's own voice for a language; null follows the global choice. */
    fun voices(own: BookSettings?): (Lang) -> VoiceId? = { lang -> own?.voices?.get(lang) }
}
