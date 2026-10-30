// File: baselineprofile/src/main/kotlin/io/github/skm0078/audora/baselineprofile/AppScreens.kt
// Purpose: The few screens the generator walks through, driven with UiAutomator by the words a listener sees.

package io.github.skm0078.audora.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal class AppScreens(private val device: UiDevice, private val app: AppUnderTest) {

    /** A fresh install opens on first run; Skip finishes it. Later rounds start on the library and skip this. */
    fun finishFirstRunIfShown() {
        val skip = By.text(app.text("first_run_skip"))
        val button = device.wait(Until.findObject(skip), FIRST_RUN_WAIT_MS) ?: return
        button.click()
        awaitGone(skip, "first run to close after Skip")
    }

    /** Waits for the reader's Read aloud button, which shows once the book's first page is ready. */
    fun awaitReader() = await(By.desc(app.text("reader_play")), "the reader to open the book", READER_WAIT_MS)

    /** Swipes [count] pages forward, letting each turn finish. */
    fun turnPages(count: Int) {
        val y = device.displayHeight / 2
        val fromX = device.displayWidth * SWIPE_FROM / PERCENT
        val toX = device.displayWidth * SWIPE_TO / PERCENT
        repeat(count) {
            device.swipe(fromX, y, toX, y, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    /** Back until the tab bar shows: the library, where the next round starts from. */
    fun backToLibrary() {
        val tab = By.text(app.text("tab_you"))
        repeat(MAX_BACKS) {
            if (device.hasObject(tab)) return
            device.pressBack()
            device.waitForIdle()
        }
        await(tab, "the library's tab bar after going back", UI_WAIT_MS)
    }

    private fun await(selector: BySelector, what: String, timeoutMs: Long) {
        checkNotNull(device.wait(Until.findObject(selector), timeoutMs)) { "Timed out after $timeoutMs ms: $what" }
    }

    private fun awaitGone(selector: BySelector, what: String) {
        check(device.wait(Until.gone(selector), UI_WAIT_MS)) { "Timed out after $UI_WAIT_MS ms: $what" }
    }

    private companion object {
        const val FIRST_RUN_WAIT_MS = 5_000L
        const val UI_WAIT_MS = 10_000L

        // Opening reads the PDF's text and draws the page; CI's emulator is slow at both.
        const val READER_WAIT_MS = 30_000L
        const val MAX_BACKS = 5
        const val PERCENT = 100
        const val SWIPE_FROM = 80
        const val SWIPE_TO = 20
        const val SWIPE_STEPS = 20
    }
}
