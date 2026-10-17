// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/async/LaunchedFuture.kt
// Purpose: A Media3 answer computed in a coroutine that always completes, with the failure when the work throws.

package io.github.skm0078.audora.playback.async

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [block] on this scope and completes the returned future with its value. A [block] that throws completes the
 * future with the exception (logged under [tag] with [what], never user text), so a controller such as Android Auto
 * is answered instead of waiting forever; cancellation cancels the future and still propagates.
 */
@Suppress("TooGenericExceptionCaught") // Any failure must reach the controller as an answer, not hang it.
fun <T> CoroutineScope.launchedFuture(
    tag: String,
    what: String,
    block: suspend (SettableFuture<T>) -> Unit,
): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    launch {
        try {
            block(result)
        } catch (e: CancellationException) {
            result.cancel(false)
            throw e
        } catch (e: Exception) {
            AudoraLog.e(tag, "$what failed", e)
            result.setException(e)
        }
    }
    return result
}

/** [launchedFuture] for work whose answer is its return value. */
fun <T> CoroutineScope.launchedValue(tag: String, what: String, block: suspend () -> T): ListenableFuture<T> =
    launchedFuture(tag, what) { it.set(block()) }
