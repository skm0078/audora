// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ContentsEntries.kt
// Purpose: Finds a contents page's entry lines ("Introduction ........ 5") so each entry is read as its own unit.

package io.github.skm0078.audora.pdf.units

/**
 * An entry line ends with a leader ([Leaders]), perhaps spaces, then a page number: digits in any script, a range
 * ("9-12"), a Roman numeral in either case ("ix", "VII") or a letter and number ("A-1"). A paragraph holding at least
 * [MIN_ENTRIES] of them is a contents list: each entry is one unit, cut at its line's end, with no sentence break
 * between its leader and its page number (the splitters break "Appendix ..... | A-1" and "Foreword ..... | V").
 * Prose with one leader in it ("वह चला गया...... २⏎दिन बाद", "is ______ which⏎is") holds one entry line at most and
 * is left alone.
 */
internal object ContentsEntries {
    /**
     * Each entry line of the paragraph from [start] to [end] of [text], as its leader's first offset until its line's
     * end, [mask] marking the leaders of [text]; empty unless there are at least [MIN_ENTRIES].
     */
    fun of(text: CharSequence, mask: BooleanArray, start: Int, end: Int): List<IntRange> {
        val lineEnds = (start until end).filter { text[it] in LINE_ENDS } + end
        val entries = lineEnds.mapNotNull { lineEnd -> entryAt(text, mask, start, lineEnd) }
        return entries.takeIf { it.size >= MIN_ENTRIES }.orEmpty()
    }

    /**
     * [sentences] (ranges of [text] in order, from [start] to [end]) with no break inside one of [entries] and a cut
     * at each entry's line end.
     */
    fun cut(sentences: List<IntRange>, entries: List<IntRange>, start: Int, end: Int): List<IntRange> {
        if (entries.isEmpty()) return sentences
        val kept = sentences.drop(1).map { it.first }.filterNot { at -> entries.any { at > it.first && at <= it.last } }
        val cuts = (kept + entries.map { it.last + 1 }).filter { it in (start + 1) until end }.distinct().sorted()
        return (listOf(start) + cuts).zip(cuts + end) { from, until -> from until until }
    }

    /** The entry whose line ends at [lineEnd] (after [start]), from its leader's first offset to [lineEnd], or null. */
    private fun entryAt(text: CharSequence, mask: BooleanArray, start: Int, lineEnd: Int): IntRange? {
        var i = lineEnd
        while (i > start && text[i - 1].isWhitespace()) i--
        val wordEnd = i
        while (i > start && !text[i - 1].isWhitespace() && !mask[i - 1]) i--
        val word = text.subSequence(i, wordEnd)
        while (i > start && text[i - 1] == ' ') i--
        if (i == start || !mask[i - 1] || !PAGE_NUMBER.matches(word)) return null
        while (i > start && mask[i - 1]) i--
        return i until lineEnd
    }

    /** A page number: "5", "७", "9-12", "ix", "VII", "A-1". */
    private val PAGE_NUMBER = Regex("""\p{Nd}+(?:[-–]\p{Nd}+)?|[ivxlcdm]+|[IVXLCDM]+|\p{L}[-–.]?\p{Nd}+""")

    /** A paragraph is a contents list once this many of its lines are entries. */
    private const val MIN_ENTRIES = 2

    private const val LINE_ENDS = "\n\r"
}
