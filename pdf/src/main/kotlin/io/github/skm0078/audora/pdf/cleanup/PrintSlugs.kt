// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/PrintSlugs.kt
// Purpose: Tells a printer's slug in a page margin: a layout file's name, or a print date and time.

package io.github.skm0078.audora.pdf.cleanup

/**
 * A printer's slug is what typesetting software stamps outside the printed page and a PDF made from the print file
 * keeps: "HUD3_25_09Single-Bengali-BE.indd Sec1:9", "Chapter 9 Transitions.indd 71", "5/13/11 6:45 AM",
 * "17-12-2019 12:54:06", "22-Nov-2019 2:51:06 PM". Callers look only at a page's margin lines, text layer and OCR
 * alike. A slug by itself ([standsAlone]) is a line that ends with a layout file's name and only page references or
 * a print time after it, or a bare print time with its seconds, which no date sheet, notice or receipt prints alone.
 * Any other print time is a slug only when a neighbouring page's margin carries the same time to the minute
 * ([times]): a time alone is an exam date sheet's row, a notice's date or a receipt's first line.
 */
internal object PrintSlugs {
    /** [text] is a print time alone, to the minute or the second, with "AM" or "PM" after it if given. */
    fun isBareStamp(text: String): Boolean = BARE_STAMP.matches(text)

    /** [text] is a slug whatever other pages hold: a layout file's name at its end, or a bare stamp to the second. */
    fun standsAlone(text: String): Boolean = LAYOUT_FILE.containsMatchIn(text) ||
        BARE_STAMP.matchEntire(text)?.groupValues?.get(SECONDS)?.isNotEmpty() == true

    /** The print times in [text], each written alike to the minute ("5/13/11 6:45"), to match a neighbour's. */
    fun times(text: String): Set<String> =
        PRINT_TIME.findAll(text).mapTo(HashSet()) { "${it.groupValues[DATE]} ${it.groupValues[MINUTE]}" }

    private const val DATE = 1
    private const val MINUTE = 2
    private const val SECONDS = 3

    /**
     * Date (numbers, or a month's short name: "22-Nov-2019"), then the time to the minute, then its seconds if
     * given; the clipped copy of the date some PDFs repeat, its last two parts, may come between ("5/13/11 /13/11
     * 6:45"), and is no part of the key. ASCII digits
     * and spaces only, as typesetting software writes them (`\d` and `\s` would also take Devanagari digits and a
     * no-break space on Android's ICU regex, not on the JVM).
     */
    private const val TIME =
        """([0-9]{1,4}[/.-](?:[0-9]{1,2}|[A-Za-z]{3})[/.-][0-9]{2,4})(?:[ \t]+/[0-9]{1,2}/[0-9]{2,4})?,?[ \t]+""" +
            """([0-9]{1,2}:[0-9]{2})(:[0-9]{2})?"""

    private val PRINT_TIME = Regex(TIME)

    /** A small Roman page number ("iii", "xiv"), up to 399: a prelims page; "civil" is none. */
    private const val ROMAN = """(?=[ivxlc])c{0,3}(?:xc|xl|l?x{0,3})(?:ix|iv|v?i{0,3})"""

    /** "AM" or "PM" after a time, with or without stops. */
    private const val MERIDIEM = """(?:[ \t]*(?i:[ap]\.?m\.?))?"""

    /**
     * A line holding nothing but a print time, with "AM" or "PM" after it if given, the same time perhaps repeated
     * as some PDFs clip it ("5/13/11 /13/11 6:45 AM 6:45 AM"); a second, different time ("10:00 AM 1:00 PM") is a
     * date sheet's row.
     */
    private val BARE_STAMP =
        Regex("""[ \t]*$TIME$MERIDIEM(?:[ \t]+\2(?::[0-9]{2})?$MERIDIEM)?[ \t]*""")

    /**
     * A layout program's file name ("ch04.qxd", "HUD3_25_09.indd") with nothing after it but page references and
     * a print stamp ("Sec1:9", "All Pages", "71", "iii", "Page 3 of 10", "2/3/09 10:12 AM Page 62", the file's name
     * again, or the clipped copy of a date a PDF repeats: "5/13/11 /13/11"), so a body line that names a file
     * ("Open Lesson01.indd and click") is no slug.
     */
    private val LAYOUT_FILE = Regex(
        """\S\.(?i:indd|qxd|qxp)(?:[ \t]+(?:\S*\.(?i:indd|qxd|qxp)|/?[0-9][0-9/.:,-]*|""" +
            """[0-9]{1,4}[/.-][A-Za-z]{3}[/.-][0-9]{2,4}|""" +
            """(?i:[ap]\.?m\.?|page|pages|of|all|sec[0-9:]+|$ROMAN)))*[ \t]*$""",
    )
}
