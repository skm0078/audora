// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/ResourceBrowseTexts.kt
// Purpose: The browsable library's words from the app's strings, in the phone's language (plan item 34).

package io.github.skm0078.audora.playback.browse

import android.content.Context
import io.github.skm0078.audora.playback.R

/** Read when asked, so a language change on the phone shows at the car's next look. */
class ResourceBrowseTexts(context: Context) : BrowseTexts {
    private val appContext = context.applicationContext

    override val recent: String get() = appContext.getString(R.string.browse_recent)
    override val upNext: String get() = appContext.getString(R.string.browse_up_next)
    override val library: String get() = appContext.getString(R.string.browse_library)
    override val resume: String get() = appContext.getString(R.string.browse_resume)
    override val whereLeftOff: String get() = appContext.getString(R.string.browse_where_left_off)

    override fun page(number: Int): String = appContext.getString(R.string.browse_page, number)

    override fun chapter(number: Int): String = appContext.getString(R.string.browse_chapter, number)
}
