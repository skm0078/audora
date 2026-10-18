// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/PrintSlugs.kt
// Purpose: Tells a printer's slug in a page margin: a layout file's name, or a print date and time.

package io.github.skm0078.audora.pdf.cleanup

/**
 * A printer's slug is what typesetting software stamps outside the printed page and a PDF made from the print file
 * keeps: "HUD3_25_09Single-Bengali-BE.indd Sec1:9", "Chapter 9 Transitions.indd 71", "5/13/11 6:45 AM",
 * "17-12-2019 12:54:06", "22-Nov-2019 2:51:06 PM". Callers look only at a page's margin lines, text layer and OCR
 * alike. A layout file's name is a slug alone. A print time is a slug when it gives its seconds, which no date sheet,
 * notice or receipt does, or when a neighbouring page's margin carries the same time to the minute; a time alone is
 * an exam date sheet's row, a notice's date or a receipt's first line.
 */
internal object PrintSlugs {
    /** [text] names a layout program's file: "ch04.qxd", "HUD3_25_09.indd". */
    fun namesLayoutFile(text: String): Boolean = LAYOUT_FILE.containsMatchIn(text)

    /** The print times in [text], each written alike to the minute ("5/13/11 6:45"), to match a neighbour's. */
    fun times(text: String): Set<String> =
        PRINT_TIME.findAll(text).mapTo(HashSet()) { "${it.groupValues[DATE]} ${it.groupValues[MINUTE]}" }

    /** [text] holds a print time with its seconds ("17-12-2019 12:54:06"). */
    fun hasStampWithSeconds(text: String): Boolean =
        PRINT_TIME.findAll(text).any { it.groupValues[SECONDS].isNotEmpty() }

    private const val DATE = 1
    private const val MINUTE = 2
    private const val SECONDS = 3

    private val LAYOUT_FILE = Regex("""\S\.(?i:indd|qxd|qxp)\b""")

    /**
     * Date (numbers, or a month's short name: "22-Nov-2019"), then the time to the minute, then its seconds if
     * given. ASCII digits only, as typesetting software writes them (`\d` would also take Devanagari and Bengali
     * digits on Android's ICU regex).
     */
    private val PRINT_TIME = Regex(
        """([0-9]{1,4}[/.-](?:[0-9]{1,2}|[A-Za-z]{3})[/.-][0-9]{2,4}),?\s+([0-9]{1,2}:[0-9]{2})(:[0-9]{2})?""",
    )
}
