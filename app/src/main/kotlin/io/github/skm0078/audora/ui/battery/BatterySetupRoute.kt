// File: app/src/main/kotlin/io/github/skm0078/audora/ui/battery/BatterySetupRoute.kt
// Purpose: The battery setup screen's controller and route, out of MainActivity: it needs only a context and a scope.

package io.github.skm0078.audora.ui.battery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.skm0078.audora.battery.BatteryExemption
import io.github.skm0078.audora.battery.OnboardingPreferences
import kotlinx.coroutines.CoroutineScope

/** Re-checks the exemption on every resume: the user comes back from the system prompt or Settings. */
@Composable
fun rememberBatterySetup(scope: CoroutineScope): BatterySetupController {
    val context = LocalContext.current.applicationContext
    val battery = remember {
        BatterySetupController(BatteryExemption(context), OnboardingPreferences(context), scope)
    }
    LifecycleResumeEffect(Unit) {
        battery.refresh()
        onPauseOrDispose {}
    }
    return battery
}

/** The system prompt and the settings page open from the activity, so they stay in the app's task. */
@Composable
fun BatterySetupRoute(battery: BatterySetupController, navigateBack: () -> Unit) {
    val activity = LocalContext.current
    BatterySetupScreen(
        exempt = battery.exempt,
        brand = battery.brand,
        steps = battery.steps,
        onAllow = { battery.requestExemption(activity::startActivity) },
        onOpenAppSettings = { battery.openAppSettings(activity::startActivity) },
        onBack = navigateBack,
    )
}
