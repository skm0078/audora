// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/ocr/TessdataFiles.kt
// Purpose: Where the downloaded OCR models live, which of them are installed, and how one is swapped in or removed.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException

/**
 * Layout under [root] (the app's no-backup folder: models are large and re-downloadable):
 * - `tessdata/<code>.traineddata` the models, where Tesseract wants them (its data path is [root]);
 * - `tessdata/<code>.traineddata.sha256` the pin the model was verified against; a model whose marker is missing or
 *   names another pin does not count as installed, so a new pin downloads again and a kill between the rename and
 *   the marker leaves nothing that looks complete;
 * - `downloads/<id>-<sha12>.partial` a download in progress, named by its pin so a new pin never resumes an old file.
 */
class TessdataFiles(val root: File) {
    val tessdataDir = File(root, TESSDATA)
    private val downloadsDir = File(root, DOWNLOADS)

    fun partialDownload(model: OcrModel): File =
        File(downloadsDir, "${model.id}-${model.sha256.take(PIN_PREFIX_CHARS)}$PARTIAL_SUFFIX")

    /** The catalog's languages whose model is on disk and verified against the current pin. */
    fun installed(catalog: TessdataCatalog): Set<Lang> =
        catalog.models.filter { isInstalled(it) }.mapTo(LinkedHashSet()) { it.lang }

    fun isInstalled(model: OcrModel): Boolean {
        val file = File(tessdataDir, model.fileName)
        val marker = markerOf(file)
        return file.isFile && file.length() == model.sizeBytes && marker.isFile &&
            runCatching { marker.readText(Charsets.UTF_8).trim() }.getOrNull() == model.sha256
    }

    /** Bytes of the installed models. */
    fun installedBytes(catalog: TessdataCatalog): Long = catalog.models.filter { isInstalled(it) }.sumOf { it.sizeBytes }

    /** Moves a verified download into place, replacing an older copy; the marker is written last. */
    fun install(verified: File, model: OcrModel): Outcome<Unit, String> {
        val target = File(tessdataDir, model.fileName)
        val marker = markerOf(target)
        return try {
            tessdataDir.mkdirs()
            marker.delete()
            if (target.exists() && !target.delete()) throw IOException("cannot replace ${target.name}")
            if (!verified.renameTo(target)) throw IOException("cannot move ${verified.name} into place")
            val tempMarker = File(marker.path + TEMP_SUFFIX)
            tempMarker.writeText(model.sha256, Charsets.UTF_8)
            if (!tempMarker.renameTo(marker)) throw IOException("cannot write ${marker.name}")
            AudoraLog.i(TAG, "Installed OCR model ${model.fileName} (${model.sizeBytes} bytes)")
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Installing OCR model ${model.fileName} failed", e)
            Outcome.Failure("could not install ${model.fileName}: ${e.message}")
        }
    }

    /** Removes every model and download. */
    fun deleteAll(): Outcome<Unit, String> {
        val clean = listOf(tessdataDir, downloadsDir).all { !it.exists() || it.deleteRecursively() }
        AudoraLog.i(TAG, if (clean) "Deleted the OCR models" else "Could not fully delete the OCR models")
        return if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every OCR model file")
    }

    private fun markerOf(model: File): File = File(model.path + MARKER_SUFFIX)

    private companion object {
        const val TAG = "Speech.OcrFiles"
        const val TESSDATA = "tessdata"
        const val DOWNLOADS = "downloads"
        const val PARTIAL_SUFFIX = ".partial"
        const val MARKER_SUFFIX = ".sha256"
        const val TEMP_SUFFIX = ".tmp"
        const val PIN_PREFIX_CHARS = 12
    }
}
