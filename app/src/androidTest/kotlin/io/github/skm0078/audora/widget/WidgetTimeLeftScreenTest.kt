// File: app/src/androidTest/kotlin/io/github/skm0078/audora/widget/WidgetTimeLeftScreenTest.kt
// Purpose: The home-screen widget shows the book's time left under its title (CF-7), and the library's page line
//          without one. Draws the real RemoteViews and leaves screenshots in CI's "player-screens" artifact.

package io.github.skm0078.audora.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.Screens
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetTimeLeftScreenTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val book = ResumeWidgetBook(BOOK, "A Tale of Two Cities", page = 11, pageCount = 340)

    @Test
    fun theTimeLeftShowsUnderTheTitle() {
        val timeLeft = WidgetTimeLeft(BOOK, ms = (2 * 60 + 10) * 60_000L, atElapsedMs = 0)
        val view = drawn(ResumeWidgetProvider.views(context, book, timeLeft), "widget-time-left")
        val line = view.findViewById<TextView>(R.id.widget_progress_text).text.toString()
        val duration = context.getString(R.string.duration_hours_minutes, 2L, 10L)
        assertEquals(context.getString(R.string.widget_time_left, duration, 12, 340), line)
    }

    @Test
    fun anotherBooksTimeLeftIsNotShown() {
        val other = WidgetTimeLeft("other", ms = 60_000, atElapsedMs = 0)
        val view = drawn(ResumeWidgetProvider.views(context, book, other), "widget-no-time-left")
        val line = view.findViewById<TextView>(R.id.widget_progress_text).text.toString()
        assertEquals(context.getString(R.string.library_progress, 12, 340, 4), line)
    }

    /** The widget as a launcher would draw it, at its target size of three cells by one. */
    private fun drawn(views: android.widget.RemoteViews, name: String): View {
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            view = views.apply(context, parent)
            val density = context.resources.displayMetrics.density
            val width = (WIDTH_DP * density).toInt()
            val height = (HEIGHT_DP * density).toInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            Screens.save(name, bitmap)
        }
        return view
    }

    private companion object {
        const val BOOK = "widget-book"
        const val WIDTH_DP = 250
        const val HEIGHT_DP = 72
    }
}
