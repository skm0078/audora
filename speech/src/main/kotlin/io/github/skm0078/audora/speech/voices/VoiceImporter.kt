// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceImporter.kt
// Purpose: Imports a sherpa-onnx voice folder the user picked (.onnx + tokens.txt [+ espeak-ng-data]) as a voice.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.sherpa.SherpaFamily
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** A folder tree to import from: the Storage Access Framework tree in the app, a plain folder in tests. */
interface ImportTree {
    /** The picked folder's name, the default label of the voice. */
    val name: String

    /** Files under the folder as `/`-separated relative paths, at most [maxDepth] folders deep, at most [limit]. */
    @Throws(IOException::class)
    fun files(maxDepth: Int, limit: Int): List<ImportFile>

    @Throws(IOException::class)
    fun open(path: String): InputStream
}

data class ImportFile(val path: String, val sizeBytes: Long)

/** Why a folder cannot become a voice. [message] is shown to the user as is. */
sealed interface ImportError {
    val message: String

    data object NoModel : ImportError {
        override val message = "There is no .onnx model in this folder. Pick the folder that holds the voice's files."
    }

    data class SeveralModels(val names: List<String>) : ImportError {
        override val message: String
            get() = "This folder holds several models (${names.joinToString()}). Pick a folder with one voice."
    }

    data object NoTokens : ImportError {
        override val message = "tokens.txt is missing next to the model."
    }

    data object KittenWithoutEspeak : ImportError {
        override val message = "This KittenTTS voice needs its espeak-ng-data folder next to the model."
    }

    /** A model family Audora cannot load yet (Kokoro, Matcha, lexicon-based voices…). */
    data class Unsupported(val marker: String) : ImportError {
        override val message: String
            get() = "This kind of model is not supported yet (it has $marker). " +
                "Audora imports Piper, VITS/MMS and KittenTTS voices."
    }

    data class TooBig(val bytes: Long, val limit: Long) : ImportError {
        override val message: String get() = "This folder is too big for one voice (${bytes.toMb()} MB)."
    }

    data object TooManyFiles : ImportError {
        override val message = "This folder has too many files to be one voice. Pick the voice's own folder."
    }

    data class NoSpace(val needed: Long, val free: Long) : ImportError {
        override val message: String
            get() = "Not enough space: the voice needs ${needed.toMb()} MB, the phone has ${free.toMb()} MB free."
    }

    data class Failed(val detail: String, val cause: Throwable? = null) : ImportError {
        override val message: String get() = "Could not import the voice: $detail"
    }
}

/**
 * Copies the files a voice needs from the picked folder into `installed/custom-…`, then swaps it in like a download.
 * The voice is found in the picked folder or, if that holds exactly one model somewhere below, in the model's folder
 * (people often pick the folder above the voice). espeak-ng-data keeps only the dictionaries Audora speaks.
 *
 * Supported: one `.onnx` + `tokens.txt` (Piper, VITS, MMS), plus `voices.bin` for KittenTTS. Anything with a lexicon
 * or a `dict/` folder (Kokoro, lexicon VITS) is refused: the engine would be handed a config it cannot honour, and
 * sherpa-onnx aborts the process on some bad configs rather than failing.
 *
 * @param newSuffix unique part of a new voice's id (a seam for tests).
 */
