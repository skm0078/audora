// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/MarkdownParser.kt
// Purpose: Reads Markdown (notes, READMEs, text copied from chat assistants) without its markup symbols (ADR-066).

package io.github.skm0078.audora.playback.text

/**
 * Markdown files have no signature, and many are labelled text/plain, so a text file is read as Markdown when its
 * content shows it ([looksLikeMarkdown]): a `#` heading, a code fence or a table, or several links or bold spans.
 * Then `#` headings are headings (level 1 starts a chapter, as do `===` underlined titles), `-`, `*`, `+` and
 * `1.` lines are list items, `>` quote marks are dropped, a table row is its cells joined by "; ", and links,
 * images, emphasis, inline code and HTML tags leave only their words (a link's text, an image's alt text). A code
 * block is read as one paragraph, so nothing in the file goes unread. Front matter (`---` … `---` on top) and link
 * definitions are left out.
 */
object MarkdownParser {
    private val ATX = Regex("""^ {0,3}(#{1,6})\s+(.*?)(\s+#+)?\s*$""")
    private val FENCE = Regex("""^ {0,3}(```|~~~)""")
    private val SETEXT_1 = Regex("""^ {0,3}=+\s*$""")
    private val SETEXT_2 = Regex("""^ {0,3}-+\s*$""")
    private val RULE = Regex("""^ {0,3}([-*_])(\s*\1){2,}\s*$""")
    private val LIST = Regex("""^\s*([-*+•]|\d{1,9}[.)])\s+(.*)$""")
    private val QUOTE = Regex("""^ {0,3}>\s?""")
    private val TABLE_ROW = Regex("""^\s*\|.*\|\s*$""")
    private val TABLE_SEPARATOR = Regex("""^[\s|:\-]+$""")
    private val LINK_DEFINITION = Regex("""^ {0,3}\[[^\]]+]:\s*\S+.*$""")
    private val HTML_COMMENT = Regex("""<!--.*?-->""")
    private val LINK = Regex("""\[([^\]\n]+)]\([^)\n]+\)""")
    private val BOLD = Regex("""\*\*[^*\n]+\*\*""")

    private val INLINE = listOf(
        Regex("""!\[([^\]\n]*)]\([^)\n]*\)""") to "$1",
        LINK to "$1",
        Regex("""\[([^\]\n]+)]\[[^\]\n]*]""") to "$1",
        Regex("""<(https?://[^>\s]+)>""") to "$1",
        Regex("""`+([^`\n]+?)`+""") to "$1",
        Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""") to "$2",
        Regex("""(?<![\w*])\*(?=\S)(.+?)(?<=\S)\*(?![\w*])""") to "$1",
        Regex("""(?<![\w_])_(?=\S)(.+?)(?<=\S)_(?![\w_])""") to "$1",
        Regex("""~~(.+?)~~""") to "$1",
        Regex("""</?[A-Za-z][^>\n]*>""") to "",
        Regex("""\s*\{#[\w-]+}\s*$""") to "",
        Regex("""\\([\\`*_{}\[\]()#+\-.!|>~])""") to "$1",
    )
    private const val MARKDOWN_SCORE = 2

    /** Whether [text] is Markdown rather than prose that happens to hold a "*" or a "#". */
    fun looksLikeMarkdown(text: String): Boolean {
        var score = 0
        for (line in text.lineSequence()) {
            score += when {
                ATX.matches(line) || FENCE.containsMatchIn(line) -> MARKDOWN_SCORE
                TABLE_ROW.matches(line) && TABLE_SEPARATOR.matches(line) && '-' in line -> MARKDOWN_SCORE
                else -> LINK.findAll(line).count() + BOLD.findAll(line).count()
            }
            if (score >= MARKDOWN_SCORE) return true
        }
        return false
    }

    fun parse(text: String): TextDocument {
        val walk = Walk()
        withoutFrontMatter(text.replace("\r\n", "\n").replace('\r', '\n').lines()).forEach(walk::line)
        return walk.finish()
    }

    /** Inline markup removed: what is left is what a listener should hear. */
    internal fun plain(text: String): String = INLINE.fold(HTML_COMMENT.replace(text, "")) { acc, (pattern, with) ->
        pattern.replace(acc, with)
    }

    private fun withoutFrontMatter(lines: List<String>): List<String> {
        if (lines.firstOrNull()?.trim() != "---") return lines
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
        return if (end < 0) lines else lines.drop(end + 2)
    }

    private class Walk {
        private val builder = ChapterBuilder()
        private val paragraph = ArrayList<String>()
        private var paragraphKind = BlockKind.BODY
        private var code: StringBuilder? = null

        @Suppress("CyclomaticComplexMethod") // One flat table of Markdown's line kinds, in CommonMark's order.
        fun line(raw: String) {
            val open = code
            if (open != null) {
                if (FENCE.containsMatchIn(raw)) closeCode(open) else open.append(raw.trim()).append(' ')
                return
            }
            val line = raw.replace(QUOTE, "").replace(QUOTE, "")
            when {
                FENCE.containsMatchIn(line) -> {
                    flush()
                    code = StringBuilder()
                }

                line.isBlank() || LINK_DEFINITION.matches(line) -> flush()

                ATX.matches(line) -> heading(line)

                SETEXT_1.matches(line) && paragraph.isNotEmpty() -> underlined(chapter = true)

                SETEXT_2.matches(line) && paragraph.isNotEmpty() && paragraphKind == BlockKind.BODY ->
                    underlined(chapter = false)

                RULE.matches(line) -> flush()

                TABLE_ROW.matches(line) -> tableRow(line)

                LIST.matches(line) -> listItem(line)

                else -> paragraph += line.trim()
            }
        }

        fun finish(): TextDocument {
            code?.let(::closeCode)
            flush()
            return builder.build(documentTitle = null)
        }

        private fun heading(line: String) {
            flush()
            val match = ATX.matchEntire(line) ?: return
            val text = plain(match.groupValues[2])
            if (match.groupValues[1].length == 1) {
                builder.chapterHeading(text)
            } else {
                builder.block(BlockKind.HEADING, text)
            }
        }

        private fun underlined(chapter: Boolean) {
            val text = plain(paragraph.joinToString(" "))
            paragraph.clear()
            if (chapter) builder.chapterHeading(text) else builder.block(BlockKind.HEADING, text)
        }

        private fun tableRow(line: String) {
            flush()
            if (TABLE_SEPARATOR.matches(line)) return
            val cells = line.trim().removePrefix("|").removeSuffix("|").split('|').map { plain(it.trim()) }
            builder.block(BlockKind.BODY, cells.filter { it.isNotEmpty() }.joinToString("; "))
        }

        private fun listItem(line: String) {
            flush()
            paragraphKind = BlockKind.LIST_ITEM
            paragraph += LIST.matchEntire(line)?.groupValues?.get(2).orEmpty().removeTaskBox()
        }

        private fun closeCode(open: StringBuilder) {
            builder.block(BlockKind.BODY, open.toString())
            code = null
        }

        private fun flush() {
            if (paragraph.isNotEmpty()) builder.block(paragraphKind, plain(paragraph.joinToString(" ")))
            paragraph.clear()
            paragraphKind = BlockKind.BODY
        }

        /** "[ ] Buy milk" and "[x] Done" are task boxes; the box is not read. */
        private fun String.removeTaskBox(): String = replace(TASK_BOX, "")

        private companion object {
            val TASK_BOX = Regex("""^\[[ xX]]\s+""")
        }
    }
}
