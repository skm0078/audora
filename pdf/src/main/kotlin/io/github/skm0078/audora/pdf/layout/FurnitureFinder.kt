// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/FurnitureFinder.kt
// Purpose: Tags figure and table captions and display equations in a cleaned layout (RI-7a, ADR-035).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.FurnitureConfig
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.quality.BundledWordModels
import io.github.skm0078.audora.pdf.quality.WordModels

/**
 * Tags, never drops (ADR-035): lines and their text stay as they are; only paragraph kinds and boundaries change.
 *
 * - **Captions.** A paragraph opening with a caption label ([CAPTION_LABEL]: "Fig. 2.", "Table 3", "चित्र 4:",
 *   "সারণি ১") is a [ParagraphKind.CAPTION] when the label is set apart: followed by a delimiter, nothing or a
 *   capital ("Fig. 4 Regions of…", never "Fig. 4 shows…"), or the line is a heading, bold or small (see
 *   `FurnitureConfig.captionSizeRatio`). A label opening a later line, right after a full stop, starts a caption
 *   that layout joined to the paragraph above. A paragraph holding only the label ("Table 2" set bold above its
 *   caption) takes the next paragraph of its block with it when that one is set smaller than the body.
 * - **Display equations** ([EquationLines]). Each display line of a body paragraph becomes its own
 *   [ParagraphKind.EQUATION] paragraph. Unit boundaries stay where they were, so saved positions still resume;
 *   playback reads a run of equation units as one equation (RI-7b).
 */
internal class FurnitureFinder(private val config: FurnitureConfig, models: WordModels = BundledWordModels) {
    private val equations = EquationLines(config, models)

    fun tag(page: PageText, layout: PageLayout): PageLayout {
        val blocks = ArrayList<TextBlock>(layout.blocks.size)
        for (block in layout.blocks) {
            val paragraphs = tag(page, block, layout.bodySize).toMutableList()
            // A continuation carries on body text only (ReadingTextBuilder): not into a caption or an equation.
            val first = paragraphs.firstOrNull()
            val previous = blocks.lastOrNull()?.paragraphs?.lastOrNull()
            if (first != null && first.continuesPrevious && previous?.kind != ParagraphKind.BODY) {
                paragraphs[0] = first.copy(continuesPrevious = false)
            }
            blocks.add(block.copy(paragraphs = paragraphs))
        }
        return layout.copy(blocks = blocks)
    }

    private fun tag(page: PageText, block: TextBlock, bodySize: Float): List<Paragraph> {
        val lines = block.paragraphs.filterNot { it.kind.isTable }.flatMap { it.lines }
        val texts = lines.associateWith { line ->
            line.spans.joinToString(" ") { page.text.substring(it.start, it.end) }
        }
        val display = equations.of(lines, texts, bodySize)
        val out = ArrayList<Paragraph>()
        var labelOnly = false
        for (paragraph in block.paragraphs) {
            if (paragraph.kind.isTable) {
                out.add(paragraph)
                labelOnly = false
                continue
            }
            val captionAt = captionStart(paragraph, paragraph.lines.map(texts::getValue), bodySize)
            when {
                captionAt != null -> {
                    val head = paragraph.lines.subList(0, captionAt)
                    if (head.isNotEmpty()) out.addAll(splitEquations(paragraph.copy(lines = head), display))
                    val caption = paragraph.lines.subList(captionAt, paragraph.lines.size)
                    out.add(Paragraph(caption, ParagraphKind.CAPTION))
                    labelOnly = caption.size == 1 && texts.getValue(caption.first()).isLabelOnly()
                }

                labelOnly && isSmall(paragraph.lines.first(), bodySize) -> {
                    out.add(paragraph.copy(kind = ParagraphKind.CAPTION, continuesPrevious = false))
                    labelOnly = false
                }

                else -> {
                    out.addAll(splitEquations(paragraph, display))
                    labelOnly = false
                }
            }
        }
        return out
    }

    /**
     * The line a caption starts on: the first, when it opens with a set-apart label; or a later line opening with a
     * set-apart label right after a full stop ("… that format takes. / Table 1. Useful specifiers"). Null if none.
     */
    private fun captionStart(paragraph: Paragraph, texts: List<String>, bodySize: Float): Int? {
        val first = paragraph.lines.first()
        val styled = paragraph.kind == ParagraphKind.HEADING || first.bold || isSmall(first, bodySize)
        val opens = isSetApart(texts.first()) || (styled && CAPTION_LABEL.containsMatchIn(texts.first()))
        return when {
            paragraph.kind == ParagraphKind.LIST_ITEM -> null
            opens -> 0
            else -> (1 until texts.size).firstOrNull { i -> endsSentence(texts[i - 1]) && isSetApart(texts[i]) }
        }
    }

    /** [text] opens with a label followed by a delimiter ("Fig. 2.", "Table 3:"), nothing, or a capital. */
    private fun isSetApart(text: String): Boolean {
        val after = CAPTION_LABEL.find(text)?.let { text.substring(it.range.last + 1).trimStart() }
        return after != null && (after.isEmpty() || after.first() in DELIMITERS || after.first().isUpperCase())
    }

    private fun endsSentence(text: String): Boolean = text.trimEnd().lastOrNull()?.let { it in SENTENCE_ENDS } == true

    private fun String.isLabelOnly(): Boolean =
        CAPTION_LABEL.find(this)?.let { substring(it.range.last + 1).trim().all { c -> c in DELIMITERS } } == true

    private fun isSmall(line: TextLine, bodySize: Float): Boolean =
        bodySize > 0f && line.size > 0f && line.size <= bodySize * config.captionSizeRatio

    /** The paragraph with each display line cut out as an equation paragraph; the body text between stays together. */
    private fun splitEquations(paragraph: Paragraph, display: Set<TextLine>): List<Paragraph> {
        val equation = paragraph.lines.map { it in display }
        if (paragraph.kind != ParagraphKind.BODY || equation.none { it }) return listOf(paragraph)
        val out = ArrayList<Paragraph>()
        var start = 0
        for (i in 1..paragraph.lines.size) {
            if (i == paragraph.lines.size || equation[i] || equation[start]) {
                val kind = if (equation[start]) ParagraphKind.EQUATION else ParagraphKind.BODY
                val continues = start == 0 && paragraph.continuesPrevious && kind == ParagraphKind.BODY
                out.add(Paragraph(paragraph.lines.subList(start, i), kind, continues))
                start = i
            }
        }
        return out
    }

    internal companion object {
        /** [text] opens with a caption label ("Table 2", "চিত্র ৪"), set apart or not. */
        fun opensWithCaptionLabel(text: String): Boolean = CAPTION_LABEL.containsMatchIn(text)

        private val CAPTION_LABEL = Regex(
            "^\\s*(?:Fig(?:ure)?s?\\.?|FIG(?:URE)?S?\\.?|Tables?|TABLES?|Charts?|Schemes?|Plates?|Exhibits?|" +
                "चित्र|तालिका|सारणी|आकृति|চিত্র|সারণি|সারণী|ছক)\\s*" +
                "(?:\\p{Nd}+|[IVXLC]+\\b|[A-Z]\\b)(?:[.\\-–]\\p{Nd}+|[a-z]\\b)*",
        )
        private const val DELIMITERS = ".:|—–-।"
        private const val SENTENCE_ENDS = ".!?।"
    }
}
