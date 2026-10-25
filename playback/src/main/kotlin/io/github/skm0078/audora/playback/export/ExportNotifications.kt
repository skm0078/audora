// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportNotifications.kt
// Purpose: The audiobook export's notifications: progress with Cancel, then done, stopped, paused or failed.

package io.github.skm0078.audora.playback.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.jobs.ContinueTokens
import io.github.skm0078.audora.playback.jobs.JobTimeLeft
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.playback.jobs.OpenJobs

/** Platform notifications: no androidx dependency for four plain notifications. */
internal class ExportNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val timeLeft = JobTimeLeft(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.export_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.export_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /**
     * [note]: a line under the progress for the whole export, such as [exportCastNote]. Once a chapter is under way
     * it is also the app's view of the export; the "preparing" one is posted before the request is read, so a refused
     * second request never resets what the app shows of the export that is running.
     */
    fun running(title: String, chapter: Int?, chapterCount: Int, percent: Int, note: String? = null): Notification {
        val text = if (chapter == null) {
            timeLeft.reset()
            context.getString(R.string.export_preparing)
        } else {
            timeLeft.line(context.getString(R.string.export_running_text, chapter, chapterCount, percent), percent)
        }
        val shown = note?.let { "$text$NOTE_BREAK$it" }
        val heading = context.getString(R.string.export_running_title, title)
        if (chapter != null) LongJobs.progress(LongJobKind.AUDIOBOOK, heading, shown ?: text, percent)
        val cancel = PendingIntent.getService(
            context,
            0,
            Intent(context, AudiobookExportService::class.java).setAction(AudiobookExport.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder()
            .setContentTitle(heading)
            .setContentText(text)
            .setProgress(PERCENT, percent, chapter == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { shown?.let { setStyle(Notification.BigTextStyle().bigText(it)) } }
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.export_cancel), cancel).build(),
            )
            .build()
    }

    fun showRunning(notification: Notification) = manager.notify(RUNNING_ID, notification)

    fun showDone(title: String, summary: ExportSummary, folder: String, note: String? = null) {
        val minutes = summary.durationMs / MS_PER_MINUTE
        val duration = context.getString(
            R.string.export_duration,
            (minutes / MINUTES_PER_HOUR).toInt(),
            (minutes % MINUTES_PER_HOUR).toInt(),
        )
        val files = summary.chapters.size
        val text = context.resources.getQuantityString(R.plurals.export_done_text, files, files, duration, folder)
        showResult(
            context.getString(R.string.export_done_title, title),
            note?.let { "$text$NOTE_BREAK$it" } ?: text,
            end = LongJobEnd.DONE,
        )
    }

    /** [publish] off for a refused start: the export that is running goes on, and the app keeps showing it. */
    fun showFailed(title: String, reason: String, publish: Boolean = true) = showResult(
        context.getString(R.string.export_failed_title, title),
        reason,
        end = LongJobEnd.FAILED.takeIf { publish },
    )

    fun showCancelled(title: String, folder: String) = showResult(
        context.getString(R.string.export_cancelled_title, title),
        context.getString(R.string.export_cancelled_resume, folder),
        end = LongJobEnd.STOPPED,
    )

    /** Android's 6-hour limit stopped it: Continue opens Audora, which starts the export again where it stopped. */
    fun showTimedOut(id: BookId, title: String) {
        // Its own request code: launch intents differ only in extras, which PendingIntent matching ignores, so with
        // the app's usual code 0 this would rewrite (and be rewritten by) every "open Audora" tap in the app.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(AudiobookExport.EXTRA_CONTINUE_BOOK, id.value)
            ?.putExtra(ContinueTokens.EXTRA_TOKEN, ContinueTokens.forContext(context).issue(AudiobookExport.jobOf(id)))
            // Delivered to a running Audora through onNewIntent; without these flags it only comes to the front.
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.let { PendingIntent.getActivity(context, CONTINUE_REQUEST, it, IMMUTABLE_UPDATE) }
        val action = open?.let {
            Notification.Action.Builder(null, context.getString(R.string.export_continue), it).build()
        }
        showResult(
            context.getString(R.string.export_timed_out_title, title),
            context.getString(R.string.export_timed_out_text),
            action,
            end = LongJobEnd.STOPPED,
        )
    }

    /** [end] set: the result is also the app's view of the job ([LongJobs]). */
    private fun showResult(title: String, text: String, action: Notification.Action? = null, end: LongJobEnd? = null) {
        end?.let { LongJobs.finish(LongJobKind.AUDIOBOOK, it, title, text) }
        val notification = builder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply { action?.let(::addAction) }
            .build()
        manager.notify(RESULT_ID, notification)
    }

    private fun builder(): Notification.Builder = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_export_audiobook)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .apply { OpenJobs.pendingIntent(context)?.let { setContentIntent(it) } }

    companion object {
        const val RUNNING_ID = 0x0A0D
        private const val RESULT_ID = 0x0A0E
        private const val CONTINUE_REQUEST = 0x0A0F
        private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val CHANNEL = "audiobook_export"
        private const val PERCENT = 100
        private const val NOTE_BREAK = "\n\n"
        private const val MS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L
    }
}

/** What the export tells the listener about character voices (plan item 63); null when there is nothing to say. */
internal fun Context.exportCastNote(cast: CastInExport): String? = when (cast) {
    CastInExport.NEEDS_BUILT_IN_VOICE -> getString(R.string.export_cast_needs_built_in_voice)
    CastInExport.ENGLISH_ONLY -> getString(R.string.export_cast_english_only)
    CastInExport.OFF, CastInExport.CAST -> null
}
