// File: app/src/main/kotlin/io/github/skm0078/audora/AudoraApplication.kt
// Purpose: Process entry point. Starts logging before anything else can fail, then logs how the last process ended.

package io.github.skm0078.audora

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.skm0078.audora.core.diagnostics.StartupDiagnostics
import io.github.skm0078.audora.logging.AppLogging
import javax.inject.Inject
import kotlin.concurrent.thread

@HiltAndroidApp
class AudoraApplication : Application() {
    @Inject
    lateinit var appLogging: AppLogging

    @Inject
    lateinit var startupDiagnostics: StartupDiagnostics

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        appLogging.start()
        // File reads and a binder call: kept off the main thread so they never delay the first frame.
        thread(name = STARTUP_DIAGNOSTICS_THREAD) { startupDiagnostics.run() }
    }

    private companion object {
        const val STARTUP_DIAGNOSTICS_THREAD = "audora-startup-diagnostics"
    }
}
