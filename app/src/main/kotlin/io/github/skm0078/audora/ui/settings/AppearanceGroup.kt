// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/AppearanceGroup.kt
// Purpose: Settings → Appearance: wallpaper colours or Audora's palette, light/dark/system, and true black.

package io.github.skm0078.audora.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.DarkMode
import io.github.skm0078.audora.ui.theme.Dimens

@Composable
internal fun AppearanceGroup(appearance: Appearance, onChange: (Appearance) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        SwitchItem(
            R.string.settings_wallpaper_colors_title,
            R.string.settings_wallpaper_colors_body,
            appearance.wallpaperColors,
        ) { onChange(appearance.copy(wallpaperColors = it)) }
        SettingsDivider()
        DarkModeRow(appearance.darkMode) { onChange(appearance.copy(darkMode = it)) }
        SettingsDivider()
        SwitchItem(
            R.string.settings_true_black_title,
            R.string.settings_true_black_body,
            appearance.trueBlack,
        ) { onChange(appearance.copy(trueBlack = it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkModeRow(mode: DarkMode, onChange: (DarkMode) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM)) {
        Text(stringResource(R.string.settings_dark_mode_title), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Dimens.SpaceS)) {
            DarkMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == mode,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, DarkMode.entries.size),
                ) { Text(stringResource(darkModeLabel(option))) }
            }
        }
    }
}

private fun darkModeLabel(mode: DarkMode): Int = when (mode) {
    DarkMode.SYSTEM -> R.string.settings_dark_mode_system
    DarkMode.LIGHT -> R.string.settings_dark_mode_light
    DarkMode.DARK -> R.string.settings_dark_mode_dark
}
