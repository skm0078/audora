// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/MadeJourney.kt
// Purpose: Release journey step 32 (ADR 2026-10-28-made-tab): a saved audiobook in Made plays in the book player's
//          look, with the same Voice and speed sheet and a sleep timer the saved-audio service keeps.

package io.github.skm0078.audora.release

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.export.AacAudioSink
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.BookTags
import io.github.skm0078.audora.playback.export.LocalChapterFiles
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class MadeJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForNode(matcher)
        return rule.onAllNodes(matcher).onFirst()
    }

    private fun tap(matcher: SemanticsMatcher) = node(matcher).performClick()

    private fun gone(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    /**
     * A one-chapter audiobook of the journey's PDF, written by the app's own encoder into Downloads and listed as
     * made: Made shows it with its icon buttons, Play opens the player with Speed and the sleep timer under Play,
     * Speed opens the book player's Voice and speed sheet with the saved file's voice note, and End of chapter shows
     * on the timer once the service has it, Off once it is cleared. The file and its listing go again at the end.
     */
    fun aSavedAudiobookPlaysInTheSamePlayer() {
        val entry = LibraryEntryPoint.of(app)
        val book = runBlocking { entry.library().books().first() }.first { it.format == DocumentFormat.PDF }
        val uri = savedChapter(book)
        try {
            // Two chapters of the same file: the timer offers chapter ends only for a set of more than one.
            val outputs = (1..2).map { part ->
                BookOutput(
                    book.id, OutputKind.AUDIOBOOK, SET_KEY, part, "Journey chapter $part.m4a", uri.toString(),
                    "audio/mp4", createdAtMillis = System.currentTimeMillis(),
                    durationMs =
                        CHAPTER_SECONDS * MS_PER_SECOND,
                )
            }
            assertTrue(runBlocking { entry.outputs().add(outputs) } is Outcome.Success)
            backToTabs()
            tap(hasText(text(R.string.tab_made)))
            val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: text(R.string.library_untitled)
            val named = "$title, ${text(R.string.made_kind_audiobook)}"
            node(hasContentDescription(text(R.string.made_share_named, named))).assertExists()
            tap(hasContentDescription(text(R.string.made_play_named, named)))

            // Speed and the timer wait for the player service, then work as on the book player.
            tap(hasText(text(R.string.reader_speed, "1")) and isEnabled())
            waitForNode(hasText(text(R.string.made_voice_title)))
            tap(sheetHandle())
            gone(hasText(text(R.string.made_voice_title)))

            val timer = hasContentDescription(text(R.string.reader_sleep_timer))
            tap(timer)
            tap(hasText(text(R.string.reader_sleep_end_of_chapter)))
            // The service publishes the timer back, and the button says it.
            waitForNode(timer and hasStateDescription(text(R.string.reader_sleep_end_of_chapter)))
            tap(timer)
            tap(hasText(text(R.string.reader_sleep_off)))
            waitForNode(timer and hasStateDescription(text(R.string.reader_sleep_off)))
            tap(hasContentDescription(text(R.string.made_player_pause)))
            backToTabs()
        } finally {
            runBlocking { entry.outputs().deleteSet(SET_KEY) }
            app.contentResolver.delete(uri, null, null)
        }
    }

    /** The sheet's own handle: Material's scrim behind every sheet carries the same name and fills the screen. */
    private fun sheetHandle(): SemanticsMatcher = hasContentDescription(text(R.string.a11y_sheet_close)) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Half a minute of quiet, encoded as the app encodes a chapter, then put where saved audiobooks live. */
    private fun savedChapter(book: Book): Uri {
        val folder = File(app.cacheDir, "journey-made").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val sink = AacAudioSink(LocalChapterFiles(folder), 1, book = BookTags(book.title, book.author, null))
            val writer = requireNotNull(sink.open(AudioChapter(1, 0, 0), RATE).getOrNull()) { "No encoder" }
            assertTrue(writer.write(PcmAudio(ShortArray(RATE * CHAPTER_SECONDS), RATE)) is Outcome.Success)
            val exported = requireNotNull(writer.finish().getOrNull()) { "Chapter not finished" }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "listenlock-journey.m4a")
                put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
            }
            val resolver = app.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No row" }
            requireNotNull(resolver.openOutputStream(uri)) { "Cannot write" }.use { out ->
                File(folder, exported.name).inputStream().use { it.copyTo(out) }
            }
            return uri
        } finally {
            folder.deleteRecursively()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val RATE = 22_050

        // Long enough to still be playing when the step ends, so it pauses what it started.
        const val CHAPTER_SECONDS = 30
        const val MS_PER_SECOND = 1_000L
        const val SET_KEY = "journey-made"
        const val FOLDER = "Download/ListenLock-journey"
    }
}
