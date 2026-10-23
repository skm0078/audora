// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/PlainTextParser.kt
// Purpose: Reads a text file or shared text into paragraphs, headings, list items and chapters (items 42 and 43).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Blank lines separate paragraphs; the lines of a paragraph are joined (text files wrap at 70–80 columns). A
 * one-line paragraph that is short and ends without punctuation is a heading; one that also names a chapter
 * ("Chapter 3", "Part II", "अध्याय ५", "অধ্যায় ৩", a lone Roman numeral) starts a new chapter. A paragraph starting
 * with a bullet or a number label is a list item, each of its lines a separate item.
 */
class PlainTextParser(private val config: TextBookConfig = TextBookConfig()) {
    fun parse(text: String): TextDocument {
        val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split(BLANK_LINES)
        val chapters = ArrayList<TextChapter>()
        var title: String? = null
        var blocks = ArrayList<TextBlock>()
        for (paragraph in paragraphs) {
            val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val heading = headingOf(lines)
            if (heading != null && CHAPTER.matches(heading) && blocks.isNotEmpty()) {
                chapters += TextChapter(title, blocks)
                blocks = ArrayList()
            }
            if (heading != null && CHAPTER.matches(heading)) title = heading
            blocks += blocksOf(lines, heading)
        }
        if (blocks.isNotEmpty()) chapters += TextChapter(title, blocks)
        return TextDocument(title = null, language = null, chapters = chapters)
    }

    /** Decodes a text file: a byte-order mark first, then strict UTF-8, else Windows-1252 (old English texts). */
    fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(UTF8_BOM) -> String(bytes, UTF8_BOM.size, bytes.size - UTF8_BOM.size, Charsets.UTF_8)
        UTF16_BOMS.any { bytes.startsWith(it) } -> String(bytes, Charsets.UTF_16)
        else -> strictUtf8(bytes) ?: String(bytes, WINDOWS_1252)
    }.removePrefix("")

    private fun headingOf(lines: List<String>): String? {
        val line = lines.singleOrNull() ?: return null
        val short = line.length <= config.headingMaxChars && line.any { it.isLetterOrDigit() }
        return line.takeIf {
            short && (CHAPTER.matches(line) || line.last() !in CLOSING) &&
                !LIST_MARK.containsMatchIn(line)
        }
    }

    private fun blocksOf(lines: List<String>, heading: String?): List<TextBlock> = when {
        heading != null -> listOf(TextBlock(BlockKind.HEADING, heading))

        LIST_MARK.containsMatchIn(lines.first()) && lines.all { LIST_MARK.containsMatchIn(it) } ->
            lines.map { TextBlock(BlockKind.LIST_ITEM, it.replace(BULLET, "").trim()) }

        else -> listOf(TextBlock(BlockKind.BODY, lines.joinToString(" ").replace(SPACES, " ")))
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        AudoraLog.d(TAG, "Text file is not UTF-8, read as Windows-1252: ${e.javaClass.simpleName}")
        null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val TAG = "TextBooks"
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_BOMS = listOf(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        val WINDOWS_1252: Charset = Charset.forName("windows-1252")
        val BLANK_LINES = Regex("\n[ \t ]*\n")
        val SPACES = Regex("\\s+")
        const val CLOSING = ".!?।॥,;:\"'”’)…"

        /** A bullet, or a label such as "1." or "a)" followed by a space. */
        val LIST_MARK = Regex("""^([•●◦▪‣\-*–]|\(?[0-9a-zA-Z]{1,3}[.)])\s+""")
        val BULLET = Regex("""^[•●◦▪‣\-*–]\s+""")

        /** English needs a number after the word ("Part of it" is not a chapter); Hindi and Bengali the word. */
        val CHAPTER = Regex(
            """^((chapter|part|book|section)\s+([0-9]+|[ivxlc]+)([\s.:].*)?|prologue|epilogue|""" +
                """(अध्याय|भाग|परिच्छेद|অধ্যায়|পরিচ্ছেদ|খণ্ড)(\s.*)?|(?-i:[IVXLC]{1,7})\.?)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
