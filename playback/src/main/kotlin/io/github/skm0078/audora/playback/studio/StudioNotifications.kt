// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioNotifications.kt
// Purpose: Studio mode's notifications: progress with Start now and Stop while it runs, then ready, stopped or failed.

package io.github.skm0078.audora.playback.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs

/** Platform notifications, like the audiobook export's: no androidx dependency for a handful of plain notifications. */
@Suppress("TooManyFunctions") // One per notification state, plus the small text builders each one uses.
internal class StudioNotifications(private val context: Context, private val config: StudioConfig) {
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

    /**
     * The run's progress, here and on the app's card: what is ready, the time left at the speed measured on this
     * phone (or that it is still measuring), the page, and what that speed means for this voice (ADR-062).
     */
    fun showRunning(title: String, progress: StudioProgress, waiting: ChargingGate.Wait?) {
        val ready = context.getString(
            R.string.studio_running_text,
            context.studioDuration(progress.readyMs),
            context.studioDuration(config.aheadMs),
        )
        val timeLeft = progress.timeLeftMs(config.aheadMs, config.measureMs)
        val status = when (waiting) {
            null -> timeLeft?.let { context.getString(R.string.studio_time_left, ready, context.studioDuration(it)) }
                ?: context.getString(R.string.studio_measuring, ready)

            ChargingGate.Wait.CHARGER -> context.getString(R.string.studio_waiting_charger, ready)

            ChargingGate.Wait.AUDIO -> context.getString(R.string.studio_waiting_audio, ready)
        }
        val detail = listOfNotNull(
            status,
            context.getString(R.string.studio_page_of, progress.shownPage, progress.pageCount),
            speedLine(progress),
            context.getString(R.string.studio_waiting_charger_how).takeIf { waiting == ChargingGate.Wait.CHARGER },
        ).joinToString("\n")
        val percent = progress.percent(config.aheadMs)
        val runningTitle = context.getString(R.string.studio_running_title, title)
        LongJobs.progress(
            LongJobKind.STUDIO,
            runningTitle,
            detail,
            percent,
            waitingForCharger = waiting == ChargingGate.Wait.CHARGER,
        )
        val notification = builder()
            .setContentTitle(runningTitle)
            .setContentText(status)
            .setStyle(Notification.BigTextStyle().bigText(detail))
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

    /** "1 min of speech takes 45 s to make…": the reason behind the time left, once it is measured. */
    private fun speedLine(progress: StudioProgress): String? = progress.renderFactor(config.measureMs)?.let { factor ->
        val perMinute = context.studioDuration((factor * MS_PER_MINUTE).toLong())
        context.getString(if (factor < 1.0) R.string.studio_speed_keeps_up else R.string.studio_speed_slow, perMinute)
    }

    fun showDone(title: String, summary: StudioSummary) {
        val ready = context.getString(
            if (summary.endOfBook) R.string.studio_done_end_text else R.string.studio_done_text,
            context.studioDuration(summary.readyMs),
            summary.fromPage + 1,
        )
        val text = ready + "\n" + context.getString(R.string.studio_done_how)
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

private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** "40 s", "12 min" or "1 h 05 min": as short as the duration allows, for progress the listener reads at a glance. */
fun Context.studioDuration(ms: Long): String {
    val minutes = ms / MS_PER_MINUTE
    return when {
        ms < MS_PER_MINUTE -> getString(R.string.studio_seconds, (ms / MS_PER_SECOND).toInt())

        minutes < MINUTES_PER_HOUR -> getString(R.string.studio_minutes, minutes.toInt())

        else -> getString(
            R.string.export_duration,
            (minutes / MINUTES_PER_HOUR).toInt(),
            (minutes % MINUTES_PER_HOUR).toInt(),
        )
    }
}
