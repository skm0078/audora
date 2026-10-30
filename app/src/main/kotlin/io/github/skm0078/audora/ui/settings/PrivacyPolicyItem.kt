// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PrivacyPolicyItem.kt
// Purpose: Settings → Privacy policy: opens the same page the Google Play listing links to.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.SiteConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.ui.theme.LinkOption

/** Opened in the browser rather than shown in the app, so the app and the Play listing can never show two versions. */
@Composable
internal fun PrivacyPolicyItem() {
    val uriHandler = LocalUriHandler.current
    LinkOption(
        stringResource(R.string.settings_privacy_title),
        subtitle = stringResource(R.string.settings_privacy_body),
        onClick = {
            try {
                uriHandler.openUri(SiteConfig.PRIVACY_PAGE)
            } catch (e: IllegalArgumentException) {
                AudoraLog.w(TAG, "No app can open ${SiteConfig.PRIVACY_PAGE}", e)
            }
        },
    )
}

private const val TAG = "UI.Settings"
