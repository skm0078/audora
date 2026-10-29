// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderPorts.kt
// Purpose: The reader's view of the library and of its saved settings, as small seams the tests can fake.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.core.resume.ResumePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ReaderBooks {
    suspend fun find(id: BookId): Book?

    /** Records the page count and moves the book to the top of the library. Failures are logged, not shown. */
    suspend fun markOpened(id: BookId, pageCount: Int)
}

/** Where this book was left (P1-06): its resume anchor re-anchored on the reader's own copy of the pages. */
fun interface ReaderResume {
    /** Null when the book was never read. [unitsOf] returns null for a page that cannot be read now. */
    suspend fun locate(id: BookId, pageCount: Int, unitsOf: suspend (Int) -> List<ReadingUnit>?): ResumePoint?

    /** When the place was last saved, for "Previously, N days ago"; null when there is none (or it is not known). */
    suspend fun savedAtMillis(id: BookId): Long? = null

    companion object {
        val None = ReaderResume { _, _, _ -> null }
    }
}

class LocatorReaderResume(private val locator: ResumeLocator, private val anchors: ResumeAnchorStore) : ReaderResume {
    override suspend fun locate(id: BookId, pageCount: Int, unitsOf: suspend (Int) -> List<ReadingUnit>?) =
        locator.locate(id, pageCount, unitsOf)

    override suspend fun savedAtMillis(id: BookId): Long? = anchors.get(id)?.savedAtMillis
}

/** The last speed the listener picked, kept across app restarts. */
interface SpeedPreference {
    suspend fun load(): Float?

    suspend fun save(speed: Float)
}

/** Page or text view, as the listener last left it (P4-03). */
interface ViewPreference {
    suspend fun loadView(): ReaderView?

    suspend fun saveView(view: ReaderView)

    companion object {
        /** Nothing saved, nothing kept: every book opens in the default view. */
        val None = object : ViewPreference {
            override suspend fun loadView(): ReaderView? = null

            override suspend fun saveView(view: ReaderView) = Unit
        }
    }
}

/** "Keep screen on while reading aloud": off by default, since a lit screen costs battery. */
interface ScreenPreference {
    fun keepScreenOn(): Flow<Boolean>

    suspend fun saveKeepScreenOn(on: Boolean)

    /** Volume keys move by sentence while reading aloud in the reader (plan item 20); off by default. */
    fun volumeKeysSkip(): Flow<Boolean> = flowOf(false)

    suspend fun saveVolumeKeysSkip(on: Boolean) = Unit

    /** "ListenLock, pause" and the other spoken commands in the reader (plan item 38); off by default (battery). */
    fun voiceCommands(): Flow<VoiceCommandsMode> = flowOf(VoiceCommandsMode.OFF)

    suspend fun saveVoiceCommands(mode: VoiceCommandsMode) = Unit
}

/** When voice commands listen (plan item 38). */
enum class VoiceCommandsMode {
    OFF,

    /** While the reader is on screen; Android shows the microphone indicator meanwhile. */
    WHILE_VISIBLE,

    /** Also with the screen off, from a microphone foreground service with its own notification (opt-in). */
    SCREEN_OFF,
    ;

    /**
     * What actually listens: nothing without the microphone (a mode restored from a backup, or a permission taken
     * back), and only on screen without notifications, since the screen-off notification is where Stop lives.
     */
    fun effective(microphone: Boolean, notifications: Boolean): VoiceCommandsMode = when {
        this == OFF || !microphone -> OFF
        this == SCREEN_OFF && !notifications -> WHILE_VISIBLE
        else -> this
    }

    companion object {
        /** The mode saved under [name]; null for nothing saved or a name this build doesn't know. */
        fun fromSaved(name: String?): VoiceCommandsMode? = entries.firstOrNull { it.name == name }
    }
}

class LibraryReaderBooks(private val library: Library) : ReaderBooks {
    override suspend fun find(id: BookId): Book? = library.book(id)

    override suspend fun markOpened(id: BookId, pageCount: Int) {
        val marked = library.markOpened(id, pageCount)
        if (marked is Outcome.Failure) {
            AudoraLog.w(
                TAG,
                "Could not record the open, book=${id.logLabel}: ${marked.error}",
            )
        }
    }

    private companion object {
        const val TAG = "Audora.Reader"
    }
}
