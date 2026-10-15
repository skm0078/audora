// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/ui/VoicesUiState.kt
// Purpose: Everything the spike screen shows, as one immutable value the controller publishes.

package io.github.skm0078.audora.spike.voices.ui

data class LanguageStatus(val lang: String, val name: String, val voices: Int, val installed: Int)

data class ListenItem(
    val voiceId: String,
    val letter: String,
    val label: String,
    val hasSample: Boolean,
    val rating: Int?,
)

data class VoicesUiState(
    val fatalError: String? = null,
    val languages: List<LanguageStatus> = emptyList(),
    val storageMb: Long = 0,
    val downloadBusy: Boolean = false,
    val downloadLine: String = "",
    val benchBusy: Boolean = false,
    val benchLine: String = "",
    val listening: Map<String, List<ListenItem>> = emptyMap(),
    val revealNames: Boolean = false,
    val playingVoiceId: String? = null,
    val report: String = "",
) {
    val anyInstalled: Boolean get() = languages.any { it.installed > 0 }
}

/** Every user action on the screen. The activity implements it; composables only call it. */
interface VoicesActions {
    fun downloadAll()

    fun deleteModels()

    fun startBenchmark()

    fun cancelBenchmark()

    fun play(voiceId: String)

    fun rate(voiceId: String, rating: Int)

    fun setRevealNames(reveal: Boolean)

    fun openTtsSettings()

    fun shareReport()

    fun shareLog()
}
