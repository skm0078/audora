// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/BrandJourney.kt
// Purpose: Release journey step 02: the launcher shows the public name, ListenLock, and never the code name Audora.

package io.github.skm0078.audora.release

import io.github.skm0078.audora.release.JourneyDevice.app
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object BrandJourney {
    fun theLauncherSaysListenLock() {
        val label = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        assertTrue("Launcher label was \"$label\"", label.startsWith("ListenLock"))
        assertFalse("Launcher label was \"$label\"", label.contains("Audora", ignoreCase = true))
    }
}
