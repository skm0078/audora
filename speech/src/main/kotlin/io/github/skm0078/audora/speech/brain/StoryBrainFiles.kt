// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainFiles.kt
// Purpose: Where the story brain's model lives on the phone (ADR-078), and how its downloaded parts become one file.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.sha256
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Layout under [root] (the no-backup folder: the model is re-downloadable):
 * - `<sha12>/model.gguf` the installed model, plus an `installed` marker written last, so a kill mid-install never
 *   leaves something that looks complete, and a new pin is a new folder;
 * - `downloads/joined.partial` the parts downloaded so far, end to end: its length says which part comes next;
 * - `downloads/<part id>.partial` the part downloading now.
 *
 * Each part is checked against its pin before it joins, and the joined file against the whole file's pin before it
 * is installed, so a wrong byte anywhere is caught. At most one part sits beside the joined file, so a download needs
 * the model's size plus one part free, not twice the model.
 */
class StoryBrainFiles(val root: File, private val model: StoryBrainModel, private val bufferBytes: Int) {
    val modelDir = File(root, model.sha256.take(PIN_PREFIX_CHARS))
    val modelFile = File(modelDir, MODEL_FILE)
    private val marker = File(modelDir, MARKER)
    private val downloads = File(root, DOWNLOADS)
    val joined = File(downloads, "joined$PARTIAL_SUFFIX")

    fun isInstalled(): Boolean = marker.isFile && modelFile.length() == model.sizeBytes

    fun partialDownload(part: StoryBrainModel.Part): File = File(downloads, "${part.id}$PARTIAL_SUFFIX")

    /**
     * The first part not yet joined, or null when all are. A joined file that does not end on a part's boundary is
     * not ours (or was cut by a kill mid-append): it is deleted and the download starts over.
     */
    fun nextPart(): StoryBrainModel.Part? {
        val have = joined.length()
        // Where each part starts, and the whole file's end: the joined file's length must be one of them.
        val starts = model.parts.runningFold(0L) { start, part -> start + part.sizeBytes }
        val next = starts.indexOf(have)
        if (next < 0) {
            AudoraLog.w(TAG, "Joined download is $have bytes, not on a part's end; starting over")
            joined.delete()
        }
        return if (next < 0) model.parts.first() else model.parts.getOrNull(next)
    }

    /** Bytes on disk toward the model: the joined parts and the part downloading now. */
    fun downloadedBytes(): Long = joined.length() + model.parts.sumOf { partialDownload(it).length() }

    /** Appends a verified part to the joined file (the first one is just renamed) and deletes it. */
    fun join(part: File): Outcome<Unit, String> = try {
        append(part)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Joining ${part.name} failed", e)
        Outcome.Failure("could not join ${part.name}: ${e.message}")
    } finally {
        part.delete()
    }

    private fun append(part: File) {
        when {
            joined.exists() ->
                FileOutputStream(joined, true).use { out -> part.inputStream().use { it.copyTo(out, bufferBytes) } }

            !part.renameTo(joined) -> throw IOException("cannot move ${part.name} into place")
        }
    }

    /** Checks the whole joined file against its pin and moves it into place; older pins are removed on success. */
    fun install(): Outcome<Unit, String> = try {
        val actual = sha256(joined, bufferBytes)
        if (joined.length() != model.sizeBytes || actual != model.sha256) {
            joined.delete()
            throw IOException("the joined file does not match its pin (SHA-256 $actual)")
        }
        modelDir.deleteRecursively()
        if (!modelDir.mkdirs() || !joined.renameTo(modelFile)) throw IOException("cannot move the model into place")
        marker.writeText(model.sha256, Charsets.UTF_8)
        root.listFiles { file -> file.isDirectory && file.name.length == PIN_PREFIX_CHARS && file != modelDir }
            ?.forEach { it.deleteRecursively() }
        AudoraLog.i(TAG, "Installed story brain ${model.id}")
        Outcome.Success(Unit)
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Installing story brain ${model.id} failed", e)
        Outcome.Failure("could not install ${model.id}: ${e.message}")
    }

    /** Every byte under [root] whatever the state: the model, downloads, leftovers of a failed one. */
    fun sizeOnDisk(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Removes the model and any download. */
    fun deleteAll(): Outcome<Unit, String> {
        val clean = !root.exists() || root.deleteRecursively()
        AudoraLog.i(TAG, if (clean) "Deleted story brain ${model.id}" else "Could not fully delete ${model.id}")
        return if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every file of ${model.id}")
    }

    private companion object {
        const val TAG = "Speech.BrainFiles"
        const val DOWNLOADS = "downloads"
        const val MODEL_FILE = "model.gguf"
        const val MARKER = "installed"
        const val PARTIAL_SUFFIX = ".partial"
        const val PIN_PREFIX_CHARS = 12
    }
}
