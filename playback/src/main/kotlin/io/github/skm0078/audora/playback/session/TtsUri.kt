// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/TtsUri.kt
// Purpose: The audora:// URIs of playlist items: which book, which render epoch, which sentence (Build Spec §6).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * `audora://book/<id>/e/<epoch>/p/<page>/u/<index>` for a sentence, `audora://book/<id>/e/<epoch>/more/<serial>`
 * for the "more is coming" placeholder. The epoch changes whenever the render queue is rebuilt (new start
 * position, new voice), so a load left over from the old playlist can never be served audio from the new one.
 * Plain strings, so the format is unit-tested on the JVM; also used as the item's media id.
 */
sealed interface TtsUri {
    val bookId: String
    val epoch: Int

    data class Sentence(override val bookId: String, override val epoch: Int, val key: UnitKey) : TtsUri {
        override fun toString(): String = "$PREFIX$bookId/$EPOCH/$epoch/$PAGE/${key.page}/$UNIT/${key.index}"
    }

    data class More(override val bookId: String, override val epoch: Int, val serial: Int) : TtsUri {
        override fun toString(): String = "$PREFIX$bookId/$EPOCH/$epoch/$MORE/$serial"
    }

    companion object {
        private const val PREFIX = "audora://book/"
        private const val EPOCH = "e"
        private const val PAGE = "p"
        private const val UNIT = "u"
        private const val MORE = "more"
        private val SENTENCE =
            Regex("""^audora://book/(?<book>[^/]+)/e/(?<epoch>\d{1,9})/p/(?<page>\d{1,9})/u/(?<unit>\d{1,9})$""")
        private val MORE_ITEM = Regex("""^audora://book/(?<book>[^/]+)/e/(?<epoch>\d{1,9})/more/(?<serial>\d{1,9})$""")

        /** Null for anything that is not exactly one of the two forms. */
        fun parse(uri: String): TtsUri? = SENTENCE.matchEntire(uri)?.let { match ->
            Sentence(match.text("book"), match.int("epoch"), UnitKey(match.int("page"), match.int("unit")))
        } ?: MORE_ITEM.matchEntire(uri)?.let { match ->
            More(match.text("book"), match.int("epoch"), match.int("serial"))
        }

        private fun MatchResult.text(group: String): String =
            checkNotNull(groups[group]) { "Regex group $group missing" }.value

        private fun MatchResult.int(group: String): Int = text(group).toInt()
    }
}
