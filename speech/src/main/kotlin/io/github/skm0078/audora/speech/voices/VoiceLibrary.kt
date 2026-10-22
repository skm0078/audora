// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceLibrary.kt
// Purpose: The voice manager's Android entry point: catalog, installed voices, downloads, import, delete, choices.

package io.github.skm0078.audora.speech.voices

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import androidx.work.WorkManager
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.config.VoiceSpeedConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.SpeechEngines
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * One per process (the app's UI, the playback service and the download worker share it), so a voice the worker
 * installs is visible to the engine at once. Construction touches no disk; the installed list is read on first use.
 *
 * Network: only [downloads] uses it, through WorkManager, and only for URLs from the catalog inside the APK.
 */
class VoiceLibrary private constructor(
    context: Context,
    val config: VoiceDownloadConfig,
    val speedConfig: VoiceSpeedConfig,
) {
    private val appContext = context.applicationContext
    private val files = VoiceFiles(File(appContext.noBackupFilesDir, ROOT_DIR))
    private val installedState = MutableStateFlow<List<InstalledVoice>?>(null)
    private val lock = Any()

    /** The APK's own voice; null only for a broken build (logged by [SpeechEngines.bundled]). */
    val bundled: SherpaVoiceSpec? by lazy { SpeechEngines.readBundledSpec(appContext.assets).getOrNull() }

    private val bundledCredits: VoiceCredits? by lazy { SpeechEngines.readBundledCredits(appContext.assets) }

    /** The downloadable voices this APK knows, minus the bundled one. */
    val catalog: VoiceCatalog by lazy { readCatalog(appContext.assets, bundled?.id) }

    val readingVoices = ReadingVoices(VoicePreferences(File(files.root, PREFERENCES_FILE)))

    /** Each voice's speed on this phone, measured by the playback service (P4-02). Read from disk on first use. */
    val speeds: VoiceSpeeds by lazy { VoiceSpeeds(File(files.root, SPEEDS_FILE), speedConfig) }

    val downloads = VoiceDownloads({ WorkManager.getInstance(appContext) }, { catalog }, files, config)

    /** Downloaded and imported voices, for the engine. Read from memory after the first call. */
    fun installedSpecs(): List<SherpaVoiceSpec> = installedNow().map { it.spec }

    fun voiceDir(id: String): File = files.voiceDir(id)

    /** The voice picked for [lang] in Settings → Voices if it can still speak, else null (the default reads). */
    fun preferredVoice(lang: Lang): VoiceId? = readingVoices.preferred(lang, canSpeak)

    /** Whether a voice can read now: a phone TTS voice always may; a neural one only while bundled or installed. */
    val canSpeak: (VoiceId) -> Boolean = { voice ->
        voice.engine != EngineId.SHERPA || voice.name == bundled?.id || installedNow().any { it.spec.id == voice.name }
    }

    /** Everything the Voices screen lists, updated as downloads progress. The work runs on the IO dispatcher. */
    fun rows(): Flow<List<VoiceRow>> = combine(
        installedState,
        readingVoices.choices,
        downloads.states(),
        flow { emitAll(speeds.all) }, // speeds read their file on first use: on the IO dispatcher, not here
    ) { installed, chosen, running, measured ->
        val judged = VoiceRows.Speeds(measured, speedConfig)
        VoiceRows.withSpeeds(VoiceRows.build(bundled, catalog, installed.orEmpty(), running, chosen.orEmpty()), judged)
    }.onStart {
        installedNow()
        readingVoices.current()
    }.flowOn(Dispatchers.IO)

    /** Deletes a downloaded or imported voice; it also stops being the reading voice of its language. */
    suspend fun delete(id: String): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        downloads.cancel(id)
        val result = synchronized(lock) { files.delete(id).also { refreshLocked() } }
        SpeechEngines.blendCache(appContext).clear(id) // a Kokoro voice's narrator blend copy (plan item 75)
        readingVoices.forget(VoiceId(EngineId.SHERPA, id))
        speeds.forget(VoiceId(EngineId.SHERPA, id))
        result
    }

    /** Copies a voice folder the user picked into Audora. The picker's read grant must still be valid. */
    suspend fun import(treeUri: Uri, lang: Lang, label: String): Outcome<InstalledVoice, ImportError> =
        withContext(Dispatchers.IO) {
            val result = try {
                VoiceImporter(files, config).import(SafImportTree(appContext.contentResolver, treeUri), lang, label)
            } catch (e: SecurityException) {
                AudoraLog.e(TAG, "Import lost access to the picked folder", e)
                Outcome.Failure(ImportError.Failed("Audora lost access to the folder; pick it again", e))
            }
            synchronized(lock) { refreshLocked() }
            result
        }

    internal fun downloadJob(): VoiceDownloadJob = VoiceDownloadJob(
        catalog = catalog,
        files = files,
        downloader = ResumableDownloader(UrlConnectionHttpSource(config, USER_AGENT), config),
        installer = VoiceArchiveInstaller(files, config),
        config = config,
        onInstalled = { voice ->
            synchronized(lock) { refreshLocked() }
            // A language without a voice of its own in the APK (Hindi, Bengali) reads with the phone's voice until
            // the user picks one; the first voice they download for it reads it from then on (P3-06, ADR-030). A
            // language with a choice keeps it, and the bundled voice's language keeps its default.
            val spec = voice.spec
            if (spec.lang != bundled?.lang) readingVoices.chooseIfUnset(spec.lang, spec.voiceId)
        },
    )

    /** Every voice's credits for the "Voices & licences" screen (P3-06). Reads the disk on the IO dispatcher. */
    suspend fun licences(): List<VoiceLicence> = withContext(Dispatchers.IO) {
        VoiceLicences.build(bundled, bundledCredits, catalog, installedNow())
    }

    private fun installedNow(): List<InstalledVoice> = installedState.value ?: synchronized(lock) {
        installedState.value ?: run {
            files.recover()
            refreshLocked()
        }
    }

    /** Re-reads the installed folders. Caller holds [lock]. */
    private fun refreshLocked(): List<InstalledVoice> = files.installed().also { installedState.value = it }

    companion object {
        private const val TAG = "Speech.Voices"
        private const val ROOT_DIR = "voices/managed"
        private const val PREFERENCES_FILE = "reading-voices.properties"
        private const val SPEEDS_FILE = "voice-speeds.properties"
        private const val USER_AGENT = "Audora (voice download)"

        @Volatile
        private var instance: VoiceLibrary? = null

        fun get(
            context: Context,
            config: VoiceDownloadConfig = VoiceDownloadConfig(),
            speedConfig: VoiceSpeedConfig = VoiceSpeedConfig(),
        ): VoiceLibrary = instance ?: synchronized(this) {
            instance ?: VoiceLibrary(context, config, speedConfig).also { instance = it }
        }
    }
}

