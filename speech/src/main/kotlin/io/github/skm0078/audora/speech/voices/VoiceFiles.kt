// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceFiles.kt
// Purpose: Where downloaded and imported voices live on disk, how they are listed, swapped in and deleted.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceManifest
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.File
import java.io.IOException
import java.util.Properties

/** Where an installed voice came from: the catalog (can be updated) or the user's own folder. */
enum class VoiceOrigin { CATALOG, CUSTOM }

data class InstalledVoice(val spec: SherpaVoiceSpec, val origin: VoiceOrigin, val dir: File, val sizeBytes: Long)

/**
 * Layout under [root] (the app's no-backup folder: models are large and re-downloadable):
 * - `installed/<id>/` one folder per voice: its files plus `voice.properties` ([SherpaVoiceManifest] keys + origin).
 * - `installed/<id>.partial/` a voice being unpacked or imported; `installed/<id>.old/` the copy it replaces.
 * - `downloads/<id>-<sha12>.zip.partial` a download in progress, named by its pin so a new pin never resumes an
 *   old file.
 *
 * A folder only counts as installed once it is renamed into place with its manifest, so a process killed mid-install
 * leaves nothing that looks complete. [recover] tidies what such a kill leaves behind.
 */
class VoiceFiles(val root: File) {
    val installedRoot = File(root, "installed")
    val downloadsRoot = File(root, "downloads")

    fun voiceDir(id: String): File = File(installedRoot, id)

    fun stagingDir(id: String): File = File(installedRoot, id + STAGING_SUFFIX)

    fun partialDownload(voice: CatalogVoice): File =
        File(downloadsRoot, "${voice.id}-${voice.sha256.take(PIN_PREFIX_CHARS)}$PARTIAL_DOWNLOAD_SUFFIX")

    /** Every installed voice with a readable manifest. Folders with a broken manifest are logged and skipped. */
    fun installed(): List<InstalledVoice> = installedRoot.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.endsWith(STAGING_SUFFIX) && !it.name.endsWith(OLD_SUFFIX) }
        .mapNotNull { readInstalled(it) }
        .sortedBy { it.spec.id }

    /**
     * Finishes or undoes what a killed swap left: a voice whose folder is missing gets its `.old` copy back; other
     * `.old` and `.partial` folders are deleted. Call once before listing at startup.
     */
    fun recover() {
        installedRoot.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            when {
                dir.name.endsWith(STAGING_SUFFIX) -> deleteTree(dir)

                dir.name.endsWith(OLD_SUFFIX) -> {
                    val live = voiceDir(dir.name.removeSuffix(OLD_SUFFIX))
                    if (!live.exists() && dir.renameTo(live)) {
                        AudoraLog.w(TAG, "Restored voice=${live.name} after an interrupted update")
                    } else {
                        deleteTree(dir)
                    }
                }
            }
        }
    }

    /**
     * Writes [spec]'s manifest into [staging] and moves it to `installed/<id>`, replacing an older copy. The old
     * folder is renamed aside first, so at every moment either the old or the new voice is complete on disk.
     */
    fun commit(staging: File, spec: SherpaVoiceSpec, origin: VoiceOrigin): Outcome<InstalledVoice, String> {
        val target = voiceDir(spec.id)
        val old = File(installedRoot, spec.id + OLD_SUFFIX)
        return try {
            InstalledVoiceManifest.write(File(staging, SherpaVoiceManifest.FILE_NAME), spec, origin)
            deleteTree(old)
            if (target.exists() && !target.renameTo(old)) throw IOException("cannot move $target aside")
            if (!staging.renameTo(target)) {
                old.renameTo(target)
                throw IOException("cannot move $staging into place")
            }
            deleteTree(old)
            Outcome.Success(InstalledVoice(spec, origin, target, treeSize(target)))
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Installing voice=${spec.id} failed", e)
            Outcome.Failure("could not install ${spec.id}: ${e.message}")
        }
    }

    /** Deletes an installed voice and any download of it. Renamed aside first so a half-deleted folder never lists. */
    fun delete(id: String): Outcome<Unit, String> {
        downloadsRoot.listFiles().orEmpty().filter { it.name.startsWith("$id-") }.forEach { it.delete() }
        val target = voiceDir(id)
        val doomed = File(installedRoot, id + OLD_SUFFIX)
        deleteTree(doomed)
        val moved = !target.exists() || target.renameTo(doomed)
        deleteTree(doomed)
        if (moved) AudoraLog.i(TAG, "Deleted voice=$id")
        return if (moved) Outcome.Success(Unit) else Outcome.Failure("could not delete $id: cannot move its folder")
    }

    private fun readInstalled(dir: File): InstalledVoice? {
        val parsed = try {
            InstalledVoiceManifest.read(File(dir, SherpaVoiceManifest.FILE_NAME))
        } catch (e: IOException) {
            Outcome.Failure("no readable manifest: ${e.message}")
        }
        val voice = (parsed as? Outcome.Success)?.value?.takeIf { (spec, _) -> spec.id == dir.name }
        if (voice == null) {
            val problem = (parsed as? Outcome.Failure)?.error ?: "manifest names another voice"
            AudoraLog.w(TAG, "Voice folder ${dir.name} skipped: $problem")
        }
        return voice?.let { (spec, origin) -> InstalledVoice(spec, origin, dir, treeSize(dir)) }
    }

    private fun deleteTree(dir: File) {
        if (dir.exists() && !dir.deleteRecursively()) AudoraLog.w(TAG, "Could not fully delete ${dir.name}")
    }

    companion object {
        const val STAGING_SUFFIX = ".partial"
        const val OLD_SUFFIX = ".old"
        const val PARTIAL_DOWNLOAD_SUFFIX = ".zip.partial"
        private const val PIN_PREFIX_CHARS = 12
        private const val TAG = "Speech.VoiceFiles"

        fun treeSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/** `voice.properties` of an installed voice: [SherpaVoiceManifest]'s keys plus where the voice came from. */
internal object InstalledVoiceManifest {
    private const val KEY_ORIGIN = "origin"

    @Throws(IOException::class)
    fun read(file: File): Outcome<Pair<SherpaVoiceSpec, VoiceOrigin>, String> {
        val properties = file.reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
        val origin = VoiceOrigin.entries.firstOrNull { it.name == properties.getProperty(KEY_ORIGIN) }
            ?: VoiceOrigin.CUSTOM
        return SherpaVoiceManifest.parse(properties).map { it to origin }
    }

    /** Writes through a temp file and a rename, so a kill never leaves a half-written manifest. */
    @Throws(IOException::class)
    fun write(file: File, spec: SherpaVoiceSpec, origin: VoiceOrigin) {
        val properties = Properties()
        values(spec, origin).forEach { (key, value) -> if (value != null) properties.setProperty(key, value) }
        val temp = File(file.path + ".tmp")
        temp.writer(Charsets.UTF_8).use { properties.store(it, null) }
        if (!temp.renameTo(file)) throw IOException("cannot write $file")
    }

    private fun values(spec: SherpaVoiceSpec, origin: VoiceOrigin): List<Pair<String, String?>> = listOf(
        "id" to spec.id,
        "label" to spec.label,
        "lang" to spec.lang.bcp47Tag,
        "family" to spec.family.name.lowercase(),
        "model" to spec.model,
        "tokens" to spec.tokens,
        "voices" to spec.voices,
        "dataDir" to spec.dataDir,
        "speakerId" to spec.speakerId.toString(),
        "licence" to spec.licence,
        "contentVersion" to spec.contentVersion,
        KEY_ORIGIN to origin.name,
    )
}
