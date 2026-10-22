// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/ocr/BookOcr.kt
// Purpose: Wires :pdf's OCR (Tesseract) to :speech's downloaded models for every book the player or reader opens.

package io.github.skm0078.audora.playback.ocr

import android.content.Context
import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.ocr.OcrCache
import io.github.skm0078.audora.pdf.ocr.RenderedPageOcr
import io.github.skm0078.audora.pdf.ocr.tesseract.TessdataSource
import io.github.skm0078.audora.pdf.ocr.tesseract.TesseractEngine
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.speech.ocr.OcrModels
import java.io.File
import java.security.MessageDigest

/**
 * One Tesseract engine and one OCR text cache per process, shared by the playback service and the reader screen, so a
 * page the player recognised is shown by the reader without a second run (and the other way round). The models come
 * from the OCR model manager; until they are downloaded, OCR pages fall back to their text layer and say why.
 */
object BookOcr {
    internal const val CACHE_DIR = "ocr"
    private const val BOOK_KEY_CHARS = 16

    @Volatile
    private var shared: Shared? = null

    /**
     * The OCR for one book. [documentUri] keys the text cache (the same book opened by the reader and the player
     * shares it); [logContext] names the book in logs, never its text.
     */
    fun factory(
        context: Context,
        documentUri: String,
        logContext: String,
        config: OcrConfig = OcrConfig(),
    ): PageOcrFactory {
        val (engine, cache) = sharedFor(context, config)
        return RenderedPageOcr.factory(engine, cache, bookKey(documentUri), logContext, config)
    }

    private fun sharedFor(context: Context, config: OcrConfig): Shared = shared ?: synchronized(this) {
        shared ?: run {
            val app = context.applicationContext
            Shared(
                TesseractEngine(ModelsTessdata(OcrModels.get(app))),
                OcrCache(File(app.cacheDir, CACHE_DIR), config.cacheMaxBytes),
            ).also { shared = it }
        }
    }

    /** A short hash of the document's address: stable across opens, and no title or path in a file name. */
    internal fun bookKey(documentUri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(documentUri.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(BOOK_KEY_CHARS)

    private data class Shared(val engine: TesseractEngine, val cache: OcrCache)
}

/** The model manager seen as Tesseract's data folder. */
internal class ModelsTessdata(private val models: OcrModels) : TessdataSource {
    override val revision: String get() = models.catalog?.revision ?: NO_CATALOG

    override fun installed(): Set<Lang> = models.installed()

    override fun dataRoot(langs: Set<Lang>): Outcome<File, OcrError> {
        val installed = models.installed()
        if (installed.isEmpty()) return Outcome.Failure(OcrError.NoModels)
        val missing = langs.firstOrNull { it !in installed }
        return if (missing ==
            null
        ) {
            Outcome.Success(models.dataRoot)
        } else {
            Outcome.Failure(OcrError.ModelMissing(missing))
        }
    }

    private companion object {
        const val NO_CATALOG = "none"
    }
}
