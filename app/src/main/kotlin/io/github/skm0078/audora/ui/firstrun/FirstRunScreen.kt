// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/FirstRunScreen.kt
// Purpose: The first-run screens (UI plan, First run): languages, one voice per language, background reading,
//          then the first PDF. Every step can be skipped and done later from Settings.

package io.github.skm0078.audora.ui.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.voices.DeleteDialog
import io.github.skm0078.audora.ui.voices.VoiceRowItem
import io.github.skm0078.audora.ui.voices.VoicesActions
import io.github.skm0078.audora.ui.voices.VoicesState
import io.github.skm0078.audora.ui.voices.languageName

/**
 * @param backgroundAllowed Audora may run in the background already (the battery exemption).
 * @param onAllowBackground asks Android for it (the same prompt as Settings → Background reading).
 * @param onAddPdf opens the file picker; the screens finish first, so the new book lands in the library.
 * @param onFinish the screens are done (finished or skipped) and never show again.
 * @param onTrySample saves and opens the sample book; the screens finish first, as for [onAddPdf].
 */
@Composable
fun FirstRunScreen(
    voices: VoicesState,
    voiceActions: VoicesActions,
    backgroundAllowed: Boolean,
    onAllowBackground: () -> Unit,
    onAddPdf: () -> Unit,
    onFinish: () -> Unit,
    onTrySample: () -> Unit = {},
) {
    var step by rememberSaveable { mutableStateOf(FirstRunStep.LANGUAGES) }
    val phoneLanguage = LocalConfiguration.current.locales[0].toLanguageTag()
    var languages by rememberSaveable { mutableStateOf(firstRunLanguages(phoneLanguage).map { it.name }) }
    // Back steps back through the screens; on the first one it leaves the app, as the system expects.
    BackHandler(enabled = step.ordinal > 0) { FirstRunStep.entries.getOrNull(step.ordinal - 1)?.let { step = it } }
    val chosen = languages.map(Lang::valueOf).toSet()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimens.ScreenPadding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            ) {
                StepProgress(step)
                when (step) {
                    FirstRunStep.LANGUAGES -> LanguagesStep(chosen) { languages = it.map(Lang::name) }

                    FirstRunStep.VOICES -> VoicesStep(chosen, voices, voiceActions)

                    FirstRunStep.BACKGROUND -> BackgroundStep(backgroundAllowed, onAllowBackground)

                    FirstRunStep.BOOK -> BookStep(
                        onAddPdf = {
                            onFinish()
                            onAddPdf()
                        },
                        onTrySample = {
                            onFinish()
                            onTrySample()
                        },
                    )
                }
            }
            StepButtons(
                step = step,
                canGoOn = step != FirstRunStep.LANGUAGES || chosen.isNotEmpty(),
                onBack = { FirstRunStep.entries.getOrNull(step.ordinal - 1)?.let { step = it } },
                onNext = { FirstRunStep.entries.getOrNull(step.ordinal + 1)?.let { step = it } ?: onFinish() },
                onSkip = onFinish,
            )
        }
    }
}

/** "Step 2 of 4" over a bar, so how much is left shows at a glance. */
@Composable
private fun StepProgress(step: FirstRunStep) {
    val total = FirstRunStep.entries.size
    Column(
        Modifier.padding(top = Dimens.SpaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Text(
            stringResource(R.string.first_run_step, step.ordinal + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(progress = { (step.ordinal + 1f) / total }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepTitle(title: Int, body: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics {
            heading()
        },
    )
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun LanguagesStep(chosen: Set<Lang>, onChange: (Set<Lang>) -> Unit) {
    StepTitle(R.string.first_run_welcome_title, R.string.first_run_languages_body)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Lang.entries.forEach { lang ->
            FilterChip(
                selected = lang in chosen,
                onClick = { onChange(if (lang in chosen) chosen - lang else chosen + lang) },
                label = { Text(stringResource(languageName(lang))) },
            )
        }
    }
    // Said plainly, since paywalls are what listeners complain about most in other read-aloud apps.
    Text(
        stringResource(R.string.first_run_privacy),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Each chosen language's voices, with size, speed on this phone and the recommended one, as in Settings. */
@Composable
private fun VoicesStep(chosen: Set<Lang>, voices: VoicesState, actions: VoicesActions) {
    StepTitle(R.string.first_run_voices_title, R.string.first_run_voices_body)
    val rows = voices.rows
    when {
        voices.loadFailed -> Text(stringResource(R.string.voices_load_failed), color = MaterialTheme.colorScheme.error)

        rows == null -> CircularProgressIndicator()

        else -> Lang.entries.filter { it in chosen }.forEach { lang ->
            Text(
                stringResource(languageName(lang)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            rows.filter { it.lang == lang }.forEach { VoiceRowItem(it, actions, voices.sample) }
        }
    }
    // A voice downloaded here can be deleted here too, so its question shows here, not later in Settings.
    voices.pendingDelete?.let { DeleteDialog(it, actions) }
}

@Composable
private fun BackgroundStep(allowed: Boolean, onAllow: () -> Unit) {
    StepTitle(R.string.first_run_background_title, R.string.first_run_background_body)
    if (allowed) {
        Text(stringResource(R.string.first_run_background_done), color = MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = onAllow) { Text(stringResource(R.string.first_run_background_allow)) }
    }
}

@Composable
private fun BookStep(onAddPdf: () -> Unit, onTrySample: () -> Unit) {
    StepTitle(R.string.first_run_book_title, R.string.first_run_book_body)
    Button(onClick = onAddPdf) { Text(stringResource(R.string.first_run_add_pdf)) }
    OutlinedButton(onClick = onTrySample) { Text(stringResource(R.string.sample_try)) }
}

/** Back (after the first step), Skip, and Next; on the last step Next reads "Not now" and finishes. */
@Composable
private fun StepButtons(
    step: FirstRunStep,
    canGoOn: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val last = step == FirstRunStep.entries.last()
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceL)) {
        if (step.ordinal > 0) TextButton(onClick = onBack) { Text(stringResource(R.string.first_run_back)) }
        Spacer(Modifier.weight(1f))
        if (!last) TextButton(onClick = onSkip) { Text(stringResource(R.string.first_run_skip)) }
        Button(onClick = onNext, enabled = canGoOn) {
            Text(stringResource(if (last) R.string.first_run_later else R.string.first_run_next))
        }
    }
}
