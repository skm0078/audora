// File: app/src/main/kotlin/io/github/skm0078/audora/widget/ResumeWidgetProvider.kt
// Purpose: The home-screen widget (plan item 35): the book to continue, its time left (CF-7) and progress, and a
//          Resume button that opens Audora reading it. Plain RemoteViews; it never touches the playback service.

package io.github.skm0078.audora.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.PendingIntentCodes
import kotlin.math.roundToInt

/**
 * Draws from [ResumeWidgetStore], which the app keeps current whenever the library's latest book or its progress
 * changes, and [WidgetTimeLeftStore], which the reader and the service's listening reports keep current, so the widget
 * never polls (updatePeriodMillis is 0) and never wakes the phone.
 */
class ResumeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        draw(context, manager, appWidgetIds)
    }

    companion object {
        private const val TAG = "Audora.Widget"
        private const val PERCENT = 100

        /** Tells the Resume intent apart from the launcher's, so MainActivity knows to continue the book. */
        const val ACTION_CONTINUE = "io.github.skm0078.audora.action.CONTINUE"
        const val EXTRA_BOOK_ID = "io.github.skm0078.audora.extra.BOOK_ID"

        /** Saves [book] for the widget and redraws every placed widget when it changed. */
        fun publish(context: Context, book: ResumeWidgetBook?) {
            if (!ResumeWidgetStore(context).write(book)) return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ResumeWidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(context, manager, ids)
        }

        /** Saves [timeLeft] for the widget and redraws it when it changed and is for the book the widget shows. */
        fun publishTimeLeft(context: Context, timeLeft: WidgetTimeLeft) {
            if (!WidgetTimeLeftStore(context).write(timeLeft)) return
            if (ResumeWidgetStore(context).read()?.bookId != timeLeft.bookId) return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ResumeWidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(context, manager, ids)
        }

        /** The book id a Resume tap asks to continue, or null for any other intent. */
        fun bookIdOf(intent: Intent?): String? =
            intent?.takeIf { it.action == ACTION_CONTINUE }?.getStringExtra(EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() }

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val book = ResumeWidgetStore(context).read()
            val timeLeft = WidgetTimeLeftStore(context).read()?.takeIf { it.bookId == book?.bookId }
            AudoraLog.d(TAG, "Drawing ${ids.size} widget(s), book=${book?.bookId?.take(LOG_ID_LENGTH)}")
            val views = views(context, book, timeLeft)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        internal fun views(context: Context, book: ResumeWidgetBook?, timeLeft: WidgetTimeLeft?): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_resume).apply {
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                if (book == null) {
                    setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    setTextViewText(R.id.widget_progress_text, context.getString(R.string.widget_empty_body))
                    setViewVisibility(R.id.widget_progress, View.GONE)
                    setViewVisibility(R.id.widget_resume, View.GONE)
                } else {
                    val percent = (book.fraction * PERCENT).roundToInt()
                    setTextViewText(R.id.widget_title, book.title ?: context.getString(R.string.library_untitled))
                    setTextViewText(R.id.widget_progress_text, progressLine(context, book, percent, timeLeft))
                    setProgressBar(R.id.widget_progress, PERCENT, percent, false)
                    setViewVisibility(R.id.widget_progress, View.VISIBLE)
                    setViewVisibility(R.id.widget_resume, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_resume, resume(context, book))
                    showCover(book.coverPath)
                    setContentDescription(
                        R.id.widget_resume,
                        context.getString(R.string.widget_resume_label, book.title ?: ""),
                    )
                }
            }

        /**
         * "About 2 h 10 min left · page 12 of 340" once the reader has estimated the book (CF-7): the time left first,
         * so a narrow widget cuts the page, which the bar also shows. Otherwise the library's "Page 12 of 340 · 4%".
         */
        internal fun progressLine(
            context: Context,
            book: ResumeWidgetBook,
            percent: Int,
            timeLeft: WidgetTimeLeft?,
        ): String = if (timeLeft != null && timeLeft.bookId == book.bookId) {
            context.getString(
                R.string.widget_time_left,
                context.durationLabel(timeLeft.ms),
                book.page + 1,
                book.pageCount,
            )
        } else {
            context.getString(R.string.library_progress, book.page + 1, book.pageCount, percent)
        }

        /** The library's cover file, already cover-sized (CoverConfig.widthPx), so it is decoded as it is. */
        private fun RemoteViews.showCover(path: String?) {
            val cover = path?.let { BitmapFactory.decodeFile(it) }
            if (cover == null) {
                setViewVisibility(R.id.widget_cover, View.GONE)
            } else {
                setImageViewBitmap(R.id.widget_cover, cover)
                setViewVisibility(R.id.widget_cover, View.VISIBLE)
            }
        }

        // The launcher's own intent, so a tap brings Audora's task forward instead of stacking a second screen on it.
        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            PendingIntentCodes.OPEN_FROM_WIDGET,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Opens Audora on the book and starts reading it: a tap on Resume is the user's own action. */
        private fun resume(context: Context, book: ResumeWidgetBook): PendingIntent = PendingIntent.getActivity(
            context,
            PendingIntentCodes.RESUME_FROM_WIDGET,
            continueIntent(context, book.bookId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** MainActivity, told to continue [bookId] (the widget's Resume, the Quick Settings tile). */
        fun continueIntent(context: Context, bookId: String): Intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_CONTINUE)
            .putExtra(EXTRA_BOOK_ID, bookId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private const val LOG_ID_LENGTH = 8
    }
}
