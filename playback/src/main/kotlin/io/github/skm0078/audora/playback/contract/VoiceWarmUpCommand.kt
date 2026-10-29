// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/VoiceWarmUpCommand.kt
// Purpose: The custom command that asks the playback service to load a book's voice before Play is pressed
//          (ADR 2026-10-29-speed-quick-wins). Its own object: the command, its arguments and its parser sit together.

package io.github.skm0078.audora.playback.contract

import android.os.Bundle
import androidx.media3.session.SessionCommand
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang

object VoiceWarmUpCommand {
    const val ACTION = "io.github.skm0078.audora.playback.WARM_VOICE"

    /**
     * Loads the voice that would read [ARG_LANG] in [ARG_BOOK_ID] (the book's own voice when it has one), and nothing
     * else: no sound, no playlist, no notification. Safe to send without a user action, unlike PLAY_FROM.
     */
    val COMMAND = SessionCommand(ACTION, Bundle.EMPTY)

    const val ARG_BOOK_ID = "warm_book_id"

    /** `Lang.name` of the book's first sentence. */
    const val ARG_LANG = "warm_lang"

    fun args(book: BookId, lang: Lang): Bundle = Bundle().apply {
        putString(ARG_BOOK_ID, book.value)
        putString(ARG_LANG, lang.name)
    }

    /** Parses [args]; null when the book or the language is missing or unknown. */
    fun from(args: Bundle): Request? {
        val book = args.getString(ARG_BOOK_ID)?.takeIf { it.isNotBlank() } ?: return null
        val lang = args.getString(ARG_LANG)?.let { name -> Lang.entries.firstOrNull { it.name == name } } ?: return null
        return Request(BookId(book), lang)
    }

    data class Request(val book: BookId, val lang: Lang)
}
