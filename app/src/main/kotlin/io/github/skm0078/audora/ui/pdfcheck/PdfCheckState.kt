// File: app/src/main/kotlin/io/github/skm0078/audora/ui/pdfcheck/PdfCheckState.kt
// Purpose: UI state of opening a book: password, extraction summary or a typed failure (P1-02, until P1-05).

package io.github.skm0078.audora.ui.pdfcheck

sealed interface PdfCheckState {
    data object Idle : PdfCheckState

    data object Opening : PdfCheckState

    /** Show the password prompt; [wrongPassword] when the last attempt failed. */
    data class NeedsPassword(val wrongPassword: Boolean) : PdfCheckState

    data class Ready(val summary: PdfCheckSummary) : PdfCheckState

    data class Failed(val reason: PdfCheckFailure) : PdfCheckState
}

data class PdfCheckSummary(
    val pageCount: Int,
    val pagesChecked: Int,
    val sentences: Int,
    val actualTextSpans: Int,
    val unicodeMapErrors: Int,
    val millis: Long,
    val firstSentence: String?,
)

enum class PdfCheckFailure {
    DAMAGED,
    UNREADABLE,
    ENGINE_UNAVAILABLE,
    PAGE_FAILED,
}
