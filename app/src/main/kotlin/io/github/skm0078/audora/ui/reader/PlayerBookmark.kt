// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerBookmark.kt
// Purpose: The player's one-tap bookmark (CF-2): the notice its button (PlayerLineActions) leaves, with
//          Add note and Undo.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.PlayerControlsConfig
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * "Bookmarked", with Add note and Undo, for [config]'s notice time (longer when the listener asked Android for more
 * time to act); it then closes and the bookmark stays. TalkBack reads it as it appears.
 */
@Composable
internal fun PlayerBookmarkNotice(
    bookmark: Bookmark?,
    actions: BookNotesActions,
    config: PlayerControlsConfig = PlayerControlsConfig(),
) {
    bookmark ?: return
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(bookmark.id) {
        val shownMs = accessibility?.calculateRecommendedTimeoutMillis(
            config.bookmarkNoticeMs,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: config.bookmarkNoticeMs
        delay(shownMs)
        actions.dismissQuickBookmark()
    }
    // The theme's action colour on the snackbar's dark surface; a plain TextButton's primary is too faint on it.
    val actionColors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
    Snackbar(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
        action = {
            Row {
                TextButton(onClick = actions::noteQuickBookmark, colors = actionColors) {
                    Text(stringResource(R.string.player_bookmark_note))
                }
                TextButton(onClick = actions::undoQuickBookmark, colors = actionColors) {
                    Text(stringResource(R.string.player_bookmark_undo))
                }
            }
        },
    ) {
        Text(
            stringResource(R.string.player_bookmark_saved),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
