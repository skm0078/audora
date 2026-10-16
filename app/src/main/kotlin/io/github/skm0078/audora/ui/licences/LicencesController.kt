// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LicencesController.kt
// Purpose: "Voices & licences" screen state (P3-06): every voice's credits and an installed voice's licence files.

package io.github.skm0078.audora.ui.licences

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.speech.voices.LicenceText
import io.github.skm0078.audora.speech.voices.VoiceLicence
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen needs from the voice manager; `VoiceLibrary` in the app, a fake in tests. */
interface LicenceSource {
    suspend fun licences(): List<VoiceLicence>

    suspend fun licenceTexts(id: String): List<LicenceText>
}

/** The licence files of one voice, shown over the list. */
data class OpenLicence(val voice: VoiceLicence, val texts: List<LicenceText>)

/**
 * @property voices null while the list loads.
 * @property open the voice whose licence files are shown, if any.
 */
data class LicencesState(
    val voices: List<VoiceLicence>? = null,
    val loadFailed: Boolean = false,
    val open: OpenLicence? = null,
)

/** Plain state holder (no Compose here), like the other controllers. Call [start] once. */
class LicencesController(private val source: LicenceSource, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(LicencesState())
    val state: StateFlow<LicencesState> = mutable.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val voices = runCatchingLogged("Voice licences could not be loaded") { source.licences() }
            mutable.update { it.copy(voices = voices ?: it.voices, loadFailed = voices == null) }
        }
    }

    /** Shows the licence files inside [voice]; only installed voices have them on this phone. */
    fun openTexts(voice: VoiceLicence) {
        if (!voice.installed) return
        scope.launch {
            val texts = runCatchingLogged("Licence files of voice=${voice.id.name} unreadable") {
                source.licenceTexts(voice.id.name)
            }.orEmpty()
            mutable.update { it.copy(open = OpenLicence(voice, texts)) }
        }
    }

    fun closeTexts() = mutable.update { it.copy(open = null) }

    private suspend fun <T> runCatchingLogged(message: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: IOException) {
        AudoraLog.e(TAG, message, e)
        null
    }

    private companion object {
        const val TAG = "UI.Licences"
    }
}
