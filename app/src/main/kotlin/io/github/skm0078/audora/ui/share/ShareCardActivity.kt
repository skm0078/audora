// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardActivity.kt
// Purpose: Hosts the card screen on its own, so any Share button (reader, library, later the stats and video) opens
//          it with one call and no route in the app's navigation (ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.ui.share

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.share.PhotoFocus
import io.github.skm0078.audora.playback.export.ShareWords
import io.github.skm0078.audora.playback.export.video.QuoteVideoRequest
import io.github.skm0078.audora.playback.export.video.QuoteVideos
import io.github.skm0078.audora.privacy.PrivacyHost
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AppearancePreferences
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        // A page snippet is held only for this screen: closing it lets the picture go.
        if (request is ShareCardRequest.Snippet) {
            lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        if (isFinishing) PageSnippets.release()
                    }
                },
            )
        }
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

        CardSubjectState.SnippetGone -> Box(Modifier.fillMaxSize().padding(Dimens.ScreenPadding), Alignment.Center) {
            Text(stringResource(R.string.share_card_snippet_gone), style = MaterialTheme.typography.bodyLarge)
        }

        is CardSubjectState.Ready -> ReadyCard(request, loaded.subject, onClose)
    }
}

@Composable
private fun ReadyCard(request: ShareCardRequest, subject: CardSubject, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = rememberCardRecorder()
    val files = remember(context) { ShareCardFiles(context) }
    val withNotices = rememberPlayWithNotificationPermission()
    var choices by rememberSaveable(stateSaver = CardChoicesSaver) {
        mutableStateOf(CardChoices(video = (request as? ShareCardRequest.Quote)?.video == true))
    }
    var photo by remember { mutableStateOf<CardPhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Lines from the reader open in its page colours once; a book's card keeps its own default, and a look the
    // listener picks always wins.
    var lookSettled by rememberSaveable { mutableStateOf(request is ShareCardRequest.WholeBook) }
    val darkApp = MaterialTheme.colorScheme.background.luminance() < HALF_LUMINANCE
    LaunchedEffect(Unit) {
        if (lookSettled) return@LaunchedEffect
        val tone = ReadingLookPreferences(context).look.first().tone
        if (!lookSettled) choices = choices.copy(look = CardLook.forReading(tone, darkApp))
        lookSettled = true
    }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { CardPictures.decode(context, uri) }
                if (decoded == null) {
                    Toast.makeText(context, R.string.share_card_picture_failed, Toast.LENGTH_LONG).show()
                } else {
                    photo = CardPhoto(decoded)
                    choices = choices.copy(look = CardLook.PICTURE)
                }
            }
        }
    }
    // A picture look without its picture (the screen was recreated) shows the default until one is picked again.
    val shown = if (choices.look == CardLook.PICTURE && photo == null) choices.copy(look = CardLook.DUSK) else choices
    val actions = object : CardScreenActions {
        override fun onBack() = onClose()

        override fun onChoices(next: CardChoices) {
            choices = next
            lookSettled = true
        }

        override fun onPhotoFocus(next: PhotoFocus) {
            photo = photo?.copy(focus = next)
        }

        override fun onPickPicture() = pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))

        override fun onShare() {
            if (busy) return
            busy = true
            val photo = shown.look.isPhoto
            scope.captureCard(recorder, write = { files.write(it, photo) }) { card ->
                busy = false
                when {
                    card == null -> Toast.makeText(context, R.string.share_card_failed, Toast.LENGTH_LONG).show()

                    !context.sendCard(files.uri(card), card.mimeType, ShareWords.caption(context, subject.source)) ->
                        Toast.makeText(context, R.string.share_card_no_app, Toast.LENGTH_LONG).show()
                }
            }
        }

        // The video's progress and its Share button are in a notification, so that permission is asked first.
        override fun onMakeVideo() {
            val body = subject.body as? CardBody.Quote
            when {
                busy || body == null -> Unit
                QuoteVideos.busy -> Toast.makeText(context, R.string.share_card_video_busy, Toast.LENGTH_LONG).show()
                else -> withNotices { makeVideo(videoRequest(request.book, body, shown)) }
            }
        }

        // The card is captured without its words as the video's backdrop; the video's notification takes over.
        private fun makeVideo(video: QuoteVideoRequest) {
            busy = true
            scope.captureCard(recorder, write = { writeBackdrop(it, QuoteVideos.backdropFile(context)) }) { backdrop ->
                busy = false
                if (backdrop == null) {
                    Toast.makeText(context, R.string.share_card_failed, Toast.LENGTH_LONG).show()
                } else {
                    QuoteVideos.make(context, video)
                    Toast.makeText(context, R.string.share_card_video_started, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    ShareCardScreen(subject, shown, photo, busy, recorder, actions)
}

/** The video of [body]'s quote as [shown]: its words in the look's ink, the one being said on its accent. */
private fun videoRequest(book: BookId, body: CardBody.Quote, shown: CardChoices) = QuoteVideoRequest(
    book = book,
    quote = body.quote.text,
    page = body.page ?: 0,
    ink = shown.look.ink.toArgb(),
    accent = shown.look.accent.toArgb(),
    sharp = shown.sharp,
)

/**
 * Captures the card as drawn and hands it to [write] off the main thread; [then] gets what was written, or null if the
 * capture failed (logged), back on the main thread whatever thread the scope resumes on (a test's composition clock
 * resumes off it).
 */
internal fun <T : Any> CoroutineScope.captureCard(recorder: CardRecorder, write: (Bitmap) -> T?, then: (T?) -> Unit) =
    launch {
        val written = try {
            val card = recorder.capture()
            withContext(Dispatchers.IO) { write(card) }
        } catch (e: IllegalStateException) {
            AudoraLog.e("ShareCard", "Card could not be captured", e)
            null
        }
        withContext(Dispatchers.Main) { then(written) }
    }

/**
 * PNG: the backdrop is read once, by the video, and a lossless one keeps the credit sharp. Blocking; [file] once
 * written, else null (logged).
 */
private fun writeBackdrop(backdrop: Bitmap, file: File): File? = try {
    val written = FileOutputStream(file).use { out ->
        backdrop.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    }
    file.takeIf { written }.also { if (!written) AudoraLog.w("ShareCard", "Video backdrop was not encoded") }
} catch (e: IOException) {
    AudoraLog.e("ShareCard", "Video backdrop could not be written", e)
    null
}

// Below this the app's background is dark (Material's own threshold for light or dark content).
private const val HALF_LUMINANCE = 0.5f

// PNG ignores the quality; the platform asks for one.
private const val PNG_QUALITY = 100

// Where the saver keeps the video choices (after shape, look and dedication); missing in a state saved before them.
private const val SAVED_VIDEO = 3
private const val SAVED_SHARP = 4

/** The choices survive rotation; the picked picture does not (it is decoded again only if picked again). */
private val CardChoicesSaver = listSaver<CardChoices, String>(
    save = { listOf(it.shape.name, it.look.name, it.dedication, it.video.toString(), it.sharp.toString()) },
    restore = { saved ->
        CardChoices(
            shape = CardShape.entries.firstOrNull { it.name == saved[0] } ?: CardShape.STORY,
            look = CardLook.entries.firstOrNull { it.name == saved[1] } ?: CardLook.DUSK,
            dedication = saved[2],
            video = saved.getOrNull(SAVED_VIDEO) == true.toString(),
            sharp = saved.getOrNull(SAVED_SHARP) == true.toString(),
        )
    },
)
