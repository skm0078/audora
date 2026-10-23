// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/FinishRestoringScreen.kt
// Purpose: Shown instead of the app when a restore was left half undone at start: only a Restart (item 28); and
//          the restart that applies or finishes a restore.

package io.github.skm0078.audora.ui.library

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.ui.theme.AudoraTheme

/**
 * Part of the old library is still set aside after a failed restore (ADR-046), so the library must not open: it
 * would read a mix of the old library and the backup. [onRestart] starts a new process, which tries again first.
 */
@Composable
fun FinishRestoringScreen(onRestart: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.backup_finish_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.backup_finish_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRestart) { Text(stringResource(R.string.backup_finish_restart)) }
        }
    }
}

/**
 * When this process started with a restore half undone ([Backups.restoreUnfinishedAtStart]), shows only
 * [FinishRestoringScreen] and returns true: the caller must then build nothing else (no library, no prune, no resume).
 */
fun ComponentActivity.showFinishRestoringIfNeeded(): Boolean {
    if (!Backups.restoreUnfinishedAtStart) return false
    AudoraLog.w(TAG, "Restore unfinished at start: only Finish restoring is shown")
    setContent { AudoraTheme { FinishRestoringScreen(onRestart = { restartForRestore() }) } }
    return true
}

/**
 * Plan item 28: a staged restore is put in place (or a half-undone one finished) at process start, so the app starts
 * again in a new process. The task is relaunched first, then this process ends; playback stops with it.
 */
fun ComponentActivity.restartForRestore() {
    val launch = packageManager.getLaunchIntentForPackage(packageName)?.component ?: return
    AudoraLog.i(TAG, "Restarting to apply a restore")
    startActivity(Intent.makeRestartActivityTask(launch))
    Runtime.getRuntime().exit(0)
}

private const val TAG = "UI.Restore"
