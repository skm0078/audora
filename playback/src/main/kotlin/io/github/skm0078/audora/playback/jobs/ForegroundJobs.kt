// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/ForegroundJobs.kt
// Purpose: The pieces every long-job foreground service shares (audiobook export, copies, studio mode, clips, quote
//          videos): its foreground type, its wake lock, and how it leaves the foreground when the work ends.

package io.github.skm0078.audora.playback.jobs

import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager

object ForegroundJobs {
    /** mediaProcessing is the type made for this (Android 15+); dataSync before it existed. */
    fun type(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
    } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }
}

/**
 * A partial wake lock that keeps the work going with the screen off. Not reference counted: each `acquire(timeout)`
 * replaces the last timeout instead of stacking holds, so one [releaseIfHeld] always lets go.
 */
fun Service.jobWakeLock(tag: String): PowerManager.WakeLock = getSystemService(PowerManager::class.java)
    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
    .apply { setReferenceCounted(false) }

/** Lets go of the lock if it still holds it (its timeout may already have). */
fun PowerManager.WakeLock.releaseIfHeld() {
    if (isHeld) release()
}

/**
 * Takes the service out of the foreground, removing its running notification, and stops it. [runningNoticeId] also
 * cancels that notification by id, for a late progress update that re-posted it after the service left the foreground.
 */
fun Service.leaveForeground(runningNoticeId: Int? = null) {
    stopForeground(Service.STOP_FOREGROUND_REMOVE)
    runningNoticeId?.let { getSystemService(NotificationManager::class.java)?.cancel(it) }
    stopSelf()
}
