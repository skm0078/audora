// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/RestoreRefusal.kt
// Purpose: Stops a playback start that comes while a backup restore is unfinished, as a foreground start must stop.

package io.github.skm0078.audora.playback.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.R

/**
 * A headset's play button starts the playback service with startForegroundService; Android throws if it stops
 * without calling startForeground first. While a restore is unfinished (ADR-046) the service opens no store, so it
 * shows a short notice in the foreground and stops at once; the notice stays to say why nothing played.
 */
internal object RestoreRefusal {
    fun refuse(service: Service) {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                service.getString(R.string.restore_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notice = Notification.Builder(service, CHANNEL)
            .setSmallIcon(R.drawable.ic_export_audiobook)
            .setContentText(service.getString(R.string.long_job_restoring))
            .build()
        service.startForeground(NOTICE_ID, notice)
        service.stopForeground(Service.STOP_FOREGROUND_DETACH)
        service.stopSelf()
        AudoraLog.w(TAG, "Playback start refused: a restore is unfinished")
    }

    private const val TAG = "Playback.Restore"
    private const val CHANNEL = "restore_notice"
    private const val NOTICE_ID = 0x0A0B
}
