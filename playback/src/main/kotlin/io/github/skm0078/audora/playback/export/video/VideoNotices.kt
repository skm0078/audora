// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/VideoNotices.kt
// Purpose: The quote video's notifications: its progress while it is made, then ready with a Share button, or why
//          it failed.

package io.github.skm0078.audora.playback.export.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.R

/** Platform notifications, like the audio clip's, on a channel of their own. */
internal class VideoNotices(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val name = context.getString(R.string.video_channel)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))
    }

    /** While the voice reads (no [percent] yet: the bar runs on its own), then while the frames are encoded. */
    fun making(percent: Int? = null): Notification = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_export_audiobook)
        .setContentTitle(context.getString(R.string.video_making))
        .setProgress(FULL, percent ?: 0, percent == null)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    fun showProgress(percent: Int) = manager.notify(RUNNING_ID, making(percent))

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
            .setContentTitle(context.getString(R.string.video_ready))
            .setContentIntent(share)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.video_share), share).build())
            .build()
        manager.notify(RESULT_ID, notice)
    }

    fun showFailed(reason: String) {
        AudoraLog.w(TAG, "No video: $reason")
        val notice = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_export_audiobook)
            .setContentTitle(context.getString(R.string.video_channel))
            .setContentText(context.getString(R.string.video_failed))
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID, notice)
    }

    companion object {
        const val RUNNING_ID = 0x0A3D
        private const val RESULT_ID = 0x0A3E
        private const val CHANNEL = "quote_videos"
        private const val TAG = "Video.Notices"
        private const val FULL = 100
    }
}
