// File: core/src/main/kotlin/io/github/skm0078/audora/core/text/LintCanary.kt
// Purpose: Trial only, never merged: an API 33 call in :core that lint must flag as NewApi.

package io.github.skm0078.audora.core.text

import java.net.URLEncoder

object LintCanary {
    fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)
}
