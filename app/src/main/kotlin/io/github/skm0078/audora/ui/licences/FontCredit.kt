// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/FontCredit.kt
// Purpose: The bundled fonts (the app's own type and the text view's, CF-5), credited on "Voices & licences" as their
//          licence asks.

package io.github.skm0078.audora.ui.licences

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R

/** [copyright] as the font carries it; the OFL text itself ships in the APK under assets/licences. */
internal data class FontCredit(val name: String, val copyright: String, val url: String)

internal val BUNDLED_FONTS = listOf(
    FontCredit(
        "Anek (Latin, Devanagari, Bangla)",
        "Copyright 2021 The Anek Project Authors (https://github.com/EkType/Anek)",
        "https://github.com/EkType/Anek",
    ),
    FontCredit(
        "Atkinson Hyperlegible",
        "Copyright 2020 Braille Institute of America, Inc.",
        "https://github.com/googlefonts/atkinson-hyperlegible",
    ),
    FontCredit(
        "OpenDyslexic",
        "Copyright 2012-2019 Abbie Gonzalez, Reserved Font Name OpenDyslexic",
        "https://opendyslexic.org",
    ),
    // The quote cards' type (ADR 2026-10-27-share-cards); trimmed to their scripts and Latin.
    FontCredit(
        "Tiro Devanagari Hindi",
        "Copyright 2020 The Indigo Project Authors (https://github.com/TiroTypeworks/Indigo)",
        "https://github.com/TiroTypeworks/Indigo",
    ),
    FontCredit(
        "Tiro Bangla",
        "Copyright 2020 The Indigo Project Authors (https://github.com/TiroTypeworks/Indigo)",
        "https://github.com/TiroTypeworks/Indigo",
    ),
)

/** A "Fonts" caption and one row per bundled font: its name, copyright and licence, and a link to its source. */
internal fun LazyListScope.fontLicenceItems(caption: @Composable () -> Unit, openLink: (String) -> Unit) {
    item(key = "fonts") { caption() }
    items(BUNDLED_FONTS, key = { "font:${it.name}" }) { font ->
        ListItem(
            headlineContent = { Text(font.name) },
            supportingContent = { Text(font.copyright + "\n" + stringResource(R.string.licences_ofl)) },
            trailingContent = { LinkButton(R.string.licences_source) { openLink(font.url) } },
        )
        HorizontalDivider()
    }
}
