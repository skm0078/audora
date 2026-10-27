// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/brain/StoryBrainReader.kt
// Purpose: Runs the opt-in story brain (ADR story-brain) over a book's pages ahead of the listener: who says each
//          spoken line and how, kept for the renderer and on disk. With no model, it answers nothing and the rules read
//          as before.

package io.github.skm0078.audora.playback.brain

import android.os.Process
import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.StoryBrainConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.PROSE
import io.github.skm0078.audora.speech.brain.BrainLine
import io.github.skm0078.audora.speech.brain.BrainModel
import io.github.skm0078.audora.speech.brain.BrainRequest
import io.github.skm0078.audora.speech.brain.StoryBrainAnswer
import io.github.skm0078.audora.speech.brain.StoryBrainPrompt
import io.github.skm0078.audora.speech.route.QuoteSplitter
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where the reader gets the model. [load] runs on the reader's thread, once per book until it gives one; null when
 * the brain is not installed or does not load. [installed] is asked before each page, so a deleted model is let go.
 */
class BrainSource(val load: () -> BrainModel?, val installed: () -> Boolean)

/**
 * One per playback service. The session tells it each page it extracts ([page]); the reader asks the model about that
 * page and the [StoryBrainConfig.pagesAhead] after it (fetched with the book's [open] lookup), one page at a time on
 * its own low-priority thread, so a page's notes are usually ready before it plays. A sentence asked about before its
 * page is read gets no note ([noteOf] is null) and keeps the rules' call, as with no brain at all.
 *
 * Only spoken lines are asked about (a page of narration costs nothing), and only while [wanted] (character voices or
 * "Read to suit the book" on). Notes are checked against the sentence's text, so a page read differently (another
 * pipeline version) is never misapplied. Thread-safe: [open], [page] and [close] from any thread; [noteOf] from the
 * render thread.
 */
