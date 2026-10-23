// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PrivacyGroup.kt
// Purpose: Settings' "Privacy and security" group (ADR-061): what stays on the phone, the app lock, hiding app
//          content from screenshots and recents, and the privacy policy.

package io.github.skm0078.audora.ui.settings

import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.privacy.PrivacyControls

/** The statement leads, so the promise and the person's own part in it are read before any switch. */
@Composable
internal fun PrivacyGroup(controls: PrivacyControls?) {
    SettingsGroup(stringResource(R.string.settings_group_privacy)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.privacy_statement_title)) },
            supportingContent = { Text(stringResource(R.string.privacy_statement_body)) },
            colors = settingsRowColors(),
        )
        controls?.let {
            SettingsDivider()
            SwitchItem(R.string.app_lock_title, R.string.app_lock_body, it.settings.appLock, it.onAppLock)
            SettingsDivider()
            SwitchItem(
                R.string.hide_content_title,
                R.string.hide_content_body,
                it.settings.hideContent,
                it.onHideContent,
            )
        }
        SettingsDivider()
        PrivacyPolicyItem()
    }
}
