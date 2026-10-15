// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderFakes.kt
// Purpose: In-memory player, PDF, library and speed store for the reader tests.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.contract.PlaybackAddress
import io.github.skm0078.audora.playback.contract.SleepTimerSetting
import kotlinx.coroutines.flow.MutableStateFlow

val BOOK = BookId("3f2a9c01-7b1e-4c55-9a0d-2f6c1e8b7d10")
val OTHER_BOOK = BookId("other-book")

fun book(id: BookId = BOOK) = Book(id, "content://docs/1", "Moby Dick", 1_000, "v1:a", null, 0, null)

/** Three sentences per page; sentence i of page p starts at char i * 20. */
fun unitsOf(page: Int, count: Int = 3) = List(count) { i ->
    ReadingUnit(page, i, i * 20, i * 20 + 15, "Page $page sentence $i.", Lang.EN, 0, null)
}

class FakePlaybackPort : PlaybackPort {
    override val state = MutableStateFlow(PlaybackSnapshot.Connecting)
    val calls = mutableListOf<String>()
    val starts = mutableListOf<Pair<PlaybackAddress, String?>>()

    fun connect(speed: Float = 1f) {
        state.value = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED, speed = speed)
    }

    fun readingAt(address: PlaybackAddress, playing: Boolean = true) {
        state.value = state.value.copy(position = address, wantsToPlay = playing)
    }

    override fun start(address: PlaybackAddress, password: String?) {
        starts += address to password
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

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
    }

    override fun setSleepTimer(setting: SleepTimerSetting) {
        calls += "sleep $setting"
    }
}

class FakeDocument(override val pageCount: Int, private val failingPages: Set<Int> = emptySet()) : ReaderDocument {
    var closed = false
    val reads = mutableListOf<Int>()

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> {
        reads += page
        return if (page in failingPages) {
            Outcome.Failure(PdfPageError.ExtractionFailed(page, "test"))
        } else {
            Outcome.Success(unitsOf(page))
        }
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

class FakeSpeeds(var saved: Float? = null) : SpeedPreference {
    override suspend fun load(): Float? = saved

    override suspend fun save(speed: Float) {
        saved = speed
    }
}
