// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/ActualTextRebuilder.kt
// Purpose: Plans the /ActualText rebuild so PDFium's fake spaces stop splitting Indic words (ADR-011 §2).

package io.github.skm0078.audora.pdf.extract

import io.github.skm0078.audora.core.config.ExtractionConfig

/** What happened to a page's `/ActualText` spans. */
enum class ActualTextRebuild {
    /** The page has no `/ActualText`; PDFium's text is used as is. */
    NOT_NEEDED,

    /** Every text object was matched to PDFium's characters and the spans were planned. */
    APPLIED,

    /** Some text object could not be matched; PDFium's text is used as is (never worse than before). */
    ALIGNMENT_FAILED,
}

/**
 * One `/ActualText` span mapped onto PDFium's characters.
 *
 * @property firstChar the first PDFium char of the span; the span text is emitted there.
 * @property lastChar the last char the span owns, including PDFium's generated space right after it.
 * @property spaceAfter a real space glyph follows the span, so one space is kept after it.
 */
data class ActualTextSpan(val firstChar: Int, val lastChar: Int, val text: String, val spaceAfter: Boolean)

/** The rebuild plan: for each PDFium char, the index of the span that owns it, or [NO_SPAN]. */
class ActualTextPlan(
    val status: ActualTextRebuild,
    val spans: List<ActualTextSpan>,
    private val spanOfChar: IntArray?,
) {
    fun spanOf(charIndex: Int): Int = spanOfChar?.get(charIndex) ?: NO_SPAN

    companion object {
        const val NO_SPAN = -1

        fun unchanged(status: ActualTextRebuild) = ActualTextPlan(status, emptyList(), null)
    }
}

/**
 * Chromium/Skia (and InDesign) wrap each Indic cluster in `/Span <</ActualText (…)>> BDC … EMC` and draw a real
 * space glyph between words. PDFium ignores the space glyph and generates a space after every span, so "मानव"
 * comes out "मा नव". This keeps PDFium's character stream and replaces each span's characters with its
 * `/ActualText`, keeping the generated space after a span only when a space glyph follows (S2 §5).
 *
 * Alignment: PDFium does not say which text object a character came from, so each object's text is found in
 * the page's character stream with whitespace ignored (PDFium invents and drops spaces; the other characters
 * are stable). Matching moves forward in content order but tolerates PDFium's small reorders within a cluster
 * (a Bengali reph drawn after its consonant). If any object cannot be matched, nothing is changed.
 */
class ActualTextRebuilder(private val config: ExtractionConfig = ExtractionConfig()) {
    fun plan(page: RawPage): ActualTextPlan {
        val hasActualText = page.textObjects.any { it.actualText != null }
        val objectChars = if (hasActualText) alignObjects(page) else null
        return when {
            !hasActualText -> ActualTextPlan.unchanged(ActualTextRebuild.NOT_NEEDED)
            objectChars == null -> ActualTextPlan.unchanged(ActualTextRebuild.ALIGNMENT_FAILED)
            else -> planSpans(page, objectChars)
        }
    }

    /** For each text object, the PDFium char indices its non-space characters matched; null if any failed. */
    private fun alignObjects(page: RawPage): Array<IntArray?>? {
        val skeleton = Skeleton.of(page.chars)
        val used = BooleanArray(skeleton.text.length)
        val result = arrayOfNulls<IntArray>(page.textObjects.size)
        var cursor = 0
        page.textObjects.forEachIndexed { k, obj ->
            val needle = obj.text.filterNot { it.isWhitespace() }
            if (needle.isEmpty()) return@forEachIndexed
            val from = maxOf(0, cursor - config.actualTextSearchBehind)
            val until = cursor + config.actualTextSearchAhead
            val at = findUnused(skeleton.text, needle, from, until, used) ?: return null
            for (p in at until at + needle.length) used[p] = true
            result[k] = IntArray(needle.length) { skeleton.charIndex[at + it] }.distinct().toIntArray()
            cursor = maxOf(cursor, at + needle.length)
        }
        return result
    }

