// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/TextLayerCheck.kt
// Purpose: Per-book OCR-sample cross-check: an Indic book's text layer is trusted only if OCR agrees (ADR-011 §3).

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import java.text.Normalizer
import java.util.Locale

/**
 * Recognizes the text of one rendered page. The Tesseract implementation is P3-05 (models from the P4-01 model
 * manager); until then a book has no OCR and its Indic text layer stays [TextLayerVerdict.UNVERIFIED].
 */
fun interface PageOcr {
    fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<String, OcrError>
}

sealed interface OcrError {
    /** The language's model is not downloaded yet; the check is tried again later. */
    data class ModelMissing(val lang: Lang) : OcrError

    data class Failed(val detail: String, val cause: Throwable? = null) : OcrError
}

/** What the book's OCR sample said about its Indic text layer. */
enum class TextLayerVerdict {
    /** No Indic page with enough text has been read yet. */
    UNCHECKED,

    /** No OCR available (P3-05 not installed, model missing, OCR failed): the text layer is read as is. */
    UNVERIFIED,

    /** The sample's error rate was within `QualityConfig.maxSampleCer`. */
    TRUSTED,

    /** The sample disagreed: every Indic page of the book goes to OCR. */
    REJECTED,
}

/**
 * The languages to OCR a page in: its main Indic language (more Devanagari or more Bengali letters), plus English
 * when at least `minIndicShare` of its letters are not Indic (bilingual pages).
 */
fun ocrLangs(text: CharSequence, config: QualityConfig): Set<Lang> {
    var devanagari = 0
    var bengali = 0
    var other = 0
    for (c in text) {
        when {
            !IndicChars.isIndicLetter(c) -> if (c.isLetter()) other++
            c.code < BENGALI_BLOCK -> devanagari++
            else -> bengali++
        }
    }
    val langs = LinkedHashSet<Lang>()
    if (devanagari + bengali > 0) langs.add(if (bengali > devanagari) Lang.BN else Lang.HI)
    if (other > 0 && other >= config.minIndicShare * (devanagari + bengali + other)) langs.add(Lang.EN)
    return langs
}

private const val BENGALI_BLOCK = 0x0980

/** A page's final route: the scorer's, unless the book's cross-check overrode it. */
data class PageRouting(val route: PageRoute, val reason: RouteReason, val verdict: TextLayerVerdict)

/**
 * Build Spec §5 step 2, ADR-011 §3: office exports often map conjunct glyphs to wrong letters, which no text-only
 * test can prove wrong. So the first Indic page of a book with enough text is OCR'd and compared with the text the
 * pipeline extracted for it (after the `/ActualText` rebuild and the Indic repair), line by line
 * ([CharacterErrorRate.ofLines]): character error rate ≤ `maxSampleCer` → the book's text layer is trusted;
 * otherwise every Indic page goes to OCR. While trusted, one Indic page in `recheckEveryPages` is checked again; a
 * failed re-check rejects the book from then on.
 *
 * One per book, used under the book's lock (not thread-safe on its own). Logs verdicts with [logContext] and the
 * page number, never text.
 */
class TextLayerCheck(private val config: QualityConfig, private val ocr: PageOcr?, private val logContext: String) {
    var verdict: TextLayerVerdict = TextLayerVerdict.UNCHECKED
        private set

    /** The error rate of the last sample, for diagnostics; null before the first check. */
    var lastSampleCer: Float? = null
        private set

    private var indicPagesSinceCheck = 0

    /**
     * The route of a page the scorer judged [quality]. [layerText] is the page's extracted text in layout order,
     * clutter included (OCR reads headers too); it is only built when this page becomes a sample.
     */
    fun route(pageIndex: Int, quality: PageQuality, langs: Set<Lang>, layerText: () -> String): PageRouting {
        if (quality.route != PageRoute.TEXT_LAYER || !quality.isIndic) {
            return PageRouting(quality.route, quality.reason, verdict)
        }
        indicPagesSinceCheck++
        if (isDue() && quality.signals.letters >= config.minSampleLetters) check(pageIndex, langs, layerText())
        return if (verdict == TextLayerVerdict.REJECTED) {
            PageRouting(PageRoute.OCR, RouteReason.SAMPLE_MISMATCH, verdict)
        } else {
            PageRouting(PageRoute.TEXT_LAYER, RouteReason.TEXT_LAYER_OK, verdict)
        }
    }

