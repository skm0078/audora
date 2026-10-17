// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderFakes.kt
// Purpose: In-memory player, PDF, library and settings stores for the reader tests.

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.playback.report.SoundedWrongReport
import kotlinx.coroutines.flow.MutableStateFlow

val BOOK = BookId("3f2a9c01-7b1e-4c55-9a0d-2f6c1e8b7d10")
val OTHER_BOOK = BookId("other-book")

fun book(id: BookId = BOOK) = Book(id, "content://docs/1", "Moby Dick", 1_000, "v1:a", null, 0, null)

/** Three sentences per page; sentence i of page p starts at char i * 20. */
fun unitsOf(page: Int, count: Int = 3) = List(count) { i ->
    ReadingUnit(page, i, i * 20, i * 20 + 15, "Page $page sentence $i.", Lang.EN, 0, null)
}

/** US Letter, the fake document's page size in points. */
const val PAGE_WIDTH = 612f
const val PAGE_HEIGHT = 792f

/** Like [unitsOf], each sentence on a line of its own: line i spans x 72..540, its top 72 + 20·i pt from the top. */
fun boxedUnitsOf(page: Int) = unitsOf(page).map { unit ->
    val top = PAGE_HEIGHT - LINE_TOP - unit.index * LINE_PITCH
    unit.copy(bounds = listOf(TextBox(left = 72f, top = top, right = 540f, bottom = top - LINE_HEIGHT)))
}

private const val LINE_TOP = 72f
private const val LINE_PITCH = 20f
private const val LINE_HEIGHT = 12f

class FakePageImage(val page: Int, val widthPx: Int, val maxPixels: Int) : PageImage {
    override val transform = PageTransform.upright(PAGE_WIDTH, PAGE_HEIGHT)
    override val aspectRatio = PAGE_WIDTH / PAGE_HEIGHT

    override fun imageBitmap(): ImageBitmap = error("tests never draw")
}

class FakePlaybackPort : PlaybackPort {
    override val state = MutableStateFlow(PlaybackSnapshot.Connecting)
    val calls = mutableListOf<String>()
    val starts = mutableListOf<Pair<ReadingPosition, String?>>()
    val reports = mutableListOf<SoundedWrongReport>()

    fun connect(speed: Float = 1f) {
        state.value = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED, speed = speed)
    }

    fun readingAt(position: ReadingPosition, playing: Boolean = true) {
        state.value = state.value.copy(position = position, wantsToPlay = playing)
    }

    override fun start(position: ReadingPosition, password: String?) {
        starts += position to password
    }

    override fun play() {
        calls += "play"
    }

    override fun pause() {
        calls += "pause"
    }

    override fun skipToNextSentence() {
        calls += "next"
    }

    override fun skipToPreviousSentence() {
        calls += "previous"
    }

    override fun setPace(pace: Pace) {
        calls += "pace $pace"
    }

    override fun setClausePauses(on: Boolean) {
        calls += "clause pauses $on"
    }

    override fun setReadingModes(modes: ReadingModes) {
        calls += "reading modes ${modes.encode()}"
    }

    override fun setVoiceShaping(shaping: VoiceShaping) {
        calls += "voice shaping ${shaping.encode()}"
    }

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
    }

    override fun setSleepTimer(setting: SleepTimerSetting) {
        calls += "sleep $setting"
    }

    override fun setSleepOptions(options: SleepOptions) {
        calls += "sleep options ${options.encode()}"
    }

    override fun setOwnSettings(on: Boolean) {
        calls += "own settings $on"
    }

    override fun setBookVoice(lang: Lang, voice: VoiceId) {
        calls += "book voice $lang $voice"
    }

    override fun undoJump() {
        calls += "undo jump"
    }

    override fun reportSoundedWrong(report: SoundedWrongReport) {
        reports += report
    }
}

class FakeDocument(
    override val pageCount: Int,
    private val failingPages: Set<Int> = emptySet(),
    private val unitsOnPage: (Int) -> List<ReadingUnit> = { unitsOf(it) },
    private val failingRenders: Set<Int> = emptySet(),
    private val outline: List<OutlineEntry> = emptyList(),
) : ReaderDocument {
    var closed = false
    val reads = mutableListOf<Int>()
    val renders = mutableListOf<FakePageImage>()

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> {
        reads += page
        return if (page in failingPages) {
            Outcome.Failure(PdfPageError.ExtractionFailed(page, "test"))
        } else {
            Outcome.Success(unitsOnPage(page))
        }
    }

    override suspend fun outline(): List<OutlineEntry> = outline

    override suspend fun render(page: Int, widthPx: Int, maxPixels: Int): Outcome<PageImage, PdfPageError> =
        if (page in failingRenders) {
            Outcome.Failure(PdfPageError.ExtractionFailed(page, "test"))
        } else {
            Outcome.Success(FakePageImage(page, widthPx, maxPixels).also { renders += it })
        }

    override fun close() {
        closed = true
    }
}

/** Opens [document]; with a [password] set, only that password opens it. */
class FakeOpener(
    private val document: FakeDocument = FakeDocument(pageCount = 10),
    private val password: String? = null,
    private val failure: PdfOpenError? = null,
) : ReaderDocumentOpener {
    override suspend fun open(
        documentUri: String,
        password: String?,
        logContext: String,
    ): Outcome<ReaderDocument, PdfOpenError> = when {
        failure != null -> Outcome.Failure(failure)
        this.password != null && password == null -> Outcome.Failure(PdfOpenError.PasswordRequired)
        this.password != null && password != this.password -> Outcome.Failure(PdfOpenError.IncorrectPassword)
        else -> Outcome.Success(document)
    }
}

class FakeBooks(private val book: Book? = book()) : ReaderBooks {
    val opened = mutableListOf<Pair<BookId, Int>>()

    override suspend fun find(id: BookId): Book? = book?.takeIf { it.id == id }

    override suspend fun markOpened(id: BookId, pageCount: Int) {
        opened += id to pageCount
    }
}

class FakeViews(var saved: ReaderView? = null) : ViewPreference {
    override suspend fun loadView(): ReaderView? = saved

    override suspend fun saveView(view: ReaderView) {
        saved = view
    }
}

class FakeSpeeds(var saved: Float? = null) : SpeedPreference {
    override suspend fun load(): Float? = saved

    override suspend fun save(speed: Float) {
        saved = speed
    }
}
