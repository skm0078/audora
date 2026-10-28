// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/CharacterCast.kt
// Purpose: Gives each character of a book a voice of their own and keeps it for that book (plan item 63).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.route.CharacterVoices
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.CastPartners
import io.github.skm0078.audora.speech.sherpa.CastSpeaker
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** Where one book's character voices are kept: character key (language and name) to the voice's name. */
interface CharacterFiles {
    fun load(book: BookId): Map<String, String>

    fun save(book: BookId, cast: Map<String, String>)
}

/**
 * Characters are read by the other speakers of the narrator's own model ([CastSpeakers]): Kokoro's English model
 * holds 27 more, Kitten's 7, all loaded with the narrator, so a full cast costs no memory and no model switch. A
 * one-speaker narrator has no cast of its own.
 *
 * A character of a gender the narrator's model cannot voice, and the narrator is not, borrows a voice from one other
 * model installed for the language ([partners]): a SYSPIN male narrator's women are read by the SYSPIN female voice.
 * That model is loaded beside the narrator's the first time such a character speaks. A character of the narrator's
 * own gender with no voice left keeps the narrator's: the right gender, if not a voice of their own.
 *
 * A character's first voice is the least used one of their gender, ties broken by the name's hash so a book sounds
 * the same on every phone; it is kept for the book ([open]) from then on, per narrator model, so switching the
 * narrator away and back finds the same cast. A character whose gender no tag or pronoun has told yet keeps the
 * dialogue voice until it is known: Tom in a woman's voice is worse than no voice of his own. When a later line
 * tells a gender that the kept voice does not have (an early guess was wrong), the character is cast again. The
 * voices [taken] for dialogue or headings are never given to a character.
 *
 * The kept voices live in [casts], one per book for the whole process, so playback and the audiobook export share
 * them and never save over each other ([CharacterCasts]). Build the app's casts with `forReading`, so both read
 * characters the same way.
 *
 * Thread-safe: [open] runs on the main thread, [voiceFor] on the render worker. A renderer takes [current] before it
 * looks up the sentence's speaker, so a book opened meanwhile never gets the old book's character.
 */
class CharacterCast(
    private val narrator: (Lang) -> VoiceId?,
    private val speakers: (VoiceId) -> List<CastSpeaker>,
    private val taken: (Lang) -> Set<VoiceId> = { emptySet() },
    private val casts: CharacterCasts,
    private val partners: (VoiceId) -> CastPartners = { CastPartners.NONE },
) : CharacterVoices {
    private val lock = Any()
    private var book: BookCast? = null
    private var opened = 0

    /** A book opened: its characters' voices as they were left, shared with any other cast of that book. */
    fun open(id: BookId) {
        val cast = casts.of(id).also { it.edit { } } // loads the file here, on the caller's thread
        synchronized(lock) {
            book = cast
            opened++
        }
    }

    override fun current(): CharacterVoices {
        val at = synchronized(lock) { opened }
        return CharacterVoices { lang, speaker -> voiceFor(lang, speaker, at) }
    }

    override fun voiceFor(lang: Lang, speaker: Speaker): VoiceId? = voiceFor(lang, speaker, null)

    private fun voiceFor(lang: Lang, speaker: Speaker, at: Int?): VoiceId? {
        val reader = narrator(lang)?.takeIf { it.engine == EngineId.SHERPA } ?: return null
        // The narrator may itself be another speaker of its model (a built-in voice's speaker, ADR-052).
        val base = CastSpeakers.parse(reader).first
        val voices = castVoices(base, reader, lang)
        val key = keyOf(base, lang, speaker)
        // A renderer's snapshot from before another book opened gets no voice and keeps nothing.
        val kept = synchronized(lock) { book.takeIf { at == null || at == opened } }
        return kept?.edit { assigned ->
            val known = assigned[key]?.takeIf { it in voices }
            val wrong = known != null && speaker.gender != SpeakerGender.UNKNOWN &&
                voices.getValue(known) != speaker.gender
            when {
                known != null && !wrong -> known
                speaker.gender == SpeakerGender.UNKNOWN -> null
                else -> firstVoice(assigned, key, speaker, voices)?.also { assigned[key] = it }
            }
        }
    }

    /**
     * Each voice a character may get and its gender: the other speakers of the narrator's model, and for a gender it
     * has none of (and the narrator is not), those of the first partner model that has one.
     */
    private fun castVoices(base: VoiceId, reader: VoiceId, lang: Lang): Map<VoiceId, SpeakerGender> {
        val excluded = taken(lang) + reader
        val own = speakers(base).associate { CastSpeakers.variant(base, it.speakerId) to it.gender } - excluded
        val missing = GENDERS.filter { gender -> gender !in own.values }
        // The partner models are listed only when a gender is missing: listing them reads the installed voices.
        val partners = if (missing.isEmpty()) CastPartners.NONE else partners(reader)
        if (partners.narrator == SpeakerGender.UNKNOWN) return own
        val borrowed = missing.filter { it != partners.narrator }.flatMap { gender ->
            val ofGender = partners.voices.filter { it.gender == gender && it.voice !in excluded }
            val model = ofGender.firstOrNull()?.let { CastSpeakers.parse(it.voice).first }
            ofGender.filter { CastSpeakers.parse(it.voice).first == model }
        }
        return own + borrowed.associate { it.voice to it.gender }
    }

    /** The least used voice of [speaker]'s gender, the character's own current voice not counted. */
    private fun firstVoice(
        assigned: Map<String, VoiceId>,
        key: String,
        speaker: Speaker,
        voices: Map<VoiceId, SpeakerGender>,
    ): VoiceId? {
        val pool = voices.filterValues { it == speaker.gender }.keys.toList()
        val uses = assigned.filterKeys { it != key }.values.groupingBy { it }.eachCount()
        val fewest = pool.minOfOrNull { uses[it] ?: 0 } ?: return null
        val least = pool.filter { (uses[it] ?: 0) == fewest }
        return least[Math.floorMod((speaker.key ?: speaker.gender.name).hashCode(), least.size)]
    }

    private fun keyOf(base: VoiceId, lang: Lang, speaker: Speaker): String =
        listOf(base.name, lang.name, speaker.key ?: "$GENERIC${speaker.gender.name}").joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"
        private const val GENERIC = "#"
        private val GENDERS = listOf(SpeakerGender.FEMALE, SpeakerGender.MALE)
    }
}
