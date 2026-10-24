// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/RtfParser.kt
// Purpose: Reads a Rich Text Format (.rtf) file's text, title and footnotes for the plain-text reader (ADR-066).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.logging.AudoraLog
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/** An RTF file's [text] (paragraphs separated by blank lines), its [title] and its footnotes, in order. */
data class RtfText(val title: String?, val text: String, val notes: List<String>)

/**
 * A small RTF reader: groups, control words and symbols, `\'hh` bytes in the file's code page (`\ansicpg`, Windows-1252
 * by default) and `\uN` Unicode characters (Hindi and Bengali RTF files use these) with their `\ucN` fallbacks
 * skipped. Font, colour and style tables, pictures, objects, headers, footers, field instructions and every `\*`
 * destination are skipped; the `\info` title is kept; footnotes are collected apart so they are read last. Table
 * cells are joined by "; " and each row is a paragraph. The text then goes through [PlainTextParser], so headings,
 * list items and chapters are found as in a text file.
 */
object RtfParser {
    private const val TAG = "TextBooks"
    private const val HEX_RADIX = 16

    /** `\'` before the two hex digits of a byte. */
    private const val HEX_PREFIX = 2
    private const val HEX_DIGITS = 2
    private const val UNICODE_WRAP = 65_536
    private val DEFAULT_CHARSET: Charset = Charset.forName("windows-1252")

    private val SKIPPED = setOf(
        "fonttbl", "colortbl", "stylesheet", "listtable", "listoverridetable", "rsidtbl", "revtbl", "filetbl",
        "pict", "object", "header", "headerl", "headerr", "headerf", "footer", "footerl", "footerr", "footerf",
        "fldinst", "xe", "tc", "bkmkstart", "bkmkend", "themedata", "colorschememapping", "datastore",
        "latentstyles", "generator", "info", "pgdsctbl", "xmlnstbl", "nonshppict", "mmathpr", "annotation",
        "atnid", "atnauthor", "comment",
    )

    private enum class Target { TEXT, SKIP, TITLE, NOTE }

    private data class Group(val target: Target, val unicodeSkip: Int)

    fun parse(source: String): RtfText = Reader(source).read()

    private class Reader(private val source: String) {
        private val stack = ArrayList<Group>()
        private var group = Group(Target.TEXT, unicodeSkip = 1)
        private var charset = DEFAULT_CHARSET
        private val text = StringBuilder()
        private val title = StringBuilder()
        private val notes = ArrayList<String>()
        private val note = StringBuilder()
        private var pendingSkip = 0
        private var i = 0

        /** The next control word starts a destination group: `{\*\name` or the group's first word. */
        private var groupStart = false

        fun read(): RtfText {
            while (i < source.length) {
                val c = source[i]
                when (c) {
                    '{' -> {
                        stack += group
                        groupStart = true
                        i++
                    }

                    '}' -> closeGroup()

                    '\\' -> control()

                    '\r', '\n' -> i++

                    else -> {
                        append(c.toString())
                        i++
                    }
                }
            }
            return RtfText(
                title.toString().trim().takeIf { it.isNotEmpty() },
                text.toString(),
                notes,
            )
        }

        private fun closeGroup() {
            if (group.target == Target.NOTE && stack.lastOrNull()?.target != Target.NOTE) {
                note.toString().trim().takeIf { it.isNotEmpty() }?.let(notes::add)
                note.clear()
            }
            group = stack.removeLastOrNull() ?: group
            groupStart = false
            i++
        }

        private fun control() {
            val next = source.getOrNull(i + 1) ?: run {
                i++
                return
            }
            when {
                next.isLetter() -> controlWord()

                next == '\'' -> hexByte()

                else -> {
                    i += 2
                    controlSymbol(next)
                }
            }
        }

