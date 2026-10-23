// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/AppearanceGroup.kt
// Purpose: Settings → Reading and look: keep the screen on, light/dark/system, wallpaper colours or ListenLock's
//          palette, true black, and the app's language (plan item 44).

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.DarkMode
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption

/** Theme and language show their current value at the row's end; a tap opens the choices (ADR-062). */
@Composable
internal fun AppearanceGroup(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
) {
    OptionGroup(stringResource(R.string.settings_group_reading)) {
        SwitchOption(
            stringResource(R.string.settings_keep_screen_on_title),
            keepScreenOn,
            onKeepScreenOnChange,
            help = stringResource(R.string.settings_keep_screen_on_body),
        )
        OptionDivider()
        ChoiceOption(
            stringResource(R.string.settings_dark_mode_title),
            DarkMode.entries,
            appearance.darkMode,
            label = { stringResource(darkModeLabel(it)) },
            onPick = { onChange(appearance.copy(darkMode = it)) },
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.settings_wallpaper_colors_title),
            appearance.wallpaperColors,
            { onChange(appearance.copy(wallpaperColors = it)) },
            help = stringResource(R.string.settings_wallpaper_colors_body),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.settings_true_black_title),
            appearance.trueBlack,
            { onChange(appearance.copy(trueBlack = it)) },
            help = stringResource(R.string.settings_true_black_body),
        )
        OptionDivider()
        LanguageItem(language, onLanguageChange)
    }
}

private fun darkModeLabel(mode: DarkMode): Int = when (mode) {
    DarkMode.SYSTEM -> R.string.settings_dark_mode_system
    DarkMode.LIGHT -> R.string.settings_dark_mode_light
    DarkMode.DARK -> R.string.settings_dark_mode_dark
}
