// File: core/src/main/kotlin/io/github/skm0078/audora/core/result/Outcome.kt
// Purpose: Typed success/failure result used across modules instead of exceptions for expected failures.

package io.github.skm0078.audora.core.result

/**
 * Either a [Success] value or a typed [Failure] error.
 *
 * Kotlin's own `Result` carries only a Throwable, which hides the expected failure cases (wrong password,
 * missing voice) from the type system. [E] is usually a sealed interface, so callers handle every case.
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T, E> Outcome<T, E>.errorOrNull(): E? = (this as? Outcome.Failure)?.error
