// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/comic/ComicPages.kt
// Purpose: Picks a comic archive's (.cbz) pages from its entries and puts them in reading order
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.comic

/**
 * The pages are the pictures Android decodes, in natural name order ("page2" before "page10"), folders included in
 * the name, as comic readers do. Mac resource forks (`__MACOSX/`, `._name`), hidden files and anything else
 * (ComicInfo.xml, text) are left out.
 */
object ComicPages {
    private val PICTURE_ENDINGS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
    private val NUMBER_OR_NOT = Regex("""\d+|\D+""")

    fun order(entryNames: Collection<String>): List<String> = entryNames
        .filter(::isPage)
        .sortedWith(NaturalOrder)

    fun isPage(name: String): Boolean {
        val lower = name.lowercase()
        val file = lower.substringAfterLast('/')
        return !lower.endsWith('/') && !lower.startsWith("__macosx/") && !file.startsWith('.') &&
            PICTURE_ENDINGS.any { lower.endsWith(it) }
    }

    /** Runs of digits compare as numbers (leading zeros ignored), everything else without case. */
    private object NaturalOrder : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val left = NUMBER_OR_NOT.findAll(a.lowercase()).map { it.value }.toList()
            val right = NUMBER_OR_NOT.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(left.size, right.size)) {
                val order = comparePart(left[i], right[i])
                if (order != 0) return order
            }
            return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
        }

        private fun comparePart(a: String, b: String): Int = if (a[0].isDigit() && b[0].isDigit()) {
            val x = a.trimStart('0')
            val y = b.trimStart('0')
            if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
        } else {
            a.compareTo(b)
        }
    }
}
