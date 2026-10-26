// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/FolderSyncTrigger.kt
// Purpose: Syncs through the picked folder (CF-16) when the listener leaves ListenLock, so another phone picks up
//          where this one stopped.

package io.github.skm0078.audora.ui.library

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.skm0078.audora.playback.sync.FolderSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Counts started activities; when the last one stops (home, another app, the screen off), one sync runs, skipped when
 * sync is off or ran within the last minute. A rotation stops and starts the activity at once, so it is skipped too.
 */
class FolderSyncTrigger(private val scope: CoroutineScope, private val folderSync: () -> FolderSync) :
    Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0 && !activity.isChangingConfigurations) scope.launch { folderSync().sync(force = false) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
