// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/PageQuality.kt
// Purpose: Scores a page's text layer and routes it: text layer, legacy-font converter or OCR (Build Spec §5 step 2).

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.extract.PageText

/** Where a page's words come from (ADR-011 §3). */
enum class PageRoute {
    /** The PDF's own text, after the `/ActualText` rebuild and the Indic reorder pass. */
    TEXT_LAYER,

    /** Pre-Unicode Hindi/Bengali font: the legacy-font converter (P3-04). */
    LEGACY_FONT,

    /** Text layer unusable or untrusted: OCR of the rendered page (P3-05). */
    OCR,
}

/** Why a page got its route; logged and shown in diagnostics. */
enum class RouteReason {
    TEXT_LAYER_OK,
    TOO_LITTLE_TEXT,
    HIDDEN_TEXT_LAYER,
    LEGACY_FONT,
    UNICODE_MAP_ERRORS,
    GARBAGE,
    INVALID_INDIC,

    /** The book's OCR sample disagreed with its text layer, so every Indic page is OCR'd ([TextLayerCheck]). */
    SAMPLE_MISMATCH,
}

/**
 * The scorer's measurements. Rates are shares of [letters] (Indic rates of [indicLetters]) except the two PDFium
 * rates, which are shares of the page's extracted (non-generated) characters.
 */
data class PageSignals(
    val letters: Int,
    val indicLetters: Int,
    val unicodeMapErrorRate: Float,
    val invisibleShare: Float,
    val garbageRate: Float,
    val invalidIndicRate: Float,
    val legacyShare: Float,
)

/** A page's route as the scorer sees it alone; [TextLayerCheck] may still move an Indic page to OCR. */
data class PageQuality(
    val route: PageRoute,
    val reason: RouteReason,
    val signals: PageSignals,
    val indicOrder: IndicOrderEvidence,
    val isIndic: Boolean,
)

/**
 * Build Spec §5 step 2, ADR-011 §3, ADR-021. Pure and per page; runs once per extracted page (PdfBook caches the
 * result with the page's layout). Checks, in order: almost no letters (a scan) → OCR; mostly invisible text (a
 * scanner's OCR layer) → OCR; legacy-font runs → converter; Unicode-map errors, junk characters or impossible Indic
 * sequences above their thresholds → OCR. Junk and impossible sequences are counted after the page's own Indic
 * repair ([IndicReorder]), so a visual-order page the reorder pass fixes stays on its text layer.
 */
class PageQualityScorer(private val config: QualityConfig = QualityConfig()) {
    fun score(page: PageText): PageQuality {
        val text = page.text
        val evidence = IndicOrderEvidence.of(text)
        val signals = signalsOf(page, evidence)
        val isIndic = signals.letters > 0 && signals.indicLetters >= config.minIndicShare * signals.letters
        val reason = reasonOf(signals, isIndic)
        val route = when (reason) {
            RouteReason.TEXT_LAYER_OK -> PageRoute.TEXT_LAYER
            RouteReason.LEGACY_FONT -> PageRoute.LEGACY_FONT
            else -> PageRoute.OCR
        }
        return PageQuality(route, reason, signals, evidence, isIndic)
    }

    private fun reasonOf(s: PageSignals, isIndic: Boolean): RouteReason = when {
        s.letters < config.minPageLetters -> RouteReason.TOO_LITTLE_TEXT
        s.invisibleShare > config.maxInvisibleShare -> RouteReason.HIDDEN_TEXT_LAYER
        s.legacyShare >= config.minLegacyShare -> RouteReason.LEGACY_FONT
        s.unicodeMapErrorRate > config.maxUnicodeMapErrorRate -> RouteReason.UNICODE_MAP_ERRORS
        s.garbageRate > config.maxGarbageRate -> RouteReason.GARBAGE
        isIndic && s.invalidIndicRate > config.maxInvalidIndicRate -> RouteReason.INVALID_INDIC
        else -> RouteReason.TEXT_LAYER_OK
    }

