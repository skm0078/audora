// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/SentenceTiming.kt
// Purpose: Where the voice speaks inside one sentence's clip, for the reader's word highlight (plan item 14).

package io.github.skm0078.audora.playback.contract

/**
 * In the clip's own time, which is what a controller's `currentPosition` counts inside the sentence's media item (the
 * player's speed changes how fast it runs, not these values). The clip is [leadMs] of cue (the sound before a chapter's
 * heading or a caption) or nothing, the voice for [speechMs] (with any pauses at punctuation inside it), then silence
 * up to [totalMs] (the pace's gap, a paragraph's pause). [page], [charStart] and [unitIndex] name the sentence (as the
 * media item's `ITEM_PAGE`, `ITEM_CHAR_START` and `ITEM_UNIT_INDEX` do), so a timing is never applied to another one:
 * match on [unitIndex] too, since a continued table's empty summary shares its first row's [charStart].
 */
data class SentenceTiming(
    val page: Int,
    val charStart: Int,
    val unitIndex: Int,
    val leadMs: Long,
    val speechMs: Long,
    val totalMs: Long,
) {
    init {
        require(page >= 0 && charStart >= 0 && unitIndex >= 0) { "page, charStart and unitIndex must be >= 0" }
        require(leadMs >= 0 && speechMs >= 0 && leadMs + speechMs <= totalMs) { "need lead + speech <= total" }
    }

    /** How far through the voice's speech [positionMs] is, 0 to 1: 0 during the cue, 1 in the silence after. */
    fun speechFraction(positionMs: Long): Float = when {
        positionMs <= leadMs -> 0f
        positionMs >= leadMs + speechMs -> 1f
        else -> (positionMs - leadMs).toFloat() / speechMs
    }

    fun encode(): String = listOf(page, charStart, unitIndex, leadMs, speechMs, totalMs).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"
        private const val FIELDS = 6
        private const val PAGE = 0
        private const val CHAR = 1
        private const val INDEX = 2
        private const val LEAD = 3
        private const val SPEECH = 4
        private const val TOTAL = 5

        /** Reads [encode]'s line; null when absent or malformed (no highlight beats a wrong one). */
        fun decode(line: String?): SentenceTiming? {
            val values = line?.split(SEPARATOR)?.mapNotNull { it.toLongOrNull() }
            if (values == null || values.size != FIELDS) return null
            return runCatching {
                SentenceTiming(
                    values[PAGE].toInt(),
                    values[CHAR].toInt(),
                    values[INDEX].toInt(),
                    values[LEAD],
                    values[SPEECH],
                    values[TOTAL],
                )
            }.getOrNull()
        }
    }
}