    private fun planSpans(page: RawPage, objectChars: Array<IntArray?>): ActualTextPlan {
        val objects = page.textObjects
        val spanOfChar = IntArray(page.chars.size) { ActualTextPlan.NO_SPAN }
        val spans = ArrayList<ActualTextSpan>()
        var k = 0
        while (k < objects.size) {
            val end = spanEnd(objects, k)
            val actual = objects[k].actualText
            val owned = (k until end).flatMap { objectChars[it]?.asList().orEmpty() }
            if (actual != null && owned.isNotEmpty()) {
                val spaceAfter = isSpaceGlyph(objects.getOrNull(end))
                spans.add(claim(page.chars, owned, ActualTextSpanDraft(spans.size, actual, spaceAfter), spanOfChar))
            }
            k = end
        }
        return ActualTextPlan(ActualTextRebuild.APPLIED, spans, spanOfChar)
    }

    /** End (exclusive) of the run of objects sharing object [start]'s span; [start] + 1 if it has none. */
    private fun spanEnd(objects: List<RawTextObject>, start: Int): Int {
        val key = objects[start].spanKey
        var end = start + 1
        if (objects[start].actualText == null) return end
        while (end < objects.size && objects[end].actualText != null && objects[end].spanKey == key) end++
        return end
    }

    /** Marks the span's chars, and PDFium's generated spaces inside it and right after it, in [spanOfChar]. */
    private fun claim(
        chars: RawChars,
        owned: List<Int>,
        draft: ActualTextSpanDraft,
        spanOfChar: IntArray,
    ): ActualTextSpan {
        val first = owned.min()
        var last = owned.max()
        owned.forEach { spanOfChar[it] = draft.index }
        while (last + 1 < chars.size && chars.isGeneratedSpace(last + 1)) last++
        for (q in first..last) {
            if (spanOfChar[q] == ActualTextPlan.NO_SPAN && chars.isGeneratedSpace(q)) spanOfChar[q] = draft.index
        }
        return ActualTextSpan(first, last, draft.text, draft.spaceAfter)
    }

    private class ActualTextSpanDraft(val index: Int, val text: String, val spaceAfter: Boolean)

    private fun isSpaceGlyph(next: RawTextObject?): Boolean {
        if (next == null || next.actualText != null) return false
        val blankAndZeroWidth = next.text.isBlank() && next.width <= config.zeroWidthObjectPt
        return blankAndZeroWidth || next.text.firstOrNull()?.isWhitespace() == true
    }

    private fun findUnused(haystack: String, needle: String, from: Int, until: Int, used: BooleanArray): Int? {
        val lastStart = minOf(until, haystack.length - needle.length)
        var at = from
        while (at <= lastStart) {
            if (haystack.regionMatches(at, needle, 0, needle.length) &&
                (at until at + needle.length).none { used[it] }
            ) {
                return at
            }
            at++
        }
        return null
    }

    /** The page's characters with whitespace removed, and for each UTF-16 unit the PDFium char it came from. */
    private class Skeleton(val text: String, val charIndex: IntArray) {
        companion object {
            fun of(chars: RawChars): Skeleton {
                val text = StringBuilder(chars.size)
                val index = IntArrayBuilder(chars.size)
                for (i in 0 until chars.size) {
                    val cp = chars.codePoint(i)
                    if (isSpace(cp)) continue
                    val before = text.length
                    text.appendCodePoint(safeCodePoint(cp))
                    repeat(text.length - before) { index.add(i) }
                }
                return Skeleton(text.toString(), index.toArray())
            }
        }
    }
}

internal fun RawChars.isGeneratedSpace(i: Int): Boolean = isGenerated(i) && isSpace(codePoint(i))

internal fun isSpace(codePoint: Int): Boolean = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

/** PDFium can report 0 or lone surrogates for unmapped glyphs; those become U+FFFD so strings stay valid. */
internal fun safeCodePoint(codePoint: Int): Int {
    val valid = codePoint > 0 && Character.isValidCodePoint(codePoint) &&
        Character.getType(codePoint) != Character.SURROGATE.toInt()
    return if (valid) codePoint else REPLACEMENT_CHARACTER
}

private const val REPLACEMENT_CHARACTER = 0xFFFD
