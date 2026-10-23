// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/MarkupScanner.kt
// Purpose: A forgiving XML/XHTML tokenizer for EPUB files: tags with attributes, decoded text, nothing else.

package io.github.skm0078.audora.playback.text

/** One piece of markup. Element and attribute names are lower-cased with any namespace prefix kept ("epub:type"). */
sealed interface Markup {
    data class Start(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean) : Markup {
        /** The name without its namespace prefix ("dc:title" is "title"). */
        val localName: String get() = name.substringAfter(':')
    }

    data class End(val name: String) : Markup {
        val localName: String get() = name.substringAfter(':')
    }

    data class Text(val text: String) : Markup
}

/**
 * Real-world EPUBs are often not quite XML: HTML entities such as `&nbsp;` without a DTD, stray `&`, unclosed tags.
 * A strict parser refuses the whole book over one of them, so this scanner never fails: it reads what it can and
 * treats anything it cannot as text. Comments, processing instructions and doctypes are dropped, CDATA kept as text.
 * No DTD or external entity is ever fetched.
 */
object MarkupScanner {
    fun scan(source: String): List<Markup> {
        val out = ArrayList<Markup>()
        val text = StringBuilder()
        var i = 0
        while (i < source.length) {
            val piece = if (source[i] == '<') special(source, i) ?: tag(source, i) else null
            if (piece == null) {
                text.append(source[i])
                i++
            } else {
                if (text.isNotEmpty()) out += Markup.Text(Entities.decode(text.toString())).also { text.clear() }
                piece.markup?.let(out::add)
                i = piece.end
            }
        }
        if (text.isNotEmpty()) out += Markup.Text(Entities.decode(text.toString()))
        return out
    }

    /** What was read at a `<`: a token (none for a comment or declaration) and where reading goes on. */
    private class Piece(val markup: Markup?, val end: Int)

    /** A comment, CDATA section, doctype or processing instruction at [at]; CDATA becomes text. */
    private fun special(source: String, at: Int): Piece? = when {
        source.startsWith(COMMENT_OPEN, at) -> Piece(null, endAfter(source, at, COMMENT_CLOSE))

        source.startsWith(CDATA_OPEN, at) -> {
            val end = endAfter(source, at, CDATA_CLOSE)
            val start = at + CDATA_OPEN.length
            Piece(Markup.Text(source.substring(start, maxOf(start, end - CDATA_CLOSE.length))), end)
        }

        source.startsWith("<!", at) || source.startsWith("<?", at) -> Piece(null, endAfter(source, at, ">"))

        else -> null
    }

    private fun endAfter(source: String, from: Int, close: String): Int {
        val found = source.indexOf(close, from + 2)
        return if (found < 0) source.length else found + close.length
    }

    /** A start or end tag at [at]; null when `<` does not open a tag (stray text such as "1 < 2"). */
    private fun tag(source: String, at: Int): Piece? {
        val end = tagEnd(source, at)
        val body = if (end < 0) "" else source.substring(at + 1, end).trim()
        val markup = when {
            body.startsWith('/') -> body.substring(1).trim().takeWhile { !it.isWhitespace() }.lowercase()
                .takeIf { it.isNotEmpty() }?.let { Markup.End(it) }

            body.firstOrNull()?.isLetter() == true -> startTag(body)

            else -> null
        }
        return markup?.let { Piece(it, end + 1) }
    }

    private fun startTag(body: String): Markup.Start {
        val selfClosing = body.endsWith('/')
        val inner = if (selfClosing) body.dropLast(1) else body
        val name = inner.takeWhile { !it.isWhitespace() }.lowercase()
        return Markup.Start(name, attributes(inner.substring(name.length)), selfClosing)
    }

    /** The `>` closing the tag at [at], skipping quoted attribute values; -1 when another `<` comes first. */
    private fun tagEnd(source: String, at: Int): Int {
        var quote: Char? = null
        var i = at + 1
        while (i < source.length) {
            val c = source[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' || c == '<' -> break
            }
            i++
        }
        return if (i < source.length && source[i] == '>') i else -1
    }

    private fun attributes(text: String): Map<String, String> = ATTRIBUTE.findAll(text).associate { match ->
        val value = match.groups[DOUBLE_QUOTED]?.value ?: match.groups[SINGLE_QUOTED]?.value
            ?: match.groups[UNQUOTED]?.value ?: ""
        match.groupValues[1].lowercase() to Entities.decode(value)
    }

    private const val DOUBLE_QUOTED = 2
    private const val SINGLE_QUOTED = 3
    private const val UNQUOTED = 4
    private const val COMMENT_OPEN = "<!--"
    private const val COMMENT_CLOSE = "-->"
    private const val CDATA_OPEN = "<![CDATA["
    private const val CDATA_CLOSE = "]]>"
    private val ATTRIBUTE = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
}

/** XML's five entities, numeric references and the HTML entities EPUBs actually use. */
internal object Entities {
    private val REFERENCE = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")
    private const val HEX_RADIX = 16

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "bull" to "•", "middot" to "·",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”",
        "bdquo" to "„", "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›",
        "shy" to "", "zwnj" to "‌", "zwj" to "‍", "lrm" to "", "rlm" to "",
        "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "times" to "×",
        "divide" to "÷", "plusmn" to "±", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "sect" to "§", "para" to "¶", "dagger" to "†", "Dagger" to "‡", "prime" to "′",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "aacute" to "á", "agrave" to "à",
        "acirc" to "â", "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "ccedil" to "ç",
        "ntilde" to "ñ", "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "szlig" to "ß",
        "pound" to "£", "euro" to "€", "yen" to "¥", "cent" to "¢", "rupee" to "₹",
    )

    /** Unknown names and invalid numbers stay as written. */
    fun decode(text: String): String = if ('&' !in text) {
        text
    } else {
        REFERENCE.replace(text) { match ->
            val ref = match.groupValues[1]
            val code = when {
                ref.startsWith("#x") || ref.startsWith("#X") -> ref.substring(2).toIntOrNull(HEX_RADIX)
                ref.startsWith("#") -> ref.substring(1).toIntOrNull()
                else -> null
            }
            when {
                code != null && Character.isValidCodePoint(code) -> String(Character.toChars(code))
                code == null -> NAMED[ref] ?: match.value
                else -> match.value
            }
        }
    }
}
