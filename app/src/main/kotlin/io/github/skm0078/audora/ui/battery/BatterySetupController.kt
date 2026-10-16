// File: app/src/main/kotlin/io/github/skm0078/audora/ui/battery/BatterySetupController.kt
// Purpose: UI state for battery onboarding: whether Audora is exempt and whether the library shows the setup card.

package io.github.skm0078.audora.ui.battery

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.battery.BatteryExemption
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.battery.BatteryGuide
import io.github.skm0078.audora.core.battery.BatteryStep
import io.github.skm0078.audora.core.battery.PhoneBrand
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
    val brand: PhoneBrand get() = exemption.brand
    val steps: List<BatteryStep> = BatteryGuide.stepsFor(exemption.brand)

    var exempt: Boolean by mutableStateOf(true)
        private set

    /** Starts hidden so a phone that is already set up never flashes the card. */
    var showCard: Boolean by mutableStateOf(false)
        private set

    /** Call on screen resume: the user may come back from the system prompt or Settings. */
    fun refresh() {
        scope.launch {
            val dismissed = withContext(io) { preferences.batteryCardDismissed }
            exempt = exemption.isExempt()
            showCard = !exempt && !dismissed
        }
    }

    fun requestExemption(start: (Intent) -> Unit) = exemption.requestExemption(start)

    fun openAppSettings(start: (Intent) -> Unit) = exemption.openAppSettings(start)

    fun dismissCard() {
        showCard = false
        scope.launch { withContext(io) { preferences.batteryCardDismissed = true } }
    }
}
