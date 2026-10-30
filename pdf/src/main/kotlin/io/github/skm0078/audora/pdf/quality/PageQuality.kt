// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/PageQuality.kt
// Purpose: Scores a page's text layer and routes it: text layer, legacy-font converter or OCR (Build Spec §5 step 2).

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.legacy.LegacyConverter
import io.github.skm0078.audora.pdf.legacy.LegacyRuns

/** Where a page's words come from (ADR-011 §3). */
enum class PageRoute {
    /** The PDF's own text, after the `/ActualText` rebuild and the Indic reorder pass. */
    TEXT_LAYER,

    /** Pre-Unicode Hindi/Bengali font: the PDF's own text through the legacy-font converter (P3-04, ADR-023). */
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

    /** Legacy-font text the converter has no mapping for: an unknown font variant or layout (ADR-023). */
    LEGACY_UNKNOWN_GLYPHS,
    UNICODE_MAP_ERRORS,
    GARBAGE,
    INVALID_INDIC,

    /**
     * Hindi or Bengali with almost no virama: the font maps its conjunct glyphs to nothing, so every conjunct is
     * lost ("সাহিত্য" extracts as "সাহিত"). The letters left are all possible, so only the missing sign shows it.
     */
    LOST_CONJUNCTS,

    /** The book's OCR sample disagreed with its text layer, so every Indic page is OCR'd ([TextLayerCheck]). */
    SAMPLE_MISMATCH,
}

/**
 * The scorer's measurements, on the page's text after legacy-font conversion. Rates are shares of [letters] (Indic
 * rates of [indicLetters]) except the two PDFium rates, which are shares of the page's extracted (non-generated)
 * characters, [legacyShare], a share of the extracted letters, and [legacyUnknownRate], a share of the letters in
 * legacy-font runs.
 */
data class PageSignals(
    val letters: Int,
    val indicLetters: Int,
    val unicodeMapErrorRate: Float,
    val invisibleShare: Float,
    val garbageRate: Float,
    val invalidIndicRate: Float,
    val viramaShare: Float,
    val legacyShare: Float,
    val legacyUnknownRate: Float = 0f,
)

/**
 * A page's route as the scorer sees it alone; [TextLayerCheck] may still move an Indic page to OCR. [legacyRuns] are
 * converted wherever the page is read from its text, whatever the route; [ocrLangs] are the languages of its text
 * after that conversion.
 */
data class PageQuality(
    val route: PageRoute,
    val reason: RouteReason,
    val signals: PageSignals,
    val indicOrder: IndicOrderEvidence,
    val isIndic: Boolean,
    val legacyRuns: LegacyRuns = LegacyRuns.NONE,
    val ocrLangs: Set<Lang> = emptySet(),
)

/**
 * Build Spec §5 step 2, ADR-011 §3, ADR-021, ADR-023. Pure and per page; runs once per extracted page (PdfBook caches
 * the result with the page's layout). Checks, in order: mostly legacy-font text with codes the converter does not
 * know → OCR; almost no letters (a scan) → OCR; mostly invisible text (a scanner's OCR layer) → OCR; Unicode-map
 * errors, junk characters or impossible Indic sequences above their thresholds → OCR; a long Indic page with almost
 * no virama (its conjuncts lost) → OCR; then mostly legacy-font text → converter, else text layer. Everything but the
 * PDFium rates is measured on the text as it will be read: legacy runs converted ([LegacyConverter]) and the page's
 * own Indic repair applied ([IndicReorder]), so a visual-order page the reorder pass fixes stays on its text layer
 * and a converted page is judged by its Unicode.
 */
class PageQualityScorer(private val config: QualityConfig = QualityConfig()) {
    fun score(page: PageText): PageQuality {
        val evidence = IndicOrderEvidence.of(page.text)
        val legacy = LegacyRuns.of(page, config)
        val conversion = LegacyConverter.convert(MappedText.plain(page.text), legacy)
        // Junk and impossible sequences are counted on the text as it will be read, after the Indic repair.
        val readable = IndicReorder.apply(conversion.text, indicOrderOf(evidence, emptyList(), config)).text
        val signals = signalsOf(page, readable, legacy, share(conversion.unknown, conversion.letters))
        val isIndic = signals.letters > 0 && signals.indicLetters >= config.minIndicShare * signals.letters
        val reason = reasonOf(signals, isIndic)
        val route = when (reason) {
            RouteReason.TEXT_LAYER_OK -> PageRoute.TEXT_LAYER
            RouteReason.LEGACY_FONT -> PageRoute.LEGACY_FONT
            else -> PageRoute.OCR
        }
        return PageQuality(route, reason, signals, evidence, isIndic, legacy, ocrLangs(readable, config))
    }

