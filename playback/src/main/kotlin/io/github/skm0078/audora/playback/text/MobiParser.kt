// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/MobiParser.kt
// Purpose: Turns a Mobipocket or Kindle book's HTML into chapters (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

/**
 * A MOBI 6 book is one HTML stream whose chapters are split by `<mbp:pagebreak/>`; a KF8 (AZW3) book stores each
 * XHTML file as its skeleton followed by its fragments, so a new `<html` starts the next file. Each piece is read as
 * an EPUB chapter is ([XhtmlBlocks]) and named by its first heading. A PalmDOC (.pdb) is plain text.
 */
object MobiParser {
    private val CHAPTER_BREAK = Regex("""<mbp:pagebreak\b[^>]*>|(?=<html[\s>])""", RegexOption.IGNORE_CASE)

    fun parse(content: MobiFile.Content, plainText: (String) -> TextDocument): TextDocument {
        if (!content.isHtml) return plainText(content.markup).copy(title = content.title)
        val chapters = content.markup.split(CHAPTER_BREAK).mapNotNull { piece ->
            val blocks = XhtmlBlocks.blocks(MarkupScanner.scan(piece))
            if (blocks.isEmpty()) null else TextChapter(XhtmlBlocks.firstHeading(blocks), blocks)
        }
        return TextDocument(content.title, content.language, chapters)
    }
}
