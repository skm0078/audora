// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/tesseract/TesseractEngine.kt
// Purpose: OcrEngine over Tesseract4Android with tessdata_best models (ADR-011, ADR-025).

package io.github.skm0078.audora.pdf.ocr.tesseract

import com.googlecode.tesseract.android.TessBaseAPI
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.ocr.Hocr
import io.github.skm0078.audora.pdf.ocr.OcrEngine
import io.github.skm0078.audora.pdf.ocr.RecognizedPage
import io.github.skm0078.audora.pdf.quality.OcrError
import java.io.File

/** Where the downloaded `*.traineddata` files are (the model manager in :speech owns them). */
interface TessdataSource {
    /** Names the model pins; part of the OCR cache key. */
    val revision: String

    fun installed(): Set<Lang>

    /** A folder holding `tessdata/<code>.traineddata` for every one of [langs], or which one is missing. */
    fun dataRoot(langs: Set<Lang>): Outcome<File, OcrError>
}

/**
 * One Tesseract instance per process, kept loaded between pages and reloaded only when the languages change
 * (loading the `best` models takes a moment and tens of megabytes). Tesseract is not thread-safe, so calls are
 * serialized; the player reads about a page ahead and the reader rarely asks at the same time.
 *
 * Settings: LSTM only (`tessdata_best` has no legacy model), automatic page segmentation (columns and paragraphs),
 * and the image's real resolution so Tesseract does not guess the text size.
 */
class TesseractEngine(private val models: TessdataSource) : OcrEngine {
    private val lock = Any()
    private var api: TessBaseAPI? = null
    private var loaded: String? = null

    override val cacheTag: String get() = "tess-${models.revision}"

    override fun installedLangs(): Set<Lang> = models.installed()

    override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> =
        models.dataRoot(langs).flatMap { root -> synchronized(lock) { recognizeLocked(root, image, langs) } }

    @Suppress("TooGenericExceptionCaught") // The JNI wrapper reports native failures as RuntimeExceptions.
    private fun recognizeLocked(root: File, image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
        val codes = codesOf(langs)
        return try {
            val tess = apiFor(root, codes)
                ?: return Outcome.Failure(OcrError.Failed("Tesseract could not load $codes from ${root.name}"))
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setVariable(DPI_VARIABLE, image.dpi.toString())
            tess.setImage(image.gray, image.width, image.height, GRAY_BYTES_PER_PIXEL, image.width)
            // hOCR rather than plain text: word boxes for the highlight and confidences for the orientation check.
            val hocr = tess.getHOCRText(0).orEmpty()
            tess.clear()
            Outcome.Success(Hocr.parse(hocr, image.width, image.height))
        } catch (e: RuntimeException) {
            AudoraLog.e(TAG, "Tesseract failed on page=${image.pageIndex} langs=$codes", e)
            release()
            Outcome.Failure(OcrError.Failed("Tesseract failed: $e", e))
        }
    }

    /** The loaded instance if it has [codes], else a fresh one; null when the models do not load. */
    private fun apiFor(root: File, codes: String): TessBaseAPI? {
        val key = "${root.path}|$codes"
        val current = api
        if (loaded == key && current != null) return current
        release()
        val tess = TessBaseAPI()
        val ok = try {
            tess.init(root.path, codes, TessBaseAPI.OEM_LSTM_ONLY)
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Tesseract data folder rejected for $codes", e)
            false
        }
        return if (ok) {
            AudoraLog.i(TAG, "Tesseract ${tess.getVersion()} loaded $codes")
            api = tess
            loaded = key
            tess
        } else {
            tess.recycle()
            null
        }
    }

    private fun release() {
        api?.recycle()
        api = null
        loaded = null
    }

    companion object {
        private const val TAG = "Audora.Tesseract"
        private const val DPI_VARIABLE = "user_defined_dpi"
        private const val GRAY_BYTES_PER_PIXEL = 1

        /** Tesseract's codes, main language first: an Indic script before English on a bilingual page. */
        fun codesOf(langs: Set<Lang>): String = langs.sortedBy { ORDER.indexOf(it) }.joinToString("+") { code(it) }

        fun code(lang: Lang): String = when (lang) {
            Lang.EN -> "eng"
            Lang.HI -> "hin"
            Lang.BN -> "ben"
        }

        private val ORDER = listOf(Lang.HI, Lang.BN, Lang.EN)
    }
}
