// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/ListenStorage.kt
// Purpose: What the reading tutor's speech models take on the phone, and freeing it, for the Storage screen (67).

package io.github.skm0078.audora.speech.listen

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Bytes in every language's folder: installed models, an older pin's folder left by an update and partial downloads
 * (up to a whole model each), all of which [deleteAllModels] frees.
 */
suspend fun ListenModels.storedBytes(): Long = withContext(Dispatchers.IO) {
    roots.values.sumOf { root -> root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
}

/** Deletes every language's model, side by side (each may wait for a cancelled download); true if all went. */
suspend fun ListenModels.deleteAllModels(): Boolean = coroutineScope {
    Lang.entries.map { lang -> async { delete(lang) } }.awaitAll().all { it is Outcome.Success }
}