@Suppress("TooManyFunctions") // The page steps are kept small and named rather than one long loop.
class StoryBrainReader(
    private val config: StoryBrainConfig,
    private val source: BrainSource,
    private val store: BrainNotesStore?,
    private val wanted: () -> Boolean,
    private val unitConfig: ReadingUnitConfig = ReadingUnitConfig(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, THREAD_NAME)
    },
) : AutoCloseable {
    private val notes = ConcurrentHashMap<UnitKey, StoredNote>()
    private val generation = AtomicInteger()
    private val splitter = QuoteSplitter(DialogueConfig().copy(minQuoteWords = 1))

    @Volatile
    private var book: OpenBook? = null

    /** The last page the session extracted: pages far from it are no longer worth the model's time. */
    @Volatile
    private var focus = 0

    /** Touched only on the reader's thread. */
    private var model: BrainModel? = null
    private var modelTried = false
    private val done = HashSet<Int>()
    private val cast = LinkedHashMap<String, SpeakerGender>()
    private val lastProse = HashMap<Int, List<String>>()

    /** One sentence as asked about: its unit, its line in the question, and its quoted words. */
    private class Line(val unit: ReadingUnit, val brain: BrainLine, val quoted: String)

    private class OpenBook(val key: String, val pages: (Int) -> List<ReadingUnit>?, val generation: Int)

    /**
     * A book opened. [key]: its library id (the notes file's name comes from its hash); [pages]: a page's units for
     * reading ahead, blocking, or null when the page cannot be had cheaply (scanned, unreadable, the book closed).
     */
    fun open(key: String, pages: (Int) -> List<ReadingUnit>?) {
        val opened = OpenBook(key, pages, generation.incrementAndGet())
        book = opened
        notes.clear()
        focus = 0
        model?.cancel()
        submit { reset(opened) }
    }

    /** One extracted page; [scanned] pages are not read ahead of (each look-ahead page would be OCR'd twice). */
    fun page(units: List<ReadingUnit>, scanned: Boolean) {
        val opened = book
        val page = units.firstOrNull()?.page
        if (opened == null || page == null) return
        focus = page
        if (wanted()) {
            submit {
                if (live(opened)) {
                    read(opened, page, units)
                    if (!scanned) ahead(opened, page)
                }
            }
        }
    }

    /** The brain's call on [unit], or null: no note (not read yet, narration, no brain), or a note for other text. */
    fun noteOf(unit: ReadingUnit): StoredNote? = notes[UnitKey.of(unit)]?.takeIf { it.textHash == unit.text.hashCode() }

    /** Who says [unit], when the brain named someone; null too when the name it gave was the one spoken to. */
    fun speakerOf(unit: ReadingUnit): Speaker? =
        noteOf(unit)?.takeIf { it.speaker.isNotEmpty() }?.let { Speaker(it.speaker, it.gender) }

    /** The feeling the brain heard in [unit]: [Feeling.NEUTRAL] when it heard none, null when it was not asked. */
    fun feelingOf(unit: ReadingUnit): Feeling? = noteOf(unit)?.let { it.feeling ?: Feeling.NEUTRAL }

    override fun close() {
        generation.incrementAndGet()
        book = null
        model?.cancel()
        submit {
            model?.close()
            model = null
        }
        executor.shutdown()
    }

    private fun submit(task: () -> Unit) {
        try {
            executor.execute {
                @Suppress("TooGenericExceptionCaught") // One bad page must not stop the brain for the whole service.
                try {
                    task()
                } catch (e: RuntimeException) {
                    AudoraLog.e(TAG, "Story brain step failed", e)
                }
            }
        } catch (e: RejectedExecutionException) {
            AudoraLog.d(TAG, "Story brain closed; step dropped: ${e.message}")
        }
    }

    private fun live(opened: OpenBook): Boolean = opened.generation == generation.get()

    private fun reset(opened: OpenBook) {
        if (!live(opened)) return
        done.clear()
        cast.clear()
        lastProse.clear()
        if (model == null) modelTried = false
        val saved = store?.load(opened.key) ?: return
        done += saved.pages
        saved.notes.forEach { note ->
            notes[UnitKey(note.page, note.index)] = note
            remember(note.speaker, note.gender)
        }
        AudoraLog.i(TAG, "Story brain notes: ${saved.pages.size} pages read before")
    }

    private fun ahead(opened: OpenBook, page: Int) {
        for (next in page + 1..page + config.pagesAhead) {
            if (!live(opened) || next in done) continue
            val units = opened.pages(next) ?: return
            read(opened, next, units)
        }
    }

    /** Asks about one page, unless it was read before or the listener has moved well away from it. */
    private fun read(opened: OpenBook, page: Int, units: List<ReadingUnit>) {
        // The loader extracts a little ahead of the playhead, so the page playing can be a few behind the last one.
        if (page in done || page !in focus - config.pagesAhead..focus + config.pagesAhead) return
        val lines = lines(units)
        lastProse[page] = units.filter { it.kind in PROSE }.takeLast(config.earlierSentences).map { it.text }
        val found = if (lines.none { it.brain.spoken }) {
            emptyList()
        } else {
            model()?.let { brain -> answers(opened, brain, page, lines) }
        }
        if (found != null) finish(opened, page, found)
    }

    /** Every note on the page, or null when an answer did not come or another book opened meanwhile. */
    private fun answers(opened: OpenBook, brain: BrainModel, page: Int, lines: List<Line>): List<StoredNote>? {
        val started = System.nanoTime()
        val found = ArrayList<StoredNote>()
        for (chunk in chunks(lines)) {
            val chunkNotes = ask(brain, page, chunk)
            if (chunkNotes == null || !live(opened)) return null
            found += chunkNotes
        }
        AudoraLog.i(
            TAG,
            "Story brain page $page: ${found.size} lines in ${(System.nanoTime() - started) / NANOS_PER_MS} ms",
        )
        return found
    }

    /** Null when the answer did not come (cancelled or failed): the page is left unread, to try again later. */
    private fun ask(brain: BrainModel, page: Int, chunk: List<Line>): List<StoredNote>? {
        val lang = chunk.filter { it.brain.spoken }.groupingBy { it.unit.lang }.eachCount().maxByOrNull { it.value }
            ?.key ?: Lang.EN
        val request = StoryBrainPrompt.build(lang, chunk.map { it.brain }, lastProse[page - 1].orEmpty(), knownCast())
        val answer = request?.let { brain.answer(it.prompt, it.grammar, it.maxTokens) }
        return when {
            request == null -> emptyList()
            answer == null -> null
            else -> notesOf(page, chunk, request, answer)
        }
    }

    private fun notesOf(page: Int, chunk: List<Line>, request: BrainRequest, answer: String): List<StoredNote> {
        val parsed = StoryBrainAnswer.parse(answer, request, knownCast())
        parsed.cast.forEach { (name, gender) -> remember(name, gender) }
        return parsed.notes.map { (number, note) ->
            val line = chunk[number - 1]
            // A line that calls a person by name is said to them, not by them ("Of us, Felix"): its feeling is kept.
            val name = note.speaker.name.orEmpty().takeUnless { addressed(it, line.quoted) }.orEmpty()
            remember(name, note.speaker.gender)
            StoredNote(page, line.unit.index, line.unit.text.hashCode(), name, note.speaker.gender, note.feeling)
        }
    }

    private fun finish(opened: OpenBook, page: Int, found: List<StoredNote>) {
        if (!live(opened)) return
        found.forEach { notes[UnitKey(it.page, it.index)] = it }
        done += page
        store?.append(opened.key, page, found)
    }

    /** The loaded model, loading it on first need; null (and a let-go model) when it is not installed. */
    private fun model(): BrainModel? {
        if (!source.installed()) {
            model?.close()
            model = null
            modelTried = false
            return null
        }
        if (model == null && !modelTried) {
            modelTried = true
            model = source.load()
        }
        return model
    }

    /** Whether [name] is a word of the [quoted] speech (by its bare words: "Felix" in "Of us, Felix, and…"). */
    private fun addressed(name: String, quoted: String): Boolean {
        val words = quoted.lowercase().split(NOT_WORD).toSet()
        return name.lowercase().split(NOT_WORD).any { it.length > 1 && it !in ARTICLES && it in words }
    }

    /** Each unit with its line: spoken when prose with a quote in it, or inside one its paragraph opened earlier. */
    private fun lines(units: List<ReadingUnit>): List<Line> {
        var paragraph = 0
        var open = false
        return units.mapIndexed { i, unit ->
            val previous = units.getOrNull(i - 1)
            val same = previous != null && previous.kind == unit.kind &&
                previous.pauseAfterMs <= unitConfig.sentencePauseMs
            if (previous != null && !same) paragraph++
            val prose = unit.kind in PROSE
            val startsOpen = open && same
            val spoken = prose && (startsOpen || splitter.split(unit.text, startsOpen).any { it.quoted })
            open = prose && splitter.endsInQuote(unit.text, startsOpen)
            val quoted = if (spoken) {
                splitter.split(unit.text, startsOpen).filter {
                    it.quoted
                }.joinToString(" ") { it.text }
            } else {
                ""
            }
            Line(unit, BrainLine(unit.text, spoken, paragraph), quoted)
        }
    }

    /** The page in parts of whole paragraphs, each under [StoryBrainConfig.maxPageChars]. */
    private fun chunks(lines: List<Line>): List<List<Line>> {
        val paragraphs = lines.groupBy { it.brain.paragraph }.values
        val out = mutableListOf<MutableList<Line>>()
        var chars = 0
        paragraphs.forEach { paragraph ->
            val size = paragraph.sumOf { it.brain.text.length }
            if (out.isEmpty() || chars + size > config.maxPageChars) {
                out.add(mutableListOf())
                chars = 0
            }
            out.last().addAll(paragraph)
            chars += size
        }
        return out
    }

    /** A speaker just heard moves to the end; only the [StoryBrainConfig.maxCast] most recent are kept. */
    private fun remember(name: String, gender: SpeakerGender) {
        if (name.isBlank()) return
        val known = cast.remove(name)
        cast[name] = if (gender == SpeakerGender.UNKNOWN) known ?: gender else gender
        while (cast.size > config.maxCast) cast.remove(cast.keys.first())
    }

    private fun knownCast(): Map<String, SpeakerGender> = LinkedHashMap(cast)

    private companion object {
        const val TAG = "Playback.Brain"
        const val THREAD_NAME = "audora-story-brain"
        const val NANOS_PER_MS = 1_000_000L
        val NOT_WORD = Regex("""[^\p{L}\p{M}\p{Nd}]+""")
        val ARTICLES = setOf("the", "a", "an")
    }
}
