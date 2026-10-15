// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumRuntime.kt
// Purpose: Loads PDFium (PdfiumAndroidKt) and our JNI library once per process.

package io.github.skm0078.audora.pdf.pdfium

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.legere.pdfiumandroid.core.unlocked.PdfiumCoreU
import io.legere.pdfiumandroid.core.util.wrapLock

/**
 * We use PdfiumAndroidKt's "unlocked" core because it exposes the native page pointers our JNI library needs
 * (the public wrapper keeps them internal). Every PDFium call still goes through [wrapLock], the library's
 * own global lock, so we never race the public API (a future page renderer) on PDFium's global state.
 *
 * One [PdfiumCoreU] per process: its constructor also sets PdfiumAndroidKt's global config.
 */
internal object PdfiumRuntime {
    private const val TAG = "Audora.Pdfium"
    private const val EXT_LIBRARY = "audora_pdfium_ext"

    private val core: Outcome<PdfiumCoreU, PdfOpenError> by lazy(::load)

    /** Blocks on first use while the native libraries load; call off the main thread. */
    fun core(): Outcome<PdfiumCoreU, PdfOpenError> = core

    @Suppress("TooGenericExceptionCaught") // Native loading fails with Errors and RuntimeExceptions alike.
    private fun load(): Outcome<PdfiumCoreU, PdfOpenError> = try {
        val pdfium = PdfiumCoreU()
        System.loadLibrary(EXT_LIBRARY)
        val missing = wrapLock { PdfiumExtNative.nativeInit() }
        if (missing == null) {
            AudoraLog.i(TAG, "PDFium and $EXT_LIBRARY loaded")
            Outcome.Success(pdfium)
        } else {
            AudoraLog.e(TAG, "$EXT_LIBRARY could not bind to libpdfium.so: $missing")
            Outcome.Failure(PdfOpenError.EngineUnavailable(missing))
        }
    } catch (e: Throwable) {
        AudoraLog.e(TAG, "Native PDF libraries failed to load", e)
        Outcome.Failure(PdfOpenError.EngineUnavailable(e.toString(), e))
    }
}
