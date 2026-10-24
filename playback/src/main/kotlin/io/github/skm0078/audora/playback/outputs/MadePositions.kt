// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/MadePositions.kt
// Purpose: Where the listener stopped in each saved audiobook or studio file (ADR-068), so it goes on from there.

package io.github.skm0078.audora.playback.outputs

import android.content.Context

/** A set's chapter and position in it. */
data class MadePosition(val index: Int, val positionMs: Long)

/**
 * One small preferences file, a line per set: `<chapter index>:<position ms>`. A set's media items are named
 * `<set key>#<part>` ([mediaId]), so the player knows which set it is playing without anything else.
 */
class MadePositions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(setKey: String): MadePosition? = prefs.getString(setKey, null)?.let(::parse)

    fun save(setKey: String, index: Int, positionMs: Long) {
        prefs.edit().putString(setKey, "$index$SEPARATOR${positionMs.coerceAtLeast(0)}").apply()
    }

    fun clear(setKey: String) {
        prefs.edit().remove(setKey).apply()
    }

    companion object {
        /** A stored line read back; null for anything damaged. */
        fun parse(stored: String): MadePosition? {
            val parts = stored.split(SEPARATOR)
            val index = parts.getOrNull(0)?.toIntOrNull()
            val position = parts.getOrNull(1)?.toLongOrNull()
            val valid = parts.size == 2 && index != null && position != null && index >= 0 && position >= 0
            return if (valid) MadePosition(requireNotNull(index), requireNotNull(position)) else null
        }

        private const val PREFS = "made_positions"
        private const val SEPARATOR = ":"
        private const val PART_MARK = "#"

        /** The media item id of [setKey]'s [part]. */
        fun mediaId(setKey: String, part: Int) = "$setKey$PART_MARK$part"

        /** The set a media item id belongs to; null for an id that is not one of these. */
        fun setOf(mediaId: String): String? = mediaId.substringBeforeLast(PART_MARK, "").ifEmpty { null }
    }
}
