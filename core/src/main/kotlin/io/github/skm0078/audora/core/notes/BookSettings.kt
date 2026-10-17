// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/BookSettings.kt
// Purpose: One book's own speed, pace and voices (plan item 4), used instead of the global ones for that book.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceMaps
import io.github.skm0078.audora.core.result.Outcome

/**
 * A book with settings reads at its own [rate] and [pace] and with its own [voices]; anything left null (or a
 * language with no voice here) follows the global setting. Reading modes and sound stay global.
 */
data class BookSettings(
    val bookId: BookId,
    val rate: Float? = null,
    val pace: Pace? = null,
    val voices: Map<Lang, VoiceId> = emptyMap(),
) {
    /** [global] with this book's rate and pace on top. */
    fun over(global: SpeechStyle): SpeechStyle = global.copy(rate = rate ?: global.rate, pace = pace ?: global.pace)

    companion object {
        /** The voices' stored form ([VoiceMaps]); [decodeVoices] reads it back, dropping bad entries. */
        fun encodeVoices(voices: Map<Lang, VoiceId>): String = VoiceMaps.encode(voices)

        fun decodeVoices(line: String?): Map<Lang, VoiceId> = VoiceMaps.decode(line)
    }
}

interface BookSettingsStore {
    suspend fun get(bookId: BookId): BookSettings?

    /** Replaces the book's settings. */
    suspend fun put(settings: BookSettings): Outcome<Unit, StorageError>

    /** Back to the global settings for this book. */
    suspend fun clear(bookId: BookId): Outcome<Unit, StorageError>
}