        private fun controlSymbol(symbol: Char) {
            when (symbol) {
                '\\', '{', '}' -> append(symbol.toString())
                '~' -> append(" ")
                '_' -> append("-")
                '*' -> group = group.copy(target = Target.SKIP)
                '\r', '\n' -> paragraph()
            }
        }

        /** `\'hh`: one byte in the file's code page; it also counts as one fallback character after `\uN`. */
        private fun hexByte() {
            val start = minOf(i + HEX_PREFIX, source.length)
            val hex = source.substring(start, minOf(start + HEX_DIGITS, source.length))
            i = start + hex.length
            val byte = hex.toIntOrNull(HEX_RADIX) ?: return
            append(String(byteArrayOf(byte.toByte()), charset))
        }

        private fun controlWord() {
            val start = i + 1
            var end = start
            while (end < source.length && source[end].isLetter()) end++
            val word = source.substring(start, end).lowercase()
            var paramEnd = end
            if (paramEnd < source.length && source[paramEnd] == '-') paramEnd++
            while (paramEnd < source.length && source[paramEnd].isDigit()) paramEnd++
            val param = source.substring(end, paramEnd).toIntOrNull()
            i = if (paramEnd < source.length && source[paramEnd] == ' ') paramEnd + 1 else paramEnd
            val first = groupStart
            groupStart = false
            word(word, param, first)
        }

        @Suppress("CyclomaticComplexMethod") // One flat table of RTF's control words.
        private fun word(word: String, param: Int?, groupFirst: Boolean) {
            when (word) {
                "par", "sect", "page", "row" -> paragraph()
                "line" -> append("\n")
                "tab" -> append(" ")
                "cell" -> append("; ")
                "u" -> unicode(param)
                "uc" -> group = group.copy(unicodeSkip = param ?: 1)
                "ansicpg" -> charset = codePage(param)
                "emdash" -> append("—")
                "endash" -> append("–")
                "lquote" -> append("‘")
                "rquote" -> append("’")
                "ldblquote" -> append("“")
                "rdblquote" -> append("”")
                "bullet" -> append("•")
                "emspace", "enspace", "qmspace" -> append(" ")
                "bin" -> i = minOf(source.length, i + (param ?: 0))
                "title" -> if (groupFirst) group = group.copy(target = Target.TITLE)
                "footnote" -> if (groupFirst) group = group.copy(target = Target.NOTE)
                else -> if (groupFirst && word in SKIPPED) group = group.copy(target = Target.SKIP)
            }
        }

        /** `\uN` is a signed 16-bit code unit; the next `\ucN` characters are a fallback for old readers. */
        private fun unicode(param: Int?) {
            val unit = param ?: return
            append((if (unit < 0) unit + UNICODE_WRAP else unit).toChar().toString())
            pendingSkip = group.unicodeSkip
        }

        private fun paragraph() {
            when (group.target) {
                Target.TEXT -> text.append("\n\n")
                Target.NOTE -> note.append(' ')
                else -> Unit
            }
        }

        private fun append(piece: String) {
            if (pendingSkip > 0) {
                pendingSkip--
                return
            }
            when (group.target) {
                Target.TEXT -> text.append(piece)
                Target.TITLE -> title.append(piece)
                Target.NOTE -> note.append(piece)
                Target.SKIP -> Unit
            }
        }
    }

    private fun codePage(page: Int?): Charset = page?.let { charsetOrNull("windows-$it") ?: charsetOrNull("cp$it") }
        ?: DEFAULT_CHARSET

    private fun charsetOrNull(name: String): Charset? = try {
        Charset.forName(name)
    } catch (e: IllegalCharsetNameException) {
        AudoraLog.d(TAG, "RTF code page unknown, read as Windows-1252: ${e.javaClass.simpleName}")
        null
    } catch (e: UnsupportedCharsetException) {
        AudoraLog.d(TAG, "RTF code page unknown, read as Windows-1252: ${e.javaClass.simpleName}")
        null
    }
}
