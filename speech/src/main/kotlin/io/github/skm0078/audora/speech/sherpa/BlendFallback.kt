// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/BlendFallback.kt
// Purpose: A narrator blend sherpa-onnx refuses costs the blend, never the voice (plan item 75).

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import java.io.File

/**
 * Loads with the [blended] voices file when there is one; if that load fails, [onRefused] gets its error and the
 * voice loads as installed (`create(null)`), whose outcome is returned as is. Pure, so the fallback is tested
 * without the native library ([FileSherpaSynthesizerFactory] supplies the real load).
 */
internal fun <T, E> loadPreferringBlend(
    blended: File?,
    create: (voices: File?) -> Outcome<T, E>,
    onRefused: (error: E) -> Unit,
): Outcome<T, E> {
    val loaded = create(blended)
    if (blended == null || loaded !is Outcome.Failure) return loaded
    onRefused(loaded.error)
    return create(null)
}
