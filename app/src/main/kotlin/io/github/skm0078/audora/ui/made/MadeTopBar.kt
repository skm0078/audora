// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeTopBar.kt
// Purpose: The Made place's top bar actions (ADR 2026-10-28-made-tab): Jobs, saying how many are making now, and
//          grid or list, the library's own layout choice.

package io.github.skm0078.audora.ui.made

import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.library.LibraryLayout

/**
 * "Jobs", or "2 making" while jobs run: where a job just started from a book is looked for, and where its result
 * lands. TalkBack hears the whole name, "Jobs and downloads", and opens it by the same tap.
 */
@Composable
internal fun JobsChip(running: Int, onOpen: () -> Unit) {
    val spoken = stringResource(R.string.made_jobs)
    AssistChip(
        onClick = onOpen,
        label = {
            Text(
                if (running > 0) {
                    pluralStringResource(R.plurals.made_jobs_making, running, running)
                } else {
                    stringResource(R.string.made_jobs_short)
                },
            )
        },
        leadingIcon = { Icon(painterResource(R.drawable.ic_jobs), contentDescription = null) },
        modifier = Modifier.semantics {
            onClick(label = spoken) {
                onOpen()
                true
            }
        },
    )
}

/** Grid or list as two icon segments; picking one sets the library's layout too, since they are one choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutToggle(list: Boolean, onLayout: (LibraryLayout) -> Unit, modifier: Modifier = Modifier) {
    val choices = listOf(
        Triple(LibraryLayout.GRID, R.drawable.ic_grid_view, R.string.library_layout_grid),
        Triple(LibraryLayout.LIST, R.drawable.ic_view_list, R.string.library_layout_list),
    )
    SingleChoiceSegmentedButtonRow(modifier) {
        choices.forEachIndexed { index, (layout, icon, label) ->
            SegmentedButton(
                selected = (layout == LibraryLayout.LIST) == list,
                onClick = { onLayout(layout) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {},
                label = { Icon(painterResource(icon), stringResource(label)) },
            )
        }
    }
}