/** Reads the catalog assets under `voices/catalog` (VoiceCatalogAssetsTask); a bad entry is logged and skipped. */
private fun readCatalog(assets: AssetManager, bundledId: String?): VoiceCatalog {
    val names = try {
        assets.list(VoiceCatalog.ASSET_DIR).orEmpty().filter { it.endsWith(VoiceCatalog.FILE_SUFFIX) }.sorted()
    } catch (e: IOException) {
        AudoraLog.e(CATALOG_TAG, "Voice catalog unreadable", e)
        emptyList()
    }
    val voices = names.mapNotNull { name ->
        val parsed = try {
            assets.open("${VoiceCatalog.ASSET_DIR}/$name").reader(Charsets.UTF_8).use { reader ->
                CatalogVoiceParser.parse(Properties().apply { load(reader) })
            }
        } catch (e: IOException) {
            Outcome.Failure("unreadable: ${e.message}")
        }
        if (parsed is Outcome.Failure) AudoraLog.e(CATALOG_TAG, "Catalog entry $name skipped: ${parsed.error}")
        parsed.getOrNull()
    }.filter { it.id != bundledId }
    AudoraLog.i(CATALOG_TAG, "Voice catalog: ${voices.map { it.id }}")
    return VoiceCatalog(voices)
}

private const val CATALOG_TAG = "Speech.Catalog"
