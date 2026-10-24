// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/CopyNotifications.kt
// Purpose: The copies' notifications: progress with Cancel while one is made, then saved, stopped or failed.

package io.github.skm0078.audora.playback.epub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.jobs.JobTimeLeft
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.playback.jobs.OpenJobs

/** Platform notifications, like the audiobook export's. */
internal class CopyNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val timeLeft = JobTimeLeft(context)

    /** The step the time left was measured on: each step's percent starts again from zero. */
    private var measuredStep: String? = null

    fun createChannel() {
        val name = context.getString(R.string.copy_channel)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))
    }

    /**
     * [percent] null while it is still preparing. [publish] off for the notification posted before the job is
     * known, so the app's view of a running copy is never reset by it.
     */
    fun running(title: String, step: String, percent: Int?, publish: Boolean = true): Notification {
        val line = percent?.let { stepLine(step, it) } ?: step.also { measuredStep = null }
        if (publish) LongJobs.progress(LongJobKind.COPY, title, line, percent)
        val cancel = PendingIntent.getService(
            context,
            0,
            Intent(context, BookCopyService::class.java).setAction(BookCopies.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder()
            .setContentTitle(title)
            .setContentText(line)
            .setProgress(PERCENT, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.copy_cancel), cancel).build())
            .build()
    }

    private fun stepLine(step: String, percent: Int): String {
        if (step != measuredStep) {
            timeLeft.reset()
            measuredStep = step
        }
        return timeLeft.line(step, percent)
    }

    fun showRunning(notification: Notification) = manager.notify(RUNNING_ID, notification)

    fun showDone(title: String, saved: SavedCopy, folder: String) {
        val names = saved.names.joinToString(context.getString(R.string.copy_name_separator))
        val text = context.getString(R.string.copy_done_text, names, folder.trimEnd('/'))
        val skipped = context.getString(R.string.copy_skipped, saved.skippedPages)
        val body = if (saved.skippedPages == 0) text else "$text $skipped"
        showResult(context.getString(R.string.copy_done_title, title), body, LongJobEnd.DONE)
    }

    fun showFailed(error: CopyError) {
        val reason = when (error) {
            CopyError.NothingToCopy -> context.getString(R.string.copy_failed_empty)
            is CopyError.BookUnreadable -> context.getString(R.string.copy_failed_unreadable, error.page + 1)
            is CopyError.Storage -> context.getString(R.string.copy_failed_storage, error.detail)
            is CopyError.VoiceFailed -> error.detail
            is CopyError.NotOpened -> error.reason
        }
        showResult(context.getString(R.string.copy_failed_title), reason, LongJobEnd.FAILED)
    }

    fun showCancelled() = showResult(
        context.getString(R.string.copy_cancelled_title),
        context.getString(R.string.copy_cancelled_text),
        LongJobEnd.STOPPED,
    )

    /**
     * Android's daily time limit stopped the copy; finished chapters are kept. Continue opens Audora (which resets
     * the limit) and starts the copy again ([BookCopies.continueFrom]).
     */
    fun showTimedOut(id: BookId, kind: CopyKind) {
        val text = context.getString(R.string.copy_timed_out_text)
        LongJobs.finish(LongJobKind.COPY, LongJobEnd.STOPPED, context.getString(R.string.copy_timed_out_title), text)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(BookCopies.EXTRA_CONTINUE_BOOK, id.value)
            ?.putExtra(BookCopies.EXTRA_CONTINUE_KIND, kind.name)
            // Delivered to a running Audora through onNewIntent; without these flags it only comes to the front.
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Its own request code: launch intents differ only in extras, which PendingIntent matching ignores, so
            // with the app's usual code 0 this would rewrite (and be rewritten by) every "open Audora" tap in the app.
            ?.let {
                PendingIntent.getActivity(
                    context,
                    CONTINUE_REQUEST,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val notification = builder()
            .setContentTitle(context.getString(R.string.copy_timed_out_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply {
                open?.let {
                    setContentIntent(it)
                    addAction(Notification.Action.Builder(null, context.getString(R.string.copy_continue), it).build())
                }
            }
            .build()
        manager.notify(RESULT_ID, notification)
    }

    /** [end] set: the result is also the app's view of the copy ([LongJobs]); a refused start leaves it unset. */
    fun showResult(title: String, text: String, end: LongJobEnd? = null) {
        end?.let { LongJobs.finish(LongJobKind.COPY, it, title, text) }
        val notification = builder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID, notification)
    }

    /** Tapping opens the Jobs screen (ADR-067); a timed-out result replaces it with its own Continue. */
    private fun builder(): Notification.Builder = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_export_audiobook)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .apply { OpenJobs.pendingIntent(context)?.let { setContentIntent(it) } }

    companion object {
        const val RUNNING_ID = 0x0A3D
        private const val RESULT_ID = 0x0A3E
        private const val CONTINUE_REQUEST = 0x0A3F
        private const val CHANNEL = "book_copies"
        private const val PERCENT = 100
    }
}
