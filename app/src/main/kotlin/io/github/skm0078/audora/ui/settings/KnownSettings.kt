// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/KnownSettings.kt
// Purpose: The current value of the settings the library already knows, shown after a found setting's path
//          ("You › Reading and look · now System"; ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.privacy.PrivacySettings
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.theme.Appearance

/** Switches and choices whose state the app shell already reads; [privacy] is null while it loads. */
class KnownSettings(
    val appearance: Appearance,
    val language: AppLanguage,
    val screen: ScreenPreference,
    val backgroundReading: Boolean,
    val privacy: PrivacySettings?,
)

/**
 * Each known setting's value in words. The model packs, citations and the other rows read their state only when You
 * opens, so search shows where they live without a value rather than reading them on every library visit.
 */
@Composable
fun settingValues(known: KnownSettings): Map<SettingKey, String> {
    val keepScreenOn by remember(known.screen) { known.screen.keepScreenOn() }.collectAsStateWithLifecycle(false)
    val volumeKeys by remember(known.screen) { known.screen.volumeKeysSkip() }.collectAsStateWithLifecycle(false)
    val on = stringResource(R.string.found_on)
    val off = stringResource(R.string.found_off)
    val theme = stringResource(darkModeLabel(known.appearance.darkMode))
    val language = stringResource(languageName(known.language))
    fun state(value: Boolean) = if (value) on else off
    return buildMap {
        put(SettingKey.THEME, theme)
        put(SettingKey.WALLPAPER_COLOURS, state(known.appearance.wallpaperColors))
        put(SettingKey.TRUE_BLACK, state(known.appearance.trueBlack))
        put(SettingKey.LANGUAGE, language)
        put(SettingKey.KEEP_SCREEN_ON, state(keepScreenOn))
        put(SettingKey.VOLUME_KEYS, state(volumeKeys))
        put(SettingKey.BACKGROUND_READING, state(known.backgroundReading))
        known.privacy?.let {
            put(SettingKey.APP_LOCK, state(it.appLock))
            put(SettingKey.HIDE_CONTENT, state(it.hideContent))
        }
    }
}
