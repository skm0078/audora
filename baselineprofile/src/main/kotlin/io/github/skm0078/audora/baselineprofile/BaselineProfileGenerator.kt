// File: baselineprofile/src/main/kotlin/io/github/skm0078/audora/baselineprofile/BaselineProfileGenerator.kt
// Purpose: Records which of the app's code a listener's everyday path runs (start, first run, open a PDF, turn
//          pages). Shipped as app/src/main/baseline-prof.txt, it lets Android compile that code at install, so the
//          first launches and first page turns do not run it slowly through the interpreter (ADR 2026-10-30-baseline-
//          profile). Run by scripts/ci/baseline_profile.sh on an Android 13+ emulator; not part of the normal tests.

package io.github.skm0078.audora.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startOpenABookAndTurnPages() {
        val app = AppUnderTest.fromArguments()
        val book = app.downloadedPdf(BOOK)
        // The rule stops the app before each round, so every round starts cold and walks the same path; it keeps the
        // code seen in every round.
        rule.collect(packageName = app.packageName) {
            pressHome()
            startActivityAndWait()
            val screens = AppScreens(device, app)
            screens.finishFirstRunIfShown()
            app.openWith(book)
            screens.awaitReader()
            screens.turnPages(PAGE_TURNS)
            screens.leaveReader()
        }
    }

    private companion object {
        const val BOOK = "en-udhr-noto.pdf"
        const val PAGE_TURNS = 3
    }
}
