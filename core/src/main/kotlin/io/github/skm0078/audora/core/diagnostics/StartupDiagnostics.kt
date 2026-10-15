// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/StartupDiagnostics.kt
// Purpose: At process start, logs every process death since the last run and flags a read that was cut off.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogLevel

/**
 * Android records why each app process died (crash, ANR, low memory, an OEM task killer's signal, force
 * stop) but only tells the next process. Logging those records at startup puts the cause of a stop into
 * the shared log even when the dying process had no chance to write anything.
 */
class StartupDiagnostics(
    private val exitSource: ProcessExitSource,
    private val store: DiagnosticsStateStore,
    private val tracker: ReadingSessionTracker,
) {
    fun run(): StartupReport {
        val exits = exitSource.recentExits()
        val newExits = logNewExits(exits)
        val unfinished = tracker.unfinishedSession()
        val cause = unfinished?.let { session -> likelyCause(session, exits) }
        if (unfinished != null) {
            val causeText = cause?.let(DiagnosticsText::describe) ?: "unknown (Android recorded no exit)"
            AudoraLog.w(TAG, "Reading stopped unexpectedly: ${DiagnosticsText.describe(unfinished)}; cause: $causeText")
        }
        return StartupReport(newExits, unfinished, cause)
    }

    private fun logNewExits(exits: List<ProcessExit>): List<ProcessExit> {
        val lastLogged = store.lastLoggedExitMillis()
        val fresh = exits.filter { it.timestampMillis > lastLogged }.sortedBy { it.timestampMillis }
        fresh.forEach { exit ->
            val level = if (exit.kind.expected) LogLevel.INFO else LogLevel.WARN
            AudoraLog.log(level, TAG, "Previous process ended: ${DiagnosticsText.describe(exit)}", null)
        }
        fresh.lastOrNull()?.let { newest -> store.setLastLoggedExitMillis(newest.timestampMillis) }
        return fresh
    }

    /** The newest exit recorded after the session started: the death that cut the reading off. */
    private fun likelyCause(session: ReadingSession, exits: List<ProcessExit>): ProcessExit? =
        exits.filter { it.timestampMillis >= session.startedAtMillis }.maxByOrNull { it.timestampMillis }

    private companion object {
        const val TAG = "Diagnostics"
    }
}

/**
 * @property newExits exits logged by this run, oldest first.
 * @property unfinishedSession the read a previous process was doing when it died, if any.
 * @property likelyCause the exit that ended [unfinishedSession], when Android recorded one.
 */
data class StartupReport(
    val newExits: List<ProcessExit>,
    val unfinishedSession: ReadingSession?,
    val likelyCause: ProcessExit?,
)
