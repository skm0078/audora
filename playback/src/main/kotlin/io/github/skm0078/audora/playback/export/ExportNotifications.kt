// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportNotifications.kt
// Purpose: The audiobook export's notifications: progress with Cancel while it runs, then done, stopped or failed.

package io.github.skm0078.audora.playback.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.playback.R

/** Platform notifications: no androidx dependency for four plain notifications. */
internal class ExportNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.export_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.export_channel_description) }
        manager.createNotificationChannel(channel)
    }

    fun running(title: String, chapter: Int?, chapterCount: Int, percent: Int): Notification {
        val text = if (chapter == null) {
            context.getString(R.string.export_preparing)
        } else {
            context.getString(R.string.export_running_text, chapter, chapterCount, percent)
        }
        val cancel = PendingIntent.getService(
            context,
            0,
            Intent(context, AudiobookExportService::class.java).setAction(AudiobookExport.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder()
            .setContentTitle(context.getString(R.string.export_running_title, title))
            .setContentText(text)
            .setProgress(PERCENT, percent, chapter == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.export_cancel), cancel).build(),
            )
            .build()
    }

    fun showRunning(notification: Notification) = manager.notify(RUNNING_ID, notification)

    fun showDone(title: String, summary: ExportSummary, folder: String) {
        val minutes = summary.durationMs / MS_PER_MINUTE
        val duration = context.getString(
            R.string.export_duration,
            (minutes / MINUTES_PER_HOUR).toInt(),
            (minutes % MINUTES_PER_HOUR).toInt(),
        )
        val files = summary.chapters.size
        val text = context.resources.getQuantityString(R.plurals.export_done_text, files, files, duration, folder)
        showResult(context.getString(R.string.export_done_title, title), text)
    }

    fun showFailed(title: String, reason: String) =
        showResult(context.getString(R.string.export_failed_title, title), reason)

    fun showCancelled(title: String, folder: String) = showResult(
        context.getString(R.string.export_cancelled_title, title),
        context.getString(R.string.export_cancelled_text, folder),
    )

    private fun showResult(title: String, text: String) {
        val notification = builder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID, notification)
    }

    private fun builder(): Notification.Builder = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_export_audiobook)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .apply { openApp()?.let { setContentIntent(it) } }

    /** Tapping opens Audora; :playback does not know its activity, the launcher does. */
    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val RUNNING_ID = 0x0A0D
        private const val RESULT_ID = 0x0A0E
        private const val CHANNEL = "audiobook_export"
        private const val PERCENT = 100
        private const val MS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L
    }
}