class VoiceImporter(
    private val files: VoiceFiles,
    private val config: VoiceDownloadConfig,
    private val freeBytes: (File) -> Long = { it.usableSpace },
    private val newSuffix: () -> String = { UUID.randomUUID().toString().take(SUFFIX_CHARS) },
) {
    fun import(tree: ImportTree, lang: Lang, label: String): Outcome<InstalledVoice, ImportError> {
        val listed = try {
            tree.files(config.maxImportDepth, config.maxImportFiles + 1)
        } catch (e: IOException) {
            return Outcome.Failure(ImportError.Failed("cannot read the folder: ${e.message}", e))
        }
        return when (val plan = plan(listed)) {
            is Outcome.Failure -> plan
            is Outcome.Success -> copy(tree, plan.value, lang, label.trim().ifEmpty { tree.name })
        }
    }

    /** Decides which files make the voice, or why there is none. Pure: no I/O. */
    internal fun plan(listed: List<ImportFile>): Outcome<ImportPlan, ImportError> {
        val modelDirs = listed.filter { it.path.endsWith(ONNX) }.map { parentOf(it.path) }.distinct()
        val root = modelDirs.singleOrNull()
        val problem = when {
            listed.size > config.maxImportFiles -> ImportError.TooManyFiles
            modelDirs.isEmpty() -> ImportError.NoModel
            root == null -> ImportError.SeveralModels(modelDirs.map { it.ifEmpty { "." } })
            else -> null
        }
        if (problem != null || root == null) return Outcome.Failure(problem ?: ImportError.NoModel)
        val prefix = if (root.isEmpty()) "" else "$root/"
        val local = listed.filter { it.path.startsWith(prefix) }.map { it.copy(path = it.path.removePrefix(prefix)) }
        return ImportRules.plan(local, prefix, config.maxUnpackedBytes)
    }

    private fun copy(
        tree: ImportTree,
        plan: ImportPlan,
        lang: Lang,
        label: String,
    ): Outcome<InstalledVoice, ImportError> {
        val id = "$CUSTOM_PREFIX${slug(label)}-${newSuffix()}"
        val staging = files.stagingDir(id)
        val needed = plan.bytes + config.installHeadroomBytes
        val free = freeBytes(files.root.apply { mkdirs() })
        if (free < needed) return Outcome.Failure(ImportError.NoSpace(needed, free))

        val spec = SherpaVoiceSpec(
            id = id,
            label = label.take(config.maxLabelChars),
            lang = lang,
            family = plan.family,
            model = plan.model,
            tokens = TOKENS,
            voices = plan.voices,
            dataDir = plan.dataDir,
            speakerId = 0,
            licence = CUSTOM_LICENCE,
            contentVersion = id,
        )
        val result = try {
            staging.deleteRecursively()
            plan.files.forEach { file -> copyOne(tree, plan.sourcePrefix + file, File(staging, file)) }
            when (val committed = files.commit(staging, spec, VoiceOrigin.CUSTOM)) {
                is Outcome.Success -> committed
                is Outcome.Failure -> Outcome.Failure(ImportError.Failed(committed.error))
            }
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Importing voice=$id failed", e)
            Outcome.Failure(ImportError.Failed(e.message ?: e.javaClass.simpleName, e))
        } finally {
            staging.deleteRecursively()
        }
        if (result is Outcome.Success) {
            AudoraLog.i(TAG, "Imported voice=$id (${plan.family}, ${lang.name}, ${plan.files.size} files)")
        }
        return result
    }

    private fun copyOne(tree: ImportTree, source: String, target: File) {
        target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("cannot create $it") }
        tree.open(source).use { input -> target.outputStream().use { input.copyTo(it, config.bufferBytes) } }
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    private fun slug(label: String): String = label.lowercase()
        .split(NON_SLUG)
        .filter { it.isNotEmpty() }
        .joinToString("-")
        .take(SLUG_CHARS)
        .trim('-')
        .ifEmpty { "voice" }

    companion object {
        /** Imported voices' ids start with this; catalog ids never do (the build checks). */
        const val CUSTOM_PREFIX = "custom-"
        const val CUSTOM_LICENCE = "Imported by you; its licence is the model's own"
        private const val TOKENS = "tokens.txt"
        private const val ONNX = ".onnx"
        private const val SUFFIX_CHARS = 8
        private const val SLUG_CHARS = 24
        private const val TAG = "Speech.VoiceImport"
        private val NON_SLUG = Regex("[^a-z0-9]+")
    }
}

/** What an import copies: paths relative to the voice folder ([sourcePrefix] + path inside the picked tree). */
data class ImportPlan(
    val family: SherpaFamily,
    val model: String,
    val voices: String?,
    val dataDir: String?,
    val files: List<String>,
    val bytes: Long,
    val sourcePrefix: String,
)

/** The folder rules, on paths relative to the voice folder. */
internal object ImportRules {
    private const val TOKENS = "tokens.txt"
    private const val VOICES = "voices.bin"
    private const val ESPEAK = "espeak-ng-data"
    private const val DICT_SUFFIX = "_dict"
    private val ATTRIBUTION = setOf("MODEL_CARD", "LICENSE", "README.md")
    private val LEXICON = Regex("lexicon.*\\.txt")

    /** espeak-ng dictionaries kept: the languages Audora reads (the same trim as the bundled voice, ADR-014). */
    private val SPOKEN_DICTIONARIES = Lang.entries.map { it.bcp47Tag + DICT_SUFFIX }.toSet()

    fun plan(local: List<ImportFile>, sourcePrefix: String, maxBytes: Long): Outcome<ImportPlan, ImportError> {
        val top = local.filter { '/' !in it.path }.map { it.path }.toSet()
        val model = top.filter { it.endsWith(".onnx") }
        val hasEspeak = local.any { it.path.startsWith("$ESPEAK/") }
        val family = if (VOICES in top) SherpaFamily.KITTEN else SherpaFamily.VITS
        val unsupported = top.firstOrNull { LEXICON.matches(it) }
            ?: "a dict folder".takeIf { local.any { it.path.startsWith("dict/") } }
        val problem = when {
            model.isEmpty() -> ImportError.NoModel
            model.size > 1 -> ImportError.SeveralModels(model.sorted())
            unsupported != null -> ImportError.Unsupported(unsupported)
            TOKENS !in top -> ImportError.NoTokens
            family == SherpaFamily.KITTEN && !hasEspeak -> ImportError.KittenWithoutEspeak
            else -> null
        }
        val keep = local.filter { wanted(it.path, model.firstOrNull().orEmpty()) }
        val bytes = keep.sumOf { it.sizeBytes }
        val failure = problem ?: ImportError.TooBig(bytes, maxBytes).takeIf { bytes > maxBytes }
        if (failure != null) return Outcome.Failure(failure)
        return Outcome.Success(
            ImportPlan(
                family = family,
                model = model.first(),
                voices = VOICES.takeIf { family == SherpaFamily.KITTEN },
                dataDir = ESPEAK.takeIf { hasEspeak },
                files = keep.map { it.path }.sorted(),
                bytes = bytes,
                sourcePrefix = sourcePrefix,
            ),
        )
    }

    private fun wanted(path: String, model: String): Boolean = when {
        path == model || path == TOKENS || path == VOICES || path in ATTRIBUTION -> true

        path.startsWith("$ESPEAK/") -> path.substringAfterLast('/').let { name ->
            !name.endsWith(DICT_SUFFIX) || name in SPOKEN_DICTIONARIES
        }

        else -> false
    }
}

private const val BYTES_PER_MB = 1024L * 1024

private fun Long.toMb(): Long = this / BYTES_PER_MB
