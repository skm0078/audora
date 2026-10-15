// File: app/src/test/kotlin/io/github/skm0078/audora/diagnostics/ExitInfoMappingTest.kt
// Purpose: Android's exit reasons and importance levels map to the names written in the log.

package io.github.skm0078.audora.diagnostics

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import io.github.skm0078.audora.core.diagnostics.ProcessExitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ExitInfoMappingTest {
    @Test
    fun `maps the reasons that matter for background playback`() {
        val cases = mapOf(
            ApplicationExitInfo.REASON_SIGNALED to ProcessExitKind.SIGNALED,
            ApplicationExitInfo.REASON_LOW_MEMORY to ProcessExitKind.LOW_MEMORY,
            ApplicationExitInfo.REASON_CRASH to ProcessExitKind.CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE to ProcessExitKind.CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR to ProcessExitKind.ANR,
            ApplicationExitInfo.REASON_USER_REQUESTED to ProcessExitKind.USER_REQUESTED,
            ApplicationExitInfo.REASON_FREEZER to ProcessExitKind.FREEZER,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED to ProcessExitKind.PACKAGE_UPDATED,
        )
        cases.forEach { (reason, kind) -> assertEquals("reason $reason", kind, ExitInfoMapping.kindOf(reason)) }
    }

    @Test
    fun `unknown future reason maps to UNKNOWN`() {
        assertEquals(ProcessExitKind.UNKNOWN, ExitInfoMapping.kindOf(999))
    }

    @Test
    fun `importance label keeps the raw number`() {
        assertEquals(
            "FOREGROUND_SERVICE(125)",
            ExitInfoMapping.importanceLabel(RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE),
        )
        assertEquals("OTHER(42)", ExitInfoMapping.importanceLabel(42))
    }
}
