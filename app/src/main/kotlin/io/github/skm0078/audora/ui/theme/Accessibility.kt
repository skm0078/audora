// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/Accessibility.kt
// Purpose: The two TalkBack fixes every screen shares: a slider that says what it sets, and a sheet handle that has a
//          name (CF-9).

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R

/**
 * A slider TalkBack reads as "[name], [value], slider" ("Speed, 1.25×"), not as a bare percentage of its track, and
 * that is at least [Dimens.TouchTarget] tall: Material's slider is 44 dp, a little short of the touch minimum.
 */
fun Modifier.namedSlider(name: String, value: String): Modifier = this
    .heightIn(min = Dimens.TouchTarget)
    .semantics {
        contentDescription = name
        stateDescription = value
    }

/**
 * Material's sheet handle with a name. Material wraps the handle in a tooltip whose long press is a control of its
 * own, and that control reads nothing unless the handle inside it has a description. Every sheet passes this as its
 * `dragHandle`.
 */
@Composable
fun SheetDragHandle() {
    val name = stringResource(R.string.a11y_sheet_handle)
    BottomSheetDefaults.DragHandle(Modifier.semantics { contentDescription = name })
}
