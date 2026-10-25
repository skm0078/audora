// File: app/src/main/kotlin/io/github/skm0078/audora/AudoraApplication.kt
// Purpose: Process entry point. Applies a staged restore, starts logging, then logs how the last process ended;
//          sets the app's language (plan item 44); fills books in from their files (ADR-068). Debug and dev builds
//          also watch the main thread for stalls.

package io.github.skm0078.audora

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import io.github.skm0078.audora.core.diagnostics.StallWatchdog
import io.github.skm0078.audora.core.diagnostics.StartupDiagnostics
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.language.AppLanguageStore
import io.github.skm0078.audora.language.AppLanguages
import io.github.skm0078.audora.logging.AppLogging
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.backup.PendingRestore
import io.github.skm0078.audora.ui.library.BookDetailsFill
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class AudoraApplication : Application() {
    @Inject
    lateinit var appLogging: AppLogging

    @Inject
    lateinit var startupDiagnostics: StartupDiagnostics

    @Inject
    lateinit var bookDetailsFill: BookDetailsFill

    /** Background work that lives as long as the process; one task at a time, so it never crowds the screens. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /**
     * Main-thread work that must outlive a screen, a rotation or a theme switch: a shared file being added, a studio
     * file being saved, a voice note being written. A failure is logged, never a crash.
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            AudoraLog.e(TAG, "App task failed", e)
        },
    )

    override fun onCreate() {
        // Before Hilt builds anything that could open the database or preferences a restore replaces (ADR-046).
        val restore = Backups.applyPending(this)
        super.onCreate() // Hilt injects fields here.
        appLogging.start()
        if (restore.result != PendingRestore.Result.NONE) AudoraLog.i(TAG, "Backup restore at start: $restore")
        AppLanguages.applyToProcess(this, AppLanguageStore(this).load())
        // File reads and a binder call: kept off the main thread so they never delay the first frame.
        thread(name = STARTUP_DIAGNOSTICS_THREAD) { startupDiagnostics.run() }
        bookDetailsFill.start(background)
        if (BuildConfig.DEV_TOOLS) watchForHangs()
        if (BuildConfig.DEBUG) reportStrictModeViolations()
    }

    /**
     * Debug and dev builds (the dev APK the phones run): a main thread stuck for 2 s is logged with its stack to the
     * shared logs, so a hang is found before it becomes an "isn't responding" dialog. One post a second from
     * a low-priority thread, so it costs the dev build nothing it would not cost release.
     */
    private fun watchForHangs() {
        val main = Looper.getMainLooper()
        StallWatchdog(main.thread, Handler(main)::post).start()
    }

    /**
     * Debug builds only: StrictMode reports main-thread disk and network use and leaked closeables to logcat. It
     * records a stack for every violation, too slow for the dev APK the phones time (ADR-072).
     */
    private fun reportStrictModeViolations() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
    }

    private companion object {
        const val TAG = "App"
        const val STARTUP_DIAGNOSTICS_THREAD = "audora-startup-diagnostics"
    }
}