    private fun signalsOf(page: PageText, evidence: IndicOrderEvidence): PageSignals {
        val text = page.text
        var letters = 0
        var indic = 0
        for (c in text) {
            if (IndicChars.isIndicLetter(c)) {
                indic++
                letters++
            } else if (c.isLetter()) {
                letters++
            }
        }
        val extracted = (page.stats.pdfiumChars - page.stats.generatedChars).coerceAtLeast(1)
        // Junk and impossible sequences are counted on the text as it will be read, after the Indic repair.
        val recovered = IndicReorder.apply(MappedText.plain(text), indicOrderOf(evidence, emptyList(), config)).text
        return PageSignals(
            letters = letters,
            indicLetters = indic,
            unicodeMapErrorRate = page.stats.unicodeMapErrors.toFloat() / extracted,
            invisibleShare = page.stats.invisibleChars.toFloat() / extracted,
            garbageRate = share(TextDefects.garbageChars(recovered), letters),
            invalidIndicRate = share(TextDefects.invalidIndicChars(recovered), indic),
            legacyShare = share(legacyLetters(page), letters),
        )
    }

    /** Letters in font runs whose font is a known legacy font and whose letters are mostly ASCII. */
    private fun legacyLetters(page: PageText): Int = page.fontRuns.sumOf { run ->
        if (!isLegacyFont(run.fontName)) return@sumOf 0
        val runText = page.text.substring(run.start, run.end)
        val letters = runText.count { it.isLetter() }
        val ascii = runText.count { it.isLetter() && it.code < ASCII_END }
        if (letters > 0 && ascii >= config.minLegacyAsciiShare * letters) letters else 0
    }

    private fun isLegacyFont(fontName: String): Boolean {
        val name = fontName.substringAfter('+').lowercase()
        return config.legacyFontNames.any { name.contains(it) }
    }

    private fun share(count: Int, of: Int): Float = if (of == 0) 0f else count.toFloat() / of

    private companion object {
        const val ASCII_END = 0x80
    }
}

/** Character-level defects of a text layer. */
internal object TextDefects {
    private const val REPLACEMENT = '�'
    private const val LINE_END_HYPHEN = '\u0002'

    /** Punctuation that may legitimately sit between two Indic words without spaces ("হাউজিং-এর", "क/ख"). */
    private const val JOINERS = "-‐‑–—/.,:;'\"()‘’“”।॥"

    /**
     * Junk: U+FFFD, private-use glyphs, control characters (PDFium's line-end hyphen marker aside), and non-Indic
     * characters glued inside an Indic word ("घोषि0त", "हासिAक"), which is how broken font maps show up.
     */
    fun garbageChars(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val type = Character.getType(c)
            when {
                c == REPLACEMENT || type == Character.PRIVATE_USE.toInt() -> count++
                type == Character.CONTROL.toInt() && c != LINE_END_HYPHEN && !c.isWhitespace() -> count++
            }
            if (IndicChars.isIndicLetter(c) && i + 1 < text.length && isGlueable(text[i + 1])) {
                val end = gluedRunEnd(text, i + 1)
                if (end < text.length && IndicChars.isIndicLetter(text[end])) count += end - i - 1
                i = end
                continue
            }
            i++
        }
        return count
    }

    private fun isGlueable(c: Char) = c.code < ASCII_END && !c.isWhitespace() && c !in JOINERS && c.code >= ' '.code

    private fun gluedRunEnd(text: String, from: Int): Int {
        var k = from
        while (k < text.length && isGlueable(text[k])) k++
        return k
    }

    /**
     * Indic characters in a position Unicode text never has them: a vowel sign, virama or nukta with no consonant
     * before it, a modifier with no Indic letter before it. ZWJ and ZWNJ are looked through ("র‍্য").
     */
    fun invalidIndicChars(text: String): Int {
        var count = 0
        for (i in text.indices) {
            var b = i - 1
            if (b >= 0 && text[b] in JOINER_CONTROLS) b--
            val before = if (b < 0) IndicClass.NONE else IndicChars.classOf(text[b])
            val valid = when (IndicChars.classOf(text[i])) {
                IndicClass.VOWEL_SIGN, IndicClass.PREBASE_VOWEL, IndicClass.VIRAMA ->
                    before == IndicClass.CONSONANT || before == IndicClass.NUKTA

                IndicClass.NUKTA -> before == IndicClass.CONSONANT

                IndicClass.MODIFIER -> before != IndicClass.NONE && before != IndicClass.OTHER

                else -> true
            }
            if (!valid) count++
        }
        return count
    }

    private const val ASCII_END = 0x80
    private const val JOINER_CONTROLS = "\u200C\u200D"
}
