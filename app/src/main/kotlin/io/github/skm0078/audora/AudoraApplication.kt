// File: app/src/main/kotlin/io/github/skm0078/audora/AudoraApplication.kt
// Purpose: Process entry point. Starts logging before anything else can fail.

package io.github.skm0078.audora

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.skm0078.audora.logging.AppLogging
import javax.inject.Inject

@HiltAndroidApp
class AudoraApplication : Application() {
    @Inject
    lateinit var appLogging: AppLogging

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        appLogging.start()
    }
}
