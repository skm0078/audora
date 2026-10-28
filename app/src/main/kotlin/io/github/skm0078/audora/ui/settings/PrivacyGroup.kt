// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PrivacyGroup.kt
// Purpose: Settings' "Privacy and security" page (ADR-061, ADR-062): what stays on the phone, the app lock, hiding
//          app content from screenshots and recents, and the privacy policy.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.privacy.PrivacyControls
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption

/** The promise leads, as a row whose info button holds the whole statement, so it is one tap from every switch. */
@Composable
internal fun PrivacyGroup(controls: PrivacyControls?) {
    OptionGroup(title = null) {
        LinkOption(
            stringResource(R.string.privacy_statement_title),
            onClick = null,
            help = stringResource(R.string.privacy_statement_body),
        )
        controls?.let {
            OptionDivider()
            SettingAnchor(SettingKey.APP_LOCK) {
                SwitchOption(
                    stringResource(R.string.app_lock_title),
                    it.settings.appLock,
                    it.onAppLock,
                    subtitle = stringResource(R.string.app_lock_short),
                    help = stringResource(R.string.app_lock_body),
                )
            }
            OptionDivider()
            SettingAnchor(SettingKey.HIDE_CONTENT) {
                SwitchOption(
                    stringResource(R.string.hide_content_title),
                    it.settings.hideContent,
                    it.onHideContent,
                    subtitle = stringResource(R.string.hide_content_short),
                    help = stringResource(R.string.hide_content_body),
                )
            }
        }
        OptionDivider()
        PrivacyPolicyItem()
    }
}
