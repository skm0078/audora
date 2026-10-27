// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AddBookButton.kt
// Purpose: The library's Add book button, with a name TalkBack reads.

package io.github.skm0078.audora.ui.library

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R

/** Named on the button itself: Material's animated label does not reach TalkBack. */
@Composable
internal fun AddBookButton(onClick: () -> Unit) {
    val add = stringResource(R.string.library_add_pdf)
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(add) },
        modifier = Modifier.semantics { contentDescription = add },
    )
}
