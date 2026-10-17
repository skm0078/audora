// File: core/src/main/kotlin/io/github/skm0078/audora/core/text/UrlText.kt
// Purpose: URL-escapes text for one-line settings files, with calls that exist on every supported Android version.

package io.github.skm0078.audora.core.text

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Uses the charset-name overloads on purpose: `URLEncoder.encode(String, Charset)` and its decoder twin exist only from
 * Android 13 (API 33), are not backported by D8, and crash Android 12 (minSdk 31) with NoSuchMethodError, while the
 * desktop JDK that runs the unit tests has them. The name overloads exist on every API level.
 */
object UrlText {
    private const val UTF_8 = "UTF-8"

    fun encode(text: String): String = URLEncoder.encode(text, UTF_8)

    /** Null when [text] is not valid escaped text (a stray `%`), so a damaged file reads as missing, not a crash. */
    fun decode(text: String): String? = try {
        URLDecoder.decode(text, UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
