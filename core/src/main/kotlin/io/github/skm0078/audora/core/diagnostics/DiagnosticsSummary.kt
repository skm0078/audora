// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsSummary.kt
// Purpose: The plain-text front page of a shared log bundle: build, phone, recent process deaths, unfinished read.

package io.github.skm0078.audora.core.diagnostics

/** Build and phone facts the app module reads from BuildConfig and android.os.Build. */
data class BuildAndDevice(
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/** Identifiers and system facts only: the summary never contains a book's title or text. */
object DiagnosticsSummary {
    fun render(
        createdAtMillis: Long,
        build: BuildAndDevice,
        exits: List<ProcessExit>,
        unfinished: ReadingSession?,
    ): String = buildString {
        appendLine("Audora diagnostics")
        appendLine("Created: ${DiagnosticsText.timestamp(createdAtMillis)}")
        appendLine("App: ${build.applicationId} ${build.versionName} (${build.versionCode})")
        appendLine("Phone: ${build.manufacturer} ${build.model}, Android ${build.androidRelease} (SDK ${build.sdkInt})")
        appendLine()
        appendLine("Unfinished reading session:")
        appendLine("  ${unfinished?.let(DiagnosticsText::describe) ?: "none"}")
        appendLine()
        appendLine("Recent process exits, newest first:")
        if (exits.isEmpty()) appendLine("  none recorded")
        exits.forEach { appendLine("  ${DiagnosticsText.describe(it)}") }
        appendLine()
        appendLine("Log files follow, oldest first.")
    }
}
