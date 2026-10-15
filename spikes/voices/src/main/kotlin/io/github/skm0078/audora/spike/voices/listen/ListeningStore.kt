// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/listen/ListeningStore.kt
// Purpose: Persists the listening test: one WAV sample per voice, the tester's 1-5 ratings, and the blind seed.

package io.github.skm0078.audora.spike.voices.listen

import android.content.Context
import io.github.skm0078.audora.spike.voices.bench.SampleSink
import io.github.skm0078.audora.spike.voices.engine.Rendered
import io.github.skm0078.audora.spike.voices.engine.WavFile
import java.io.File
import kotlin.random.Random

class ListeningStore(context: Context) : SampleSink {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sampleDir = File(context.filesDir, SAMPLE_DIR)

    /** Created once per install, so the letters stay the same across app restarts. */
    val seed: Long
        get() = prefs.getLong(KEY_SEED, 0L).takeIf { it != 0L } ?: Random.nextLong().also {
            prefs.edit().putLong(KEY_SEED, it).apply()
        }

    override fun save(voiceId: String, sample: Rendered) =
        WavFile.write(sampleFile(voiceId), sample.pcm, sample.sampleRate)

    fun sampleFile(voiceId: String): File = File(sampleDir, "$voiceId.wav")

    fun hasSample(voiceId: String): Boolean = sampleFile(voiceId).isFile

    fun rating(voiceId: String): Int? = prefs.getInt(KEY_RATING + voiceId, 0).takeIf { it in RATING_RANGE }

    fun setRating(voiceId: String, rating: Int) {
        require(rating in RATING_RANGE) { "rating must be in $RATING_RANGE" }
        prefs.edit().putInt(KEY_RATING + voiceId, rating).apply()
    }

    var revealNames: Boolean
        get() = prefs.getBoolean(KEY_REVEAL, false)
        set(value) = prefs.edit().putBoolean(KEY_REVEAL, value).apply()

    companion object {
        val RATING_RANGE = 1..5
        private const val PREFS = "s1-listening"
        private const val SAMPLE_DIR = "samples"
        private const val KEY_SEED = "seed"
        private const val KEY_RATING = "rating."
        private const val KEY_REVEAL = "reveal"
    }
}
