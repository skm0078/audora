// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/PrivacyHost.kt
// Purpose: The activity's side of Settings → Privacy and security (ADR-061): the lock's timing, the gate over the
//          screens and what the switches do, kept out of MainActivity.

package io.github.skm0078.audora.privacy

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.skm0078.audora.R
import kotlinx.coroutines.launch

/** The privacy switches as saved, and what turning each one does (the app lock asks for the phone's lock first). */
class PrivacyControls(
    val settings: PrivacySettings,
    val onAppLock: (Boolean) -> Unit,
    val onHideContent: (Boolean) -> Unit,
)

/** Observes the activity's lifecycle: time out of sight counts from onStop, and turning the phone is not leaving. */
class PrivacyHost(
    private val activity: ComponentActivity,
    private val session: AppLockSession = AppLockSession.process,
) : DefaultLifecycleObserver {
    private val preferences by lazy { PrivacyPreferences(activity.applicationContext) }

    override fun onStart(owner: LifecycleOwner) = session.onBack(SystemClock.elapsedRealtime())

    override fun onStop(owner: LifecycleOwner) {
        if (!activity.isChangingConfigurations) session.onAway(SystemClock.elapsedRealtime())
    }

    /**
     * Every screen goes inside. Until the settings are read (a few ms) only the background is drawn, so nothing of
     * the library shows before it is known whether the lock is on.
     */
    @Composable
    fun Gate(content: @Composable () -> Unit) {
        val settings by remember { preferences.settings }.collectAsStateWithLifecycle(initialValue = null)
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            AppLockGate(activity, current, session, content)
        }
    }

    /** For Settings; null while the settings are still being read. */
    @Composable
    fun controls(): PrivacyControls? {
        val settings by remember { preferences.settings }.collectAsStateWithLifecycle(initialValue = null)
        return settings?.let { PrivacyControls(it, ::setAppLock, ::setHideContent) }
    }

    /** Turning the lock on asks first, so nobody locks themselves out with a screen lock that does not work. */
    private fun setAppLock(on: Boolean) {
        if (!on) {
            activity.lifecycleScope.launch { preferences.saveAppLock(false) }
            return
        }
        val unlock = DeviceUnlock(activity)
        if (!unlock.hasScreenLock()) {
            Toast.makeText(activity, R.string.app_lock_needs_screen_lock, Toast.LENGTH_LONG).show()
            return
        }
        unlock.ask { proved ->
            if (proved) {
                session.unlock()
                activity.lifecycleScope.launch { preferences.saveAppLock(true) }
            }
        }
    }

    private fun setHideContent(on: Boolean) {
        activity.lifecycleScope.launch { preferences.saveHideContent(on) }
    }
}
