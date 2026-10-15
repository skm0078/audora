// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/AssetDirectoryInstaller.kt
// Purpose: Copies a folder out of the APK's assets once per content version (espeak-ng reads only real files).

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Read-only view of a folder tree; Android's AssetManager in the app, a fake in tests. */
interface AssetTree {
    /** Child names of [path], or an empty list when [path] is a file (AssetManager cannot tell the two apart). */
    fun list(path: String): List<String>

    fun open(path: String): InputStream
}

/**
 * Installs `assets/<source>` into [target], tagged with a version marker.
 *
 * sherpa-onnx reads model weights straight from the APK, but espeak-ng opens its data with plain file I/O, so
 * espeak-ng-data has to exist on disk. The copy goes to a sibling temp folder first and is renamed into place, so a
 * process killed mid-copy leaves no half-installed folder that looks complete.
 */
class AssetDirectoryInstaller(private val assets: AssetTree) {
    /** Returns [target], copying it first unless its marker already says [version]. */
    fun install(source: String, target: File, version: String): Outcome<File, String> {
        val marker = File(target, MARKER)
        if (marker.isFile && marker.readText() == version) return Outcome.Success(target)

        val staging = File(target.parentFile, "${target.name}$STAGING_SUFFIX")
        return try {
            staging.deleteRecursively()
            val files = copyTree(source, staging)
            File(staging, MARKER).writeText(version)
            if (target.exists() && !target.deleteRecursively()) throw IOException("Cannot replace $target")
            if (!staging.renameTo(target)) throw IOException("Cannot move $staging to $target")
            AudoraLog.i(TAG, "Installed assets/$source ($files files, version ${version.take(VERSION_LOG_CHARS)})")
            Outcome.Success(target)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Copying assets/$source failed", e)
            staging.deleteRecursively()
            Outcome.Failure("Copying assets/$source failed: ${e.message}")
        }
    }

    /** Copies [path] into [into] and returns the number of files copied. */
    private fun copyTree(path: String, into: File): Int {
        val children = assets.list(path)
        if (children.isEmpty()) {
            into.parentFile?.mkdirs()
            assets.open(path).use { input -> into.outputStream().use { input.copyTo(it) } }
            return 1
        }
        if (!into.isDirectory && !into.mkdirs()) throw IOException("Cannot create $into")
        return children.sumOf { copyTree("$path/$it", File(into, it)) }
    }

    private companion object {
        const val TAG = "Speech.Assets"
        const val MARKER = ".installed-version"
        const val STAGING_SUFFIX = ".partial"
        const val VERSION_LOG_CHARS = 12
    }
}
