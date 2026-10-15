// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/catalog/Catalog.kt
// Purpose: The S1 voice catalog and benchmark texts (assets/s1-voices.json), as plain Kotlin data.

package io.github.skm0078.audora.spike.voices.catalog

/** sherpa-onnx model families the spike can load. Each needs different files in its model directory. */
enum class ModelFamily(val key: String) {
    VITS("vits"),
    KITTEN("kitten"),
    KOKORO("kokoro"),
    SUPERTONIC("supertonic"),
    ;

    companion object {
        fun fromKey(key: String): ModelFamily =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown model family '$key'")
    }
}

/** What a benchmark text is for. FIRST measures first-sentence latency; HARD holds numbers and abbreviations. */
enum class TextKind(val key: String) {
    FIRST("first"),
    PROSE("prose"),
    FORMAL("formal"),
    HARD("hard"),
    ;

    companion object {
        fun fromKey(key: String): TextKind =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown text kind '$key'")
    }
}

data class BenchText(val id: String, val lang: String, val kind: TextKind, val text: String)

/**
 * One neural voice. [archive] names both the model directory and the `<archive>.zip` on the s1-voices release;
 * several voices can share one archive (Supertonic, Kokoro), which is then downloaded once.
 */
data class VoiceSpec(
    val id: String,
    val label: String,
    val lang: String,
    val family: ModelFamily,
    val archive: String,
    val model: String = "",
    val voices: String = "",
    val dataDir: String = "",
    val lexicon: String = "",
    val kokoroLang: String = "",
    val extraLang: String = "",
    val sid: Int = 0,
    val numSteps: Int = DEFAULT_SUPERTONIC_STEPS,
    val license: String = "",
) {
    companion object {
        /** sherpa-onnx's default flow-matching step count for Supertonic. */
        const val DEFAULT_SUPERTONIC_STEPS = 5
    }
}

data class Catalog(
    val version: Int,
    val modelBaseUrl: String,
    val texts: List<BenchText>,
    val voices: List<VoiceSpec>,
) {
    val languages: List<String> get() = voices.map { it.lang }.distinct()

    fun textsFor(lang: String): List<BenchText> = texts.filter { it.lang == lang }

    /** Archives needed for [langs], each once even when several voices share it. */
    fun archivesFor(langs: Collection<String>): List<String> =
        voices.filter { it.lang in langs }.map { it.archive }.distinct()
}
