// File: app/src/main/kotlin/io/github/skm0078/audora/ui/pdfcheck/PdfCheckController.kt
// Purpose: Opens a picked PDF with :pdf off the main thread and turns the result into PdfCheckState.

package io.github.skm0078.audora.ui.pdfcheck

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plain state holder (no Compose UI here). Holds the password only in memory, for this check.
 * The first pages are extracted so a real-device run shows that PDFium, the JNI library and the
 * `/ActualText` rebuild work on the phone.
 */
class PdfCheckController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    var state: PdfCheckState by mutableStateOf(PdfCheckState.Idle)
        private set

    private var uri: Uri? = null
    private var job: Job? = null
    private val builder = ReadingUnitBuilder()

    fun open(picked: Uri) {
        uri = picked
        load(password = null)
    }

    fun submitPassword(password: String) = load(password)

    fun cancelPassword() {
        state = PdfCheckState.Idle
    }

    private fun load(password: String?) {
        val target = uri ?: return
        job?.cancel()
        state = PdfCheckState.Opening
        job = scope.launch { state = withContext(io) { check(target, password) } }
    }

    private fun check(target: Uri, password: String?): PdfCheckState {
        val engine = PdfiumEngine.forUri(context, target)
        return when (val opened = PdfBook.open(engine, password, logContext = LOG_CONTEXT)) {
            is Outcome.Failure -> failureState(opened.error)
            is Outcome.Success -> opened.value.use(::summarize)
        }
    }

    private fun summarize(book: PdfBook): PdfCheckState {
        val started = SystemClock.elapsedRealtime()
        val pages = minOf(book.pageCount, PAGES_TO_CHECK)
        var sentences = 0
        var spans = 0
        var mapErrors = 0
        var firstSentence: String? = null
        for (page in 0 until pages) {
            val text = when (val result = book.pageText(page)) {
                is Outcome.Success -> result.value

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Check failed, $LOG_CONTEXT page=$page error=${result.error}")
                    return PdfCheckState.Failed(PdfCheckFailure.PAGE_FAILED)
                }
            }
            val units = builder.build(text)
            sentences += units.size
            spans += text.stats.actualTextSpans
            mapErrors += text.stats.unicodeMapErrors
            if (firstSentence == null) firstSentence = units.firstOrNull()?.text
        }
        val millis = SystemClock.elapsedRealtime() - started
        AudoraLog.i(TAG, "Check done, $LOG_CONTEXT pages=$pages/${book.pageCount} sentences=$sentences ms=$millis")
        return PdfCheckState.Ready(
            PdfCheckSummary(book.pageCount, pages, sentences, spans, mapErrors, millis, firstSentence),
        )
    }

    private fun failureState(error: PdfOpenError): PdfCheckState = when (error) {
        PdfOpenError.PasswordRequired -> PdfCheckState.NeedsPassword(wrongPassword = false)
        PdfOpenError.IncorrectPassword -> PdfCheckState.NeedsPassword(wrongPassword = true)
        is PdfOpenError.Damaged -> PdfCheckState.Failed(PdfCheckFailure.DAMAGED)
        is PdfOpenError.Unreadable -> PdfCheckState.Failed(PdfCheckFailure.UNREADABLE)
        is PdfOpenError.EngineUnavailable -> PdfCheckState.Failed(PdfCheckFailure.ENGINE_UNAVAILABLE)
    }

    private companion object {
        const val TAG = "Audora.PdfCheck"
        const val LOG_CONTEXT = "book=check"
        const val PAGES_TO_CHECK = 3
    }
}
