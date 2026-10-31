// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingKey.kt
// Purpose: Every setting search can find, with the page and group it lives in, so a search result opens You on the
//          right row (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.settings

import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/** The You place's own page and the two it opens (ADR-062). */
internal enum class SettingsPage(@StringRes val title: Int) {
    // "Settings", not the place's name "You": listeners look for the word (ADR 2026-10-28-phone-drawer).
    MAIN(R.string.settings_title),
    PRIVACY(R.string.settings_group_privacy),
    ADVANCED(R.string.settings_advanced_title),
}

/**
 * A setting by its row: [title] is the row's own name, [group] the heading above it (null where the page has none),
 * [terms] the words people type for it besides its name. In the order the You place shows them, which search keeps
 * on a tie.
 */
enum class SettingKey(
    @StringRes val title: Int,
    internal val page: SettingsPage,
    @StringRes val group: Int?,
    @StringRes val terms: Int? = null,
) {
    TOUR(R.string.settings_tour_title, SettingsPage.MAIN, null, R.string.found_terms_tour),
    ACTED_VOICE(R.string.acted_model_title, SettingsPage.MAIN, R.string.settings_group_listening),
    STORY_BRAIN(R.string.brain_model_title, SettingsPage.MAIN, R.string.settings_group_listening),
    SCANNED_PAGES(
        R.string.settings_ocr_title,
        SettingsPage.MAIN,
        R.string.settings_group_listening,
        R.string.found_terms_scanned_pages,
    ),
    SKIP_CITATIONS(
        R.string.settings_skip_citations_title,
        SettingsPage.MAIN,
        R.string.settings_group_listening,
        R.string.found_terms_citations,
    ),
    RESUME_ON_RECONNECT(
        R.string.settings_reconnect_title,
        SettingsPage.MAIN,
        R.string.settings_group_listening,
        R.string.found_terms_reconnect,
    ),
    KEEP_SCREEN_ON(
        R.string.settings_keep_screen_on_title,
        SettingsPage.MAIN,
        R.string.settings_group_reading,
        R.string.found_terms_keep_screen_on,
    ),
    THEME(
        R.string.settings_dark_mode_title,
        SettingsPage.MAIN,
        R.string.settings_group_reading,
        R.string.found_terms_theme,
    ),
    WALLPAPER_COLOURS(
        R.string.settings_wallpaper_colors_title,
        SettingsPage.MAIN,
        R.string.settings_group_reading,
        R.string.found_terms_wallpaper_colours,
    ),
    TRUE_BLACK(
        R.string.settings_true_black_title,
        SettingsPage.MAIN,
        R.string.settings_group_reading,
        R.string.found_terms_true_black,
    ),
    LANGUAGE(
        R.string.settings_language_title,
        SettingsPage.MAIN,
        R.string.settings_group_reading,
        R.string.found_terms_language,
    ),
    BACKGROUND_READING(
        R.string.settings_battery_title,
        SettingsPage.MAIN,
        R.string.settings_group_app,
        R.string.found_terms_background_reading,
    ),
    KIDS_MODE(R.string.settings_kids_title, SettingsPage.MAIN, R.string.settings_group_app, R.string.found_terms_kids),
    APP_LOCK(R.string.app_lock_title, SettingsPage.PRIVACY, null, R.string.found_terms_app_lock),
    HIDE_CONTENT(R.string.hide_content_title, SettingsPage.PRIVACY, null, R.string.found_terms_hide_content),
    VOLUME_KEYS(
        R.string.settings_volume_keys_title,
        SettingsPage.ADVANCED,
        R.string.settings_group_advanced_reading,
        R.string.found_terms_volume_keys,
    ),
    VOICE_COMMANDS(
        R.string.commands_switch_title,
        SettingsPage.ADVANCED,
        R.string.settings_group_advanced_reading,
        R.string.found_terms_voice_commands,
    ),
    PAUSES_IN_SCANS(R.string.settings_punct_title, SettingsPage.ADVANCED, R.string.settings_group_advanced_reading),
    PRONUNCIATION(
        R.string.settings_pronunciation_title,
        SettingsPage.ADVANCED,
        R.string.settings_group_pronunciation,
        R.string.found_terms_pronunciation,
    ),
    HINTS(
        R.string.settings_hints_title,
        SettingsPage.ADVANCED,
        R.string.settings_group_help,
        R.string.found_terms_hints,
    ),
    SHARE_LOGS(
        R.string.share_logs_title,
        SettingsPage.ADVANCED,
        R.string.settings_group_help,
        R.string.found_terms_share_logs,
    ),
}
