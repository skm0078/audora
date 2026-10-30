// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/HintsItem.kt
// Purpose: Settings › Help's switch for the first-use hints (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.hints.rememberHints
import io.github.skm0078.audora.ui.theme.SwitchOption

/** On: a hint now and then, at most one each time the app starts. Off: none, and none of the seen ones come back. */
@Composable
internal fun HintsItem() {
    val hints = rememberHints()
    SwitchOption(
        stringResource(R.string.settings_hints_title),
        checked = !hints.muted,
        onChange = { on -> hints.mute(!on) },
        subtitle = stringResource(R.string.row_line_hints),
        help = stringResource(R.string.settings_hints_body),
    )
}
