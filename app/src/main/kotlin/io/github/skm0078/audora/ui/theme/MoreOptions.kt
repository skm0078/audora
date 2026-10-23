// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/MoreOptions.kt
// Purpose: One way to keep rarely used options out of the way: a row that opens them in place, closed at first.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R

/**
 * The common options stay in view; the rest sit behind this row, which says what it holds ([summary]) while closed.
 * The whole row is the touch target, and TalkBack reads its title, summary and "expanded" or "collapsed" together.
 * Open or closed survives rotation and the app going to the background.
 */
@Composable
fun MoreOptions(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.more_options_expanded else R.string.more_options_collapsed)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTarget)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = state }
                .padding(vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!open) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
            )
        }
        if (open) content()
    }
}

private const val HALF_TURN = 180f