    private fun reasonOf(s: PageSignals, isIndic: Boolean): RouteReason {
        val legacy = s.legacyShare >= config.minLegacyShare
        return when {
            // First: unknown codes are dropped, so an unreadable legacy page would otherwise look nearly empty.
            legacy && s.legacyUnknownRate > config.maxLegacyUnknownRate -> RouteReason.LEGACY_UNKNOWN_GLYPHS

            s.letters < config.minPageLetters -> RouteReason.TOO_LITTLE_TEXT

            s.invisibleShare > config.maxInvisibleShare -> RouteReason.HIDDEN_TEXT_LAYER

            s.unicodeMapErrorRate > config.maxUnicodeMapErrorRate -> RouteReason.UNICODE_MAP_ERRORS

            s.garbageRate > config.maxGarbageRate -> RouteReason.GARBAGE

            isIndic && s.invalidIndicRate > config.maxInvalidIndicRate -> RouteReason.INVALID_INDIC

            isIndic && lostConjuncts(s) -> RouteReason.LOST_CONJUNCTS

            legacy -> RouteReason.LEGACY_FONT

            else -> RouteReason.TEXT_LAYER_OK
        }
    }

    /** Long enough to tell, and almost no virama: the font's conjunct glyphs map to nothing. */
    private fun lostConjuncts(s: PageSignals): Boolean =
        s.indicLetters >= config.minConjunctCheckLetters && s.viramaShare < config.minViramaShare

    private fun signalsOf(page: PageText, readable: String, legacy: LegacyRuns, legacyUnknownRate: Float): PageSignals {
        var letters = 0
        var indic = 0
        var viramas = 0
        for (c in readable) {
            if (IndicChars.isIndicLetter(c)) {
                indic++
                if (IndicChars.isVirama(c)) viramas++
                letters++
            } else if (c.isLetter()) {
                letters++
            }
        }
        val extracted = (page.stats.pdfiumChars - page.stats.generatedChars).coerceAtLeast(1)
        return PageSignals(
            letters = letters,
            indicLetters = indic,
            unicodeMapErrorRate = page.stats.unicodeMapErrors.toFloat() / extracted,
            invisibleShare = page.stats.invisibleChars.toFloat() / extracted,
            garbageRate = share(TextDefects.garbageChars(readable), letters),
            invalidIndicRate = share(TextDefects.invalidIndicChars(readable), indic),
            viramaShare = share(viramas, indic),
            legacyShare = if (legacy.isEmpty) 0f else share(legacy.letters, page.text.count { it.isLetter() }),
            legacyUnknownRate = legacyUnknownRate,
        )
    }

    private fun share(count: Int, of: Int): Float = if (of == 0) 0f else count.toFloat() / of
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
                IndicClass.VOWEL_SIGN, IndicClass.PREBASE_VOWEL -> before.takesSign()
                IndicClass.VIRAMA -> before.takesSign() || isBengaliVowelYaPhala(text, b, i)
                IndicClass.NUKTA -> before == IndicClass.CONSONANT
                IndicClass.MODIFIER -> before != IndicClass.NONE && before != IndicClass.OTHER
                else -> true
            }
            if (!valid) count++
        }
        return count
    }

    /** A vowel sign or virama is valid after a consonant, nukta included. */
    private fun IndicClass.takesSign(): Boolean = this == IndicClass.CONSONANT || this == IndicClass.NUKTA

    /** Bengali writes the English "a" of "acid", "academy" as অ or এ + virama + য ("অ্যাসিড"): valid there. */
    private fun isBengaliVowelYaPhala(text: String, vowel: Int, virama: Int): Boolean =
        vowel >= 0 && text[vowel] in BENGALI_YA_PHALA_VOWELS && text.getOrNull(virama + 1) == BENGALI_YA

    private const val ASCII_END = 0x80
    private const val JOINER_CONTROLS = "\u200C\u200D"
    private const val BENGALI_YA_PHALA_VOWELS = "\u0985\u098F"
    private const val BENGALI_YA = '\u09AF'
}
