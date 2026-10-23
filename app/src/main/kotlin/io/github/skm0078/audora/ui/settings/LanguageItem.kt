// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/LanguageItem.kt
// Purpose: Settings → Reading and look → Language (plan item 44): the app's text in English, Hindi, Bengali or the
//          phone's.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.ui.theme.ChoiceOption

/** The row shows the current language; a tap opens the choice. Each language is named in its own script. */
@Composable
internal fun LanguageItem(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    ChoiceOption(
        stringResource(R.string.settings_language_title),
        AppLanguage.entries,
        language,
        label = { stringResource(languageName(it)) },
        onPick = onChange,
    )
}

internal fun languageName(language: AppLanguage): Int = when (language) {
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HINDI -> R.string.language_hindi
    AppLanguage.BENGALI -> R.string.language_bengali
    AppLanguage.PHONE -> R.string.language_phone
}