    private fun isDue(): Boolean = when (verdict) {
        TextLayerVerdict.UNCHECKED -> true

        TextLayerVerdict.REJECTED -> false

        // An unverified book is tried again as often as a trusted one: the model may have arrived since.
        TextLayerVerdict.UNVERIFIED -> ocr != null && indicPagesSinceCheck >= config.recheckEveryPages

        TextLayerVerdict.TRUSTED -> indicPagesSinceCheck >= config.recheckEveryPages
    }

    private fun check(pageIndex: Int, langs: Set<Lang>, layerText: String) {
        indicPagesSinceCheck = 0
        if (ocr == null) {
            verdict = TextLayerVerdict.UNVERIFIED
            AudoraLog.i(TAG, "Indic text layer unverified (no OCR yet), $logContext page=$pageIndex")
            return
        }
        when (val result = ocr.recognize(pageIndex, langs)) {
            is Outcome.Success -> judge(pageIndex, CharacterErrorRate.ofLines(reference = result.value, layerText))

            is Outcome.Failure -> {
                // A failed sample never rejects a book: it keeps a verdict it already had, or stays unverified.
                if (verdict == TextLayerVerdict.UNCHECKED) verdict = TextLayerVerdict.UNVERIFIED
                AudoraLog.w(TAG, "OCR sample failed, $logContext page=$pageIndex langs=$langs error=${result.error}")
            }
        }
    }

    private fun judge(pageIndex: Int, cer: Float) {
        lastSampleCer = cer
        verdict = if (cer <= config.maxSampleCer) TextLayerVerdict.TRUSTED else TextLayerVerdict.REJECTED
        AudoraLog.i(
            TAG,
            "OCR sample $verdict, $logContext page=$pageIndex cer=${String.format(Locale.ROOT, "%.3f", cer)}",
        )
    }

    private companion object {
        const val TAG = "Audora.TextLayerCheck"
    }
}

/**
 * Character error rate as S2 measured it: edit distance over the reference's length, on text folded to what a
 * voice reads. Both sides are NFC-normalized and lose whitespace, punctuation and symbols, ZWJ/ZWNJ and soft
 * hyphens: OCR spacing and punctuation are unreliable and do not change the spoken words much.
 */
object CharacterErrorRate {
    /** Plain CER: Levenshtein distance between the folded texts over the folded reference's length. */
    fun of(reference: String, hypothesis: String): Float {
        val ref = fold(reference)
        val hyp = fold(hypothesis)
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0f else 1f
        return distance(ref, hyp, freeEnds = false).toFloat() / ref.length
    }

    /**
     * CER of each reference line against its best-matching stretch of [hypothesis], summed over the lines. The
     * cross-check's measure: OCR and the text layer can order blocks differently (a footer first or last) and the
     * layer may hold text OCR skipped, neither of which is a wrong word; every letter OCR read that the layer does
     * not hold the same way still counts.
     */
    fun ofLines(reference: String, hypothesis: String): Float {
        val hyp = fold(hypothesis)
        var errors = 0
        var length = 0
        for (line in reference.lineSequence()) {
            val ref = fold(line)
            if (ref.isEmpty()) continue
            errors += distance(ref, hyp, freeEnds = true)
            length += ref.length
        }
        if (length == 0) return if (hyp.isEmpty()) 0f else 1f
        return errors.toFloat() / length
    }

    /** Keeps letters, combining marks and digits; NFC first so precomposed and decomposed forms compare equal. */
    internal fun fold(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        for (c in nfc) {
            val type = Character.getType(c)
            val kept = c.isLetterOrDigit() || type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            if (kept) out.append(c)
        }
        return out.toString()
    }

    /**
     * Two-row Levenshtein distance from [a] to [b], O(|a|·|b|) time and O(|b|) memory (a page is a few thousand
     * characters). With [freeEnds], [a] may match any stretch of [b] (semi-global alignment).
     */
    private fun distance(a: String, b: String, freeEnds: Boolean): Int {
        var previous = IntArray(b.length + 1) { if (freeEnds) 0 else it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return if (freeEnds) previous.min() else previous[b.length]
    }
}
