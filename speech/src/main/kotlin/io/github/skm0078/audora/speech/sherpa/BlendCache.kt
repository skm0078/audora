// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/BlendCache.kt
// Purpose: The narrator blend's voices files (plan item 75): which voice gets one, where it is kept, built and cleared.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import kotlin.math.roundToInt

/** The listener's narrator blend: [blend] applied to the narrator [narrator] only. */
data class NarratorBlend(val narrator: VoiceId, val blend: StyleBlend)

/**
 * [blend] on the narrator's spec only, and only when it names another English speaker of that Kokoro model: a
 * Kokoro quote or heading voice, a Kokoro voice in another language, or a speaker id a restored setting got wrong
 * reads as installed.
 */
fun List<SherpaVoiceSpec>.withNarratorBlend(blend: NarratorBlend?): List<SherpaVoiceSpec> =
    if (blend == null) this else map { spec -> if (blend.fits(spec)) spec.copy(blend = blend.blend) else spec }

private fun NarratorBlend.fits(spec: SherpaVoiceSpec): Boolean =
    spec.voiceId == narrator && spec.family == SherpaFamily.KOKORO && blend.withSpeaker != spec.speakerId &&
        CastSpeakers.of(spec).any { it.speakerId == blend.withSpeaker }

/** Why the last blend could not be used: the bytes it needed free, or null when it was. Any thread. */
class BlendReport {
    @Volatile
    var neededBytes: Long? = null
        internal set
}

/**
 * Under [root], one folder per voice holding at most one blended copy, named by the speaker, the other speaker, the
 * percent and the voice's content version (a voice updated or imported again under the same id never reuses an old
 * copy). Builds, loads and clears run under one process-wide lock ([locked]), so the export and a playback service
 * never build the same copy at once, and a copy is never deleted between its write and its load. Each build writes a
 * temp file of its own; only finished copies are reused, and a temp file left by a killed build is removed by the
 * next one.
 */
class BlendCache(private val root: File, private val headroomBytes: Long, val report: BlendReport = BlendReport()) {
    /** The blended voices file for [spec] (which has a blend), built now if missing; null when it can't be. */
    fun voicesFor(spec: SherpaVoiceSpec, voiceDir: File): File? {
        val blend = spec.blend
        val voices = spec.voices?.let { File(voiceDir, it) }
        if (blend == null || voices == null) return null
        val folder = File(root, spec.id)
        val target = File(folder, nameOf(spec, blend))
        val needed = voices.length() + headroomBytes
        val free = (folder.takeIf { it.isDirectory } ?: root.takeIf { it.isDirectory } ?: root.parentFile)?.usableSpace
        val made = when {
            target.isFile && target.length() == voices.length() -> Outcome.Success(target)

            free != null && free < needed -> Outcome.Failure("needs $needed bytes free, has $free")

            else -> {
                prune(folder, keep = null)
                KokoroBlends.write(voices, target, spec.speakerId, blend)
            }
        }
        return when (made) {
            is Outcome.Success -> made.value.also {
                prune(folder, keep = it)
                report.neededBytes = null
            }

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Blend for ${spec.voiceId} not applied, reading the voice as installed: ${made.error}")
                report.neededBytes = needed
                null
            }
        }
    }

    /** Removes [id]'s copy: its blend was turned off, or the voice deleted. */
    fun clear(id: String) = locked { delete(File(root, id), "the blend of $id") }

    /** Removes every copy (the Storage screen). */
    fun clearAll(): Outcome<Unit, String> = locked {
        if (delete(root, "the narrator blends")) Outcome.Success(Unit) else Outcome.Failure("blend files remain")
    }

    fun sizeBytes(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Every finished copy but [keep], and every temp file (no other build runs while the lock is held). */
    private fun prune(folder: File, keep: File?) {
        folder.listFiles()?.filter { it != keep }?.forEach { stale ->
            if (!stale.delete()) AudoraLog.w(TAG, "Could not delete an old blend file of ${folder.name}")
        }
    }

    private fun delete(dir: File, what: String): Boolean {
        val gone = !dir.exists() || dir.deleteRecursively()
        if (gone) AudoraLog.i(TAG, "Cleared $what") else AudoraLog.w(TAG, "Could not fully clear $what")
        return gone
    }

    private fun nameOf(spec: SherpaVoiceSpec, blend: StyleBlend): String {
        val percent = (blend.amount * PERCENT).roundToInt()
        val version = spec.contentVersion.take(VERSION_CHARS)
        return listOf(spec.speakerId, blend.withSpeaker, percent, version).joinToString(PART) + SUFFIX
    }

    companion object {
        private const val TAG = "Speech.Blend"
        private const val PERCENT = 100
        private const val PART = "-"
        private const val SUFFIX = ".bin"
        private const val VERSION_CHARS = 12
        private val LOCK = Any()

        /** Runs [block] under the lock every build, load and clear of a blend takes, in any process component. */
        fun <T> locked(block: () -> T): T = synchronized(LOCK, block)
    }
}
