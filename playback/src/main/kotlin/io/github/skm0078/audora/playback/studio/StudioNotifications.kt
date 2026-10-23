// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioNotifications.kt
// Purpose: Studio mode's notifications: progress with Start now and Stop while it runs, then ready, stopped or failed.

package io.github.skm0078.audora.playback.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs

/** Platform notifications, like the audiobook export's: no androidx dependency for a handful of plain notifications. */
internal class StudioNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.studio_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.studio_channel_description) }
        manager.createNotificationChannel(channel)
    }

    fun preparing(): Notification = builder()
        .setContentTitle(context.getString(R.string.studio_preparing_title))
        .setContentText(context.getString(R.string.studio_preparing))
        .setProgress(PERCENT, 0, true)
        .setOngoing(true)
        .addAction(action(R.string.studio_stop, StudioMode.ACTION_STOP))
        .build()

    /** The running notification last shown, so a second "Prepare" tap re-posts it (keeping "Start now"). */
    var lastRunning: Notification? = null
        private set

    fun showRunning(title: String, readyMs: Long, aheadMs: Long, waiting: ChargingGate.Wait?) {
        val ready = context.getString(
            R.string.studio_running_text,
            context.duration(readyMs),
            context.duration(aheadMs),
        )
        val text = when (waiting) {
            null -> ready
            ChargingGate.Wait.CHARGER -> context.getString(R.string.studio_waiting_charger, ready)
            ChargingGate.Wait.AUDIO -> context.getString(R.string.studio_waiting_audio, ready)
        }
        val percent = (readyMs * PERCENT / aheadMs).toInt().coerceIn(0, PERCENT)
        LongJobs.progress(
            LongJobKind.STUDIO,
            context.getString(R.string.studio_running_title, title),
            text,
            percent,
            waitingForCharger = waiting == ChargingGate.Wait.CHARGER,
        )
        val notification = builder()
            .setContentTitle(context.getString(R.string.studio_running_title, title))
            .setContentText(text)
            .setProgress(PERCENT, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (waiting == ChargingGate.Wait.CHARGER) {
                    addAction(action(R.string.studio_start_now, StudioMode.ACTION_ON_BATTERY))
                }
            }
            .addAction(action(R.string.studio_stop, StudioMode.ACTION_STOP))
            .build()
        lastRunning = notification
        manager.notify(RUNNING_ID, notification)
    }

    fun showDone(title: String, summary: StudioSummary) {
        val text = context.getString(
            if (summary.endOfBook) R.string.studio_done_end_text else R.string.studio_done_text,
            context.duration(summary.readyMs),
        )
        showResult(context.getString(R.string.studio_done_title, title), text, LongJobEnd.DONE)
    }

    /** [publish] off for a refused start: the run that is going on stays what the app shows. */
    fun showFailed(title: String, reason: String, publish: Boolean = true) = showResult(
        context.getString(R.string.studio_failed_title, title),
        reason,
        LongJobEnd.FAILED.takeIf { publish },
    )

    fun showStopped(title: String) = showResult(
        context.getString(R.string.studio_stopped_title, title),
        context.getString(R.string.studio_stopped_text),
        LongJobEnd.STOPPED,
    )

    /** [end] set: the result is also the app's view of the run ([LongJobs]). */
    private fun showResult(title: String, text: String, end: LongJobEnd?) {
        end?.let { LongJobs.finish(LongJobKind.STUDIO, it, title, text) }
        val notification = builder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID, notification)
    }

    private fun action(label: Int, serviceAction: String): Notification.Action {
        val intent = PendingIntent.getService(
            context,
            serviceAction.hashCode(),
            Intent(context, StudioService::class.java).setAction(serviceAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, context.getString(label), intent).build()
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
        const val RUNNING_ID = 0x0A1D
        private const val RESULT_ID = 0x0A1E
        private const val CHANNEL = "studio_mode"
        private const val PERCENT = 100
    }
}

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** "1 h 05 min", as the audiobook export writes durations. */
private fun Context.duration(ms: Long): String {
    val minutes = ms / MS_PER_MINUTE
    return getString(
        R.string.export_duration,
        (minutes / MINUTES_PER_HOUR).toInt(),
        (minutes % MINUTES_PER_HOUR).toInt(),
    )
}
