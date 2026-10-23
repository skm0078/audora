// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PrivacyPolicyItem.kt
// Purpose: Settings → Privacy policy: opens the same page the Google Play listing links to.

package io.github.skm0078.audora.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog

/** Opened in the browser rather than shown in the app, so the app and the Play listing can never show two versions. */
@Composable
internal fun PrivacyPolicyItem() {
    val uriHandler = LocalUriHandler.current
    SettingsRow(
        stringResource(R.string.settings_privacy_title),
        summary = { Text(stringResource(R.string.settings_privacy_body)) },
        onClick = {
            try {
                uriHandler.openUri(PRIVACY_POLICY_URL)
            } catch (e: IllegalArgumentException) {
                AudoraLog.w(TAG, "No app can open $PRIVACY_POLICY_URL", e)
            }
        },
    )
}

/** docs/privacy.html on GitHub Pages (docs/RELEASING.md, "Google Play"). */
private const val PRIVACY_POLICY_URL = "https://skm0078.github.io/audora/privacy.html"

private const val TAG = "UI.Settings"
