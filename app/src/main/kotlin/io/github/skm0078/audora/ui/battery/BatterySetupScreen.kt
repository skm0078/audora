// File: app/src/main/kotlin/io/github/skm0078/audora/ui/battery/BatterySetupScreen.kt
// Purpose: "Keep reading with the screen off": the system exemption prompt plus this phone maker's own steps.

package io.github.skm0078.audora.ui.battery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.battery.BackgroundAccess
import io.github.skm0078.audora.core.battery.BatteryGuide
import io.github.skm0078.audora.core.battery.BatteryStep
import io.github.skm0078.audora.core.battery.PhoneBrand
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySetupScreen(
    access: BackgroundAccess,
    steps: List<BatteryStep>,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(SCREEN_PADDING).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SPACING),
        ) {
            Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyLarge)
            ExemptionStatus(access, onAllow)
            // Never the maker's name: a heading naming the phone read as personal to testers.
            Text(stringResource(R.string.battery_steps_heading), style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step ->
                Text(stringResource(R.string.battery_step_number, index + 1, stringResource(stepText(step))))
            }
            OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.battery_open_app_settings))
            }
            Text(
                stringResource(R.string.battery_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExemptionStatus(access: BackgroundAccess, onAllow: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SCREEN_PADDING), verticalArrangement = Arrangement.spacedBy(SPACING)) {
            val (status, button, hint) = statusTexts(access)
            Text(stringResource(status), style = MaterialTheme.typography.titleSmall)
            if (button != null && hint != null) {
                Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(button)) }
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Status line, button and hint; no button once Unrestricted. */
private fun statusTexts(access: BackgroundAccess): Triple<Int, Int?, Int?> = when (access) {
    BackgroundAccess.UNRESTRICTED -> Triple(R.string.battery_status_allowed, null, null)

    BackgroundAccess.OPTIMIZED -> Triple(
        R.string.battery_status_optimized,
        R.string.battery_choose_unrestricted,
        R.string.battery_choose_unrestricted_hint,
    )

    BackgroundAccess.RESTRICTED -> Triple(
        R.string.battery_status_limited,
        R.string.battery_allow,
        R.string.battery_allow_hint,
    )
}

/**
 * One line at the top of the library until Audora is exempt or the user taps Not now (the X): what it is for, and
 * Set up, which opens the setup screen that explains the rest (ADR 2026-10-28-home-and-tour).
 */
@Composable
fun BatterySetupBanner(onNotNow: () -> Unit, modifier: Modifier = Modifier, onSetUp: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = SCREEN_PADDING, vertical = SPACING),
    ) {
        Row(Modifier.padding(start = SCREEN_PADDING), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.battery_card_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = SPACING),
            )
            TextButton(onClick = onSetUp) { Text(stringResource(R.string.battery_card_set_up)) }
            IconButton(onClick = onNotNow) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.battery_card_not_now))
            }
        }
    }
}

@StringRes
private fun stepText(step: BatteryStep): Int = when (step) {
    BatteryStep.APP_BATTERY_UNRESTRICTED -> R.string.battery_step_unrestricted
    BatteryStep.ALLOW_BACKGROUND_ACTIVITY -> R.string.battery_step_background_activity
    BatteryStep.ALLOW_AUTO_LAUNCH -> R.string.battery_step_auto_launch
    BatteryStep.SAMSUNG_NEVER_SLEEPING -> R.string.battery_step_samsung_never_sleeping
    BatteryStep.XIAOMI_NO_RESTRICTIONS -> R.string.battery_step_xiaomi_no_restrictions
    BatteryStep.VIVO_HIGH_BACKGROUND_POWER -> R.string.battery_step_vivo_background_power
    BatteryStep.LOCK_IN_RECENTS -> R.string.battery_step_lock_recents
}

private val SCREEN_PADDING = 16.dp
private val SPACING = 8.dp

@Preview(showBackground = true)
@Composable
private fun BatterySetupScreenPreview() {
    AudoraTheme {
        BatterySetupScreen(
            access = BackgroundAccess.OPTIMIZED,
            steps = BatteryGuide.stepsFor(PhoneBrand.ONEPLUS),
            onAllow = {},
            onOpenAppSettings = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BatterySetupBannerPreview() {
    AudoraTheme { BatterySetupBanner(onNotNow = {}) {} }
}
