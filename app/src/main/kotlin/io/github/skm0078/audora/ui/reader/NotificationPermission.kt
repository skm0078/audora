// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NotificationPermission.kt
// Purpose: Asks for the notification permission on the first play (Build Spec §6), then plays either way.

package io.github.skm0078.audora.ui.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * Returns a function that runs a play action, first asking for POST_NOTIFICATIONS on Android 13+ if it is not
 * granted. Reading never depends on the answer: without it the media notification is hidden, audio still plays.
 * Android stops showing the prompt after two refusals; the action then simply runs.
 */
@Composable
fun rememberPlayWithNotificationPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Notification permission granted=$granted")
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(launcher) {
        { action ->
            val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needed) {
                pending[0] = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                action()
            }
        }
    }
}

private const val TAG = "Audora.Reader"
