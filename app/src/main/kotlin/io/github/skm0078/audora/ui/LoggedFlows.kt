// File: app/src/main/kotlin/io/github/skm0078/audora/ui/LoggedFlows.kt
// Purpose: Flows a screen collects end with a logged error instead of crashing the screen when their owner throws.

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * This flow, or, if it throws, its values up to then: the error is logged under [tag] as "Could not read [what]" and
 * handed to [onFailure] (a Storage row says so; elsewhere the screen keeps the last value). Cancellation passes.
 */
internal fun <T> Flow<T>.loggingFailures(tag: String, what: String, onFailure: (Throwable) -> Unit = {}): Flow<T> =
    catch { e ->
        AudoraLog.e(tag, "Could not read $what", e)
        onFailure(e)
    }
