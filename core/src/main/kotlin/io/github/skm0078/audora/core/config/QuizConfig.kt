// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/QuizConfig.kt
// Purpose: Tunables for "Quiz me": fill-in-the-blank cards from highlights and their review schedule (plan item 70).

package io.github.skm0078.audora.core.config

/**
 * @property minWordChars the shortest word (in letters and marks) that can be the blank.
 * @property choices how many answers a card offers, the right one included; fewer distractors means "show answer".
 * @property maxPromptChars a longer highlight is cut to this many characters around the blank.
 * @property sessionCards the most cards one quiz asks.
 * @property intervalsMs how long after a right answer a card comes back, by how many times in a row it was right;
 *   the last interval repeats.
 * @property retryMs how long after a wrong answer a card comes back.
 */
data class QuizConfig(
    val minWordChars: Int = 4,
    val choices: Int = 4,
    val maxPromptChars: Int = 280,
    val sessionCards: Int = 10,
    val intervalsMs: List<Long> = DEFAULT_INTERVAL_DAYS.map { it * MS_PER_DAY },
    val retryMs: Long = 600_000L, // 10 minutes
) {
    init {
        require(minWordChars > 0) { "minWordChars must be > 0, was $minWordChars" }
        require(choices >= 2) { "choices must be >= 2, was $choices" }
        require(maxPromptChars > minWordChars) { "maxPromptChars must be > minWordChars, was $maxPromptChars" }
        require(sessionCards > 0) { "sessionCards must be > 0, was $sessionCards" }
        require(intervalsMs.isNotEmpty() && intervalsMs.all { it > 0 }) { "intervalsMs must be positive, non-empty" }
        require(retryMs >= 0) { "retryMs must be >= 0, was $retryMs" }
    }
}

private const val MS_PER_DAY = 86_400_000L

// 1 day, 3 days, a week, 3 weeks, 60 days: the tunable itself, named by this list.
@Suppress("MagicNumber")
private val DEFAULT_INTERVAL_DAYS = listOf(1L, 3L, 7L, 21L, 60L)
