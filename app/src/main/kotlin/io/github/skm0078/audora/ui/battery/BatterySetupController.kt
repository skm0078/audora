// File: app/src/main/kotlin/io/github/skm0078/audora/ui/battery/BatterySetupController.kt
// Purpose: UI state for battery onboarding: whether Audora is exempt and whether the library shows the setup card.

package io.github.skm0078.audora.ui.battery

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.battery.BatteryExemption
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.battery.BackgroundAccess
import io.github.skm0078.audora.core.battery.BatteryGuide
import io.github.skm0078.audora.core.battery.BatteryStep
import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plain state holder (no Compose UI here). The card shows on first launch until Audora is exempt or the user
 * says "Not now"; Settings → Background reading always reaches the full steps.
 */
class BatterySetupController(
    private val exemption: BatteryExemption,
    private val preferences: OnboardingPreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val steps: List<BatteryStep> = BatteryGuide.stepsFor(exemption.brand)

    /** Starts set up, so a phone that already is never flashes a warning before the first check. */
    var access: BackgroundAccess by mutableStateOf(BackgroundAccess.UNRESTRICTED)
        private set

    /** Only Unrestricted counts: "Allow background usage" alone leaves Optimized ([BackgroundAccess]). */
    val exempt: Boolean get() = access == BackgroundAccess.UNRESTRICTED

    /** Starts hidden so a phone that is already set up never flashes the card. */
    var showCard: Boolean by mutableStateOf(false)
        private set

    /** Call on screen resume: the user may come back from the system prompt or Settings. */
    fun refresh() {
        scope.launch {
            val dismissed = withContext(io) { preferences.batteryCardDismissed }
            val now = withContext(io) { exemption.access() }
            if (now != access) AudoraLog.i(TAG, "Background access: $access -> $now")
            access = now
            showCard = !exempt && !dismissed
        }
    }

    /**
     * The page that fixes what is wrong now: Android's optimization list while background use is off, but App info
     * once it is on and only Optimized, since the list's "Don't optimize" is not offered on every phone and App
     * battery usage is where Unrestricted is picked.
     */
    fun allow(start: (Intent) -> Unit) = when (access) {
        BackgroundAccess.OPTIMIZED -> exemption.openAppSettings(start)
        else -> exemption.requestExemption(start)
    }

    fun openAppSettings(start: (Intent) -> Unit) = exemption.openAppSettings(start)

    fun dismissCard() {
        showCard = false
        scope.launch { withContext(io) { preferences.batteryCardDismissed = true } }
    }

    private companion object {
        const val TAG = "Audora.Battery"
    }
}
