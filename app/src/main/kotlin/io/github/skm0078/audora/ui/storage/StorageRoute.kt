// File: app/src/main/kotlin/io/github/skm0078/audora/ui/storage/StorageRoute.kt
// Purpose: Settings → Storage wired to each owner: sizes measured off the main thread, again after a free, and
//          every free done by the owner's own call.

package io.github.skm0078.audora.ui.storage

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.loggingFailures
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The Storage screen for [owners] (every kind but the voices, which come from [voices]). A size that cannot be read
 * is logged and its row says so; the others still show. A failed free is logged and told to the listener, and the
 * row is measured again either way, since a partial free still frees something.
 */
@Composable
fun StorageRoute(
    voices: Flow<List<VoiceRow>>,
    owners: Map<StorageKind, StorageOwner>,
    onOpenVoices: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var failed by remember { mutableStateOf(emptySet<StorageKind>()) }
    var sizes by remember { mutableStateOf(emptyMap<StorageKind, Long>()) }
    val voiceRows by remember(voices) {
        voices.loggingFailures(TAG, "the size of ${StorageKind.VOICES}") { failed = failed + StorageKind.VOICES }
    }.collectAsStateWithLifecycle(initialValue = null)
    suspend fun measure(kind: StorageKind, owner: StorageOwner) {
        val bytes = measuredOrNull(kind, owner)
        sizes = if (bytes == null) sizes - kind else sizes + (kind to bytes)
        failed = if (bytes == null) failed + kind else failed - kind
    }
    LaunchedEffect(owners) { owners.forEach { (kind, owner) -> launch { measure(kind, owner) } } }
    StorageScreen(
        rows = storageRows(voiceRows, sizes, failed),
        onAction = { row ->
            val owner = owners[row.kind]
            val free = owner?.free
            when {
                row.kind == StorageKind.VOICES -> onOpenVoices()

                free != null -> scope.launch {
                    val outcome = free()
                    measure(row.kind, owner) // Before the snackbar, which suspends until it is dismissed.
                    outcome.errorOrNull()?.let { error ->
                        AudoraLog.w(TAG, "Could not free ${row.kind}: $error")
                        snackbar.showSnackbar(
                            context.getString(R.string.storage_free_failed, context.getString(kindLabel(row.kind))),
                        )
                    }
                }

                else -> AudoraLog.w(TAG, "No way to free ${row.kind}")
            }
        },
        onBack = onBack,
        snackbar = snackbar,
    )
}

/** The owner's size for [kind], or null, logged, when the disk would not say. */
private suspend fun measuredOrNull(kind: StorageKind, owner: StorageOwner): Long? = try {
    owner.measure()
} catch (e: IOException) {
    AudoraLog.e(TAG, "Could not read the size of $kind", e)
    null
} catch (e: SecurityException) {
    AudoraLog.e(TAG, "Could not read the size of $kind", e)
    null
}

private const val TAG = "Audora.Storage"
