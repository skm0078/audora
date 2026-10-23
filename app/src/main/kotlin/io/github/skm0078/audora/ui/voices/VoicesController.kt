// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoicesController.kt
// Purpose: Voices screen state (P4-01): the voice list, downloads, deletes, imports and the reading voice per language.

package io.github.skm0078.audora.ui.voices

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen needs from the voice manager; `VoiceLibrary` in the app, a fake in tests. */
interface VoiceManager {
    fun rows(): Flow<List<VoiceRow>>

    fun download(id: String)

    fun cancel(id: String)

    suspend fun delete(id: String): Outcome<Unit, String>

    /** Makes [voice] read [lang]; null goes back to the default voice. */
    fun choose(lang: Lang, voice: VoiceId?)

    /** Imports the picked folder; returns the new voice's label, or a message for the user. */
    suspend fun import(treeUri: String, lang: Lang, label: String): Outcome<String, String>
}

/** Plays a short line in one voice; in the app, with the playback voices, outside the book player. */
interface VoiceSampler {
    /** Speaks a sample in [row]'s voice; how long it plays in milliseconds, or null if it could not be spoken. */
    suspend fun play(row: VoiceRow): Long?

    fun stop()
}

/** Where a voice sample is: loading the voice and rendering, then playing, for the row [id]. */
data class VoiceSample(val id: String, val playing: Boolean)

sealed interface VoicesMessage {
    data class Imported(val label: String) : VoicesMessage

    data class ImportFailed(val reason: String) : VoicesMessage

    data class Deleted(val label: String) : VoicesMessage

    data class DeleteFailed(val label: String) : VoicesMessage

    data object SampleFailed : VoicesMessage
}

/**
 * @property rows null while the list loads.
 * @property importing a folder is being copied; the screen blocks a second import.
 */
data class VoicesState(
    val rows: List<VoiceRow>? = null,
    val loadFailed: Boolean = false,
    val importing: Boolean = false,
    val pendingDelete: VoiceRow? = null,
    val message: VoicesMessage? = null,
    val sample: VoiceSample? = null,
)

/** Plain state holder (no Compose here), like the other controllers. Call [start] once. */
class VoicesController(
    private val voices: VoiceManager,
    private val scope: CoroutineScope,
    private val sampler: VoiceSampler? = null,
) {
    private val mutable = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = mutable.asStateFlow()

    private var watch: Job? = null
    private var sampling: Job? = null

    fun start() {
        if (watch != null) return
        watch = scope.launch {
            voices.rows()
                .catch { e ->
                    AudoraLog.e(TAG, "Voice list could not be loaded", e)
                    mutable.update { it.copy(loadFailed = true) }
                }
                .collect { rows -> mutable.update { it.copy(rows = rows, loadFailed = false) } }
        }
    }

    fun download(row: VoiceRow) = voices.download(row.id.name)

    fun cancel(row: VoiceRow) = voices.cancel(row.id.name)

    /**
     * Reading voice for the row's language. Choosing the built-in voice is the same as the default; one of its other
     * speakers is a choice of its own.
     */
    fun use(row: VoiceRow) = voices.choose(
        row.lang,
        row.id.takeIf { row.kind != VoiceKind.BUILT_IN || CastSpeakers.parse(it).second != null },
    )

    /** Back to the default voice of [lang] (built-in for English, the phone's for Hindi and Bengali). */
    fun useDefault(lang: Lang) = voices.choose(lang, null)

    /** Asks to confirm deleting [row]; null closes the question. */
    fun proposeDelete(row: VoiceRow?) = mutable.update { it.copy(pendingDelete = row) }

    fun confirmDelete() {
        val row = state.value.pendingDelete ?: return
        mutable.update { it.copy(pendingDelete = null) }
        scope.launch {
            val message = when (voices.delete(row.id.name)) {
                is Outcome.Success -> VoicesMessage.Deleted(row.label)
                is Outcome.Failure -> VoicesMessage.DeleteFailed(row.label)
            }
            mutable.update { it.copy(message = message) }
        }
    }

    fun import(treeUri: String, lang: Lang, label: String) {
        if (state.value.importing) return
        mutable.update { it.copy(importing = true) }
        scope.launch {
            val message = when (val imported = voices.import(treeUri, lang, label)) {
                is Outcome.Success -> VoicesMessage.Imported(imported.value)
                is Outcome.Failure -> VoicesMessage.ImportFailed(imported.error)
            }
            mutable.update { it.copy(importing = false, message = message) }
        }
    }

    /** Plays a sample of [row]'s voice, or stops the one playing when it is [row]'s; one sample at a time. */
    fun toggleSample(row: VoiceRow) {
        val sampler = sampler ?: return
        val current = state.value.sample
        stopSample()
        if (current?.id == row.id.name) return
        val id = row.id.name
        mutable.update { it.copy(sample = VoiceSample(id, playing = false)) }
        sampling = scope.launch {
            val playsMs = sampler.play(row)
            if (playsMs == null) {
                AudoraLog.w(TAG, "Sample not played, voice=$id")
                mutable.update { it.copy(sample = null, message = VoicesMessage.SampleFailed) }
                return@launch
            }
            mutable.update { it.copy(sample = VoiceSample(id, playing = true)) }
            delay(playsMs)
            mutable.update { if (it.sample?.id == id) it.copy(sample = null) else it }
        }
    }

    /** Stops any sample (the screen closed, or another one starts). */
    fun stopSample() {
        sampling?.cancel()
        sampling = null
        sampler?.stop()
        mutable.update { it.copy(sample = null) }
    }

    fun messageShown() = mutable.update { it.copy(message = null) }

    private companion object {
        const val TAG = "UI.Voices"
    }
}
