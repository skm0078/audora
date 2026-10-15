// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsFixtures.kt
// Purpose: Shared builders for diagnostics tests.

package io.github.skm0078.audora.core.diagnostics

fun exit(timestampMillis: Long, kind: ProcessExitKind = ProcessExitKind.SIGNALED, description: String? = null) =
    ProcessExit(
        timestampMillis = timestampMillis,
        kind = kind,
        status = if (kind == ProcessExitKind.SIGNALED) 9 else 0,
        importance = "FOREGROUND_SERVICE(125)",
        processName = "io.github.skm0078.audora",
        description = description,
        pssKb = 120_000,
        rssKb = 180_000,
    )
