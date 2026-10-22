// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ClipNotices.kt
// Purpose: The audio clip's notifications: while it is made, then ready with a Share button, or why it failed.

package io.github.skm0078.audora.playback.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.R

/** Platform notifications, like the export's. */
internal class ClipNotices(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val name = context.getString(R.string.clip_channel)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))
    }

    fun making(): Notification = plain(context.getString(R.string.clip_making), ongoing = true)

    /** Tapping it, or its Share button, opens [chooser]: a user action, so Android lets it open from the background. */
    fun showReady(chooser: Intent) {
        val share = PendingIntent.getActivity(
            context,
            0,
            chooser,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notice = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_export_audiobook)
            .setContentTitle(context.getString(R.string.clip_ready))
            .setContentIntent(share)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.clip_share), share).build())
            .build()
        manager.notify(RESULT_ID, notice)
    }

    fun showFailed(reason: String) {
        AudoraLog.w(TAG, "No clip: $reason")
        manager.notify(RESULT_ID, plain(reason, ongoing = false))
    }

    private fun plain(text: String, ongoing: Boolean): Notification = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_export_audiobook)
        .setContentTitle(context.getString(R.string.clip_channel))
        .setContentText(text)
        .setOngoing(ongoing)
        .setAutoCancel(!ongoing)
        .build()

    companion object {
        const val RUNNING_ID = 0x0A2D
        private const val RESULT_ID = 0x0A2E
        private const val CHANNEL = "audio_clips"
        private const val TAG = "Clip.Notices"
    }
}
