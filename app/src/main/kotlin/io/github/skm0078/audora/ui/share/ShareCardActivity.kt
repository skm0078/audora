// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardActivity.kt
// Purpose: Hosts the card screen on its own, so any Share button (reader, library, later the stats and video) opens
//          it with one call and no route in the app's navigation (ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.ui.share

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.privacy.PrivacyHost
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AppearancePreferences
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inside the same privacy gate as the main screen: the app lock and "Hide app content" apply here too. Nothing leaves
 * the phone until the listener presses Share and picks an app.
 */
class ShareCardActivity : ComponentActivity() {
    private val privacy by lazy { PrivacyHost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val request = ShareCardRequest.from(intent)
        if (request == null) {
            AudoraLog.w(TAG, "Card screen opened without a book")
            finish()
            return
        }
        lifecycle.addObserver(privacy)
        val appearance = AppearancePreferences(applicationContext).appearance
        setContent {
            val look by remember { appearance }.collectAsStateWithLifecycle(initialValue = Appearance())
            AudoraTheme(look, darkTheme = look.isDark(isSystemInDarkTheme())) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    privacy.Gate { ShareCardRoute(request, onClose = ::finish) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "ShareCard"
    }
}

/** Loads the subject, keeps the listener's choices across rotation, and makes and sends the picture. */
@Composable
internal fun ShareCardRoute(request: ShareCardRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by produceState<CardSubjectState>(CardSubjectState.Loading, request) {
        value = CardSubjects.load(context, request)
    }
    when (val loaded = state) {
        CardSubjectState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        CardSubjectState.Missing -> Box(Modifier.fillMaxSize().padding(Dimens.ScreenPadding), Alignment.Center) {
            Text(stringResource(R.string.share_card_book_missing), style = MaterialTheme.typography.bodyLarge)
        }

        is CardSubjectState.Ready -> ReadyCard(loaded.subject, onClose)
    }
}

@Composable
private fun ReadyCard(subject: CardSubject, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = rememberCardRecorder()
    val files = remember(context) { ShareCardFiles(context) }
    var choices by rememberSaveable(stateSaver = CardChoicesSaver) { mutableStateOf(CardChoices()) }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { CardPictures.decode(context, uri) }
                if (decoded == null) {
                    Toast.makeText(context, R.string.share_card_picture_failed, Toast.LENGTH_LONG).show()
                } else {
                    picture = decoded
                    choices = choices.copy(look = CardLook.PICTURE)
                }
            }
        }
    }
    // A picture look without its picture (the screen was recreated) shows the default until one is picked again.
    val shown = if (choices.look == CardLook.PICTURE && picture == null) choices.copy(look = CardLook.DUSK) else choices
    val actions = object : CardScreenActions {
        override fun onBack() = onClose()

        override fun onChoices(next: CardChoices) {
            choices = next
        }

        override fun onPickPicture() = pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))

        override fun onShare() {
            if (busy) return
            busy = true
            scope.shareCard(recorder, files, shown.look.isPhoto, context) { busy = false }
        }
    }
    ShareCardScreen(subject, shown, picture, busy, recorder, actions)
}

/** Captures the card, writes it off the main thread and opens the share sheet; [done] runs on the main thread. */
private fun CoroutineScope.shareCard(
    recorder: CardRecorder,
    files: ShareCardFiles,
    photo: Boolean,
    context: Context,
    done: () -> Unit,
) = launch {
    val card = try {
        val bitmap = recorder.capture()
        withContext(Dispatchers.IO) { files.write(bitmap, photo) }
    } catch (e: IllegalStateException) {
        AudoraLog.e("ShareCard", "Card could not be captured", e)
        null
    }
    done()
    when {
        card == null -> Toast.makeText(context, R.string.share_card_failed, Toast.LENGTH_LONG).show()

        !context.sendCard(files.uri(card), card.mimeType) ->
            Toast.makeText(context, R.string.share_card_no_app, Toast.LENGTH_LONG).show()
    }
}

/** The choices survive rotation; the picked picture does not (it is decoded again only if picked again). */
private val CardChoicesSaver = listSaver<CardChoices, String>(
    save = { listOf(it.shape.name, it.look.name, it.dedication) },
    restore = { saved ->
        CardChoices(
            shape = CardShape.entries.firstOrNull { it.name == saved[0] } ?: CardShape.STORY,
            look = CardLook.entries.firstOrNull { it.name == saved[1] } ?: CardLook.DUSK,
            dedication = saved[2],
        )
    },
)
