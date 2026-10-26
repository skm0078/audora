// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/TutorConfig.kt
// Purpose: Tunables for the reading tutor: listening, phrase finding and how heard words are matched (plan item 67).

package io.github.skm0078.audora.core.config

/**
 * @property sampleRateHz the microphone rate every tutor model takes.
 * @property micChunkMs how much audio is read from the microphone at a time; smaller follows faster, costs more calls.
 * @property micBufferMs how much audio may wait while a phrase is decoded (Hindi decodes a whole phrase at once), so
 *   none is lost; 4 s at 16 kHz is 256 KB.
 * @property matchSimilarity how alike a heard word and the page's word must be (1 minus their edit distance over the
 *   longer one's length) to count as read; recognisers misspell rare words, so this is forgiving.
 * @property exactUpToChars words this short must be heard exactly ("in" is not "on").
 * @property lookaheadWords how far past the next word a stretch of speech is matched, so a skipped line is found.
 * @property maxNumberWords how many heard words one number or symbol on the page may take ("1995" is read as four).
 * @property maxUtteranceWords heard words beyond this, in one stretch without a pause, are left unmatched.
 * @property carryWords at most this many heard words a stretch left unexplained are lined up again with the next one
 *   (ReadAlong), so a reader pausing after every word still gets past a word the recogniser missed; enough to pay for
 *   passing over a skipped line.
 * @property minRepeatWords a stretch shorter than this is never taken as going back over read words: one or two
 *   common words ("of the") fit somewhere behind almost always, which held the place still.
 * @property resyncWords a final stretch (with what was carried) of at least this many words that mostly matches page
 *   words further on moves the place there, the words between marked skipped; fewer words match by chance.
 * @property repeatMatchShare at least this share of a stretch must match for it to count as going back over words
 *   just read, or as the reader found further on; a stretch that fits nowhere well moves nothing.
 * @property misreadCost what a heard word unlike its page word costs the alignment (WordAligner).
 * @property extraCost what a heard word with no page word (a repeat, a filler) costs.
 * @property numberWordCost what each heard word after the first costs when a number or symbol takes it: just under
 *   [extraCost], so "nineteen ninety five" goes to "1995" rather than being extras, but a number at the end of a
 *   stretch cannot swallow the real words after it for free.
 * @property skipOpenCost what passing over a page word costs; [skipExtendCost] each further word in the same run,
 *   so a skipped line costs less than reading it as a row of mismatches.
 * @property endpointSilenceSec a pause this long after speech ends a stretch (word-by-word models).
 * @property endpointQuietSec a stretch with no speech ends after this long.
 * @property endpointMaxSec a stretch ends after this long whatever happens.
 * @property vadThreshold how sure the voice detector must be that someone is speaking (phrase-by-phrase model).
 * @property vadMinSilenceSec a pause this long ends a phrase.
 * @property vadMinSpeechSec sound shorter than this is not a phrase.
 * @property vadMaxSpeechSec a phrase is cut after this long, so feedback never waits too long.
 * @property vadWindowSamples the voice detector's window (Silero takes 512 samples at 16 kHz).
 * @property numThreads recogniser threads; two keep the phone cool and are fast enough for one speaker.
 * @property cancelWaitMs deleting a model waits at most this long for a cancelled download to stop first.
 */
data class TutorConfig(
    val sampleRateHz: Int = 16_000,
    val micChunkMs: Int = 100,
    val micBufferMs: Int = 4_000,
    val matchSimilarity: Double = 0.7,
    val exactUpToChars: Int = 2,
    val lookaheadWords: Int = 30,
    val maxNumberWords: Int = 6,
    val maxUtteranceWords: Int = 80,
    val carryWords: Int = 12,
    val minRepeatWords: Int = 3,
    val resyncWords: Int = 4,
    val repeatMatchShare: Double = 0.5,
    val misreadCost: Int = 10,
    val extraCost: Int = 10,
    val numberWordCost: Int = 9,
    val skipOpenCost: Int = 15,
    val skipExtendCost: Int = 3,
    val endpointSilenceSec: Float = 1.2f,
    val endpointQuietSec: Float = 2.4f,
    val endpointMaxSec: Float = 20f,
    val vadThreshold: Float = 0.5f,
    val vadMinSilenceSec: Float = 0.4f,
    val vadMinSpeechSec: Float = 0.25f,
    val vadMaxSpeechSec: Float = 8f,
    val vadWindowSamples: Int = 512,
    val numThreads: Int = 2,
    val cancelWaitMs: Long = 10_000L,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be > 0, was $sampleRateHz" }
        require(micChunkMs > 0) { "micChunkMs must be > 0, was $micChunkMs" }
        require(micBufferMs >= micChunkMs) { "micBufferMs must be >= micChunkMs, was $micBufferMs" }
        require(matchSimilarity in 0.0..1.0) { "matchSimilarity must be in 0..1, was $matchSimilarity" }
        require(exactUpToChars >= 0) { "exactUpToChars must be >= 0, was $exactUpToChars" }
        require(lookaheadWords >= 0) { "lookaheadWords must be >= 0, was $lookaheadWords" }
        require(maxNumberWords >= 1) { "maxNumberWords must be >= 1, was $maxNumberWords" }
        require(maxUtteranceWords >= 1) { "maxUtteranceWords must be >= 1, was $maxUtteranceWords" }
        require(carryWords >= 0) { "carryWords must be >= 0, was $carryWords" }
        require(minRepeatWords >= 1) { "minRepeatWords must be >= 1, was $minRepeatWords" }
        require(resyncWords >= 1) { "resyncWords must be >= 1, was $resyncWords" }
        require(repeatMatchShare in 0.0..1.0) { "repeatMatchShare must be in 0..1, was $repeatMatchShare" }
        require(misreadCost > 0 && extraCost > 0 && skipOpenCost > 0 && skipExtendCost >= 0) { "costs must be > 0" }
        require(numberWordCost in 0 until extraCost) { "numberWordCost must be in 0 until extraCost" }
        require(endpointSilenceSec > 0 && endpointQuietSec > 0 && endpointMaxSec > 0) { "endpoints must be > 0" }
        require(vadThreshold in 0f..1f) { "vadThreshold must be in 0..1, was $vadThreshold" }
        require(vadMinSilenceSec > 0 && vadMinSpeechSec > 0 && vadMaxSpeechSec > 0) { "VAD times must be > 0" }
        require(vadWindowSamples > 0) { "vadWindowSamples must be > 0, was $vadWindowSamples" }
        require(numThreads > 0) { "numThreads must be > 0, was $numThreads" }
        require(cancelWaitMs >= 0) { "cancelWaitMs must be >= 0, was $cancelWaitMs" }
    }

    /** Samples per microphone read. */
    val micChunkSamples: Int get() = sampleRateHz * micChunkMs / MS_PER_SECOND

    /** Samples the microphone buffer holds. */
    val micBufferSamples: Int get() = sampleRateHz * micBufferMs / MS_PER_SECOND

    /** Microphone chunks that may wait to be decoded. */
    val micQueueChunks: Int get() = micBufferMs / micChunkMs
}

private const val MS_PER_SECOND = 1000
