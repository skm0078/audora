// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/DownloadTimeLeft.kt
// Purpose: "12 min left" under a download's progress bar (voices, packs, text recognition, dictionaries, tutor).

package io.github.skm0078.audora.ui.jobs

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.WorkTimeLeftConfig
import io.github.skm0078.audora.core.progress.WorkTimeLeft
import io.github.skm0078.audora.ui.reader.durationText

/**
 * The time a download still needs at the speed measured since this line appeared ([WorkTimeLeft]); "working out the
 * time left" for its first seconds. One measure per shown download: leaving the screen starts it again, which only
 * costs those few seconds. The estimate is taken once per new byte count, not on every frame.
 */
@Composable
fun DownloadTimeLeft(bytes: Long, total: Long, modifier: Modifier = Modifier) {
    if (total <= 0L) return
    val estimate = remember { WorkTimeLeft(WorkTimeLeftConfig().downloadMeasureMs) }
    val left = remember(bytes, total) { estimate.update(SystemClock.elapsedRealtime(), bytes, total) }
    Text(
        left?.let { stringResource(R.string.download_time_left, durationText(it)) }
            ?: stringResource(R.string.download_time_measuring),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
