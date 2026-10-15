// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/VoicesController.kt
// Purpose: Owns the spike's work (downloads, benchmark, listening state) outside the activity, so rotating or
//          briefly leaving the screen does not cancel a run. Publishes one VoicesUiState.

package io.github.skm0078.audora.spike.voices

import android.app.Application
import android.os.SystemClock
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.voices.bench.BenchConfig
import io.github.skm0078.audora.spike.voices.bench.BenchPlan
import io.github.skm0078.audora.spike.voices.bench.BenchReport
import io.github.skm0078.audora.spike.voices.bench.BenchRunner
import io.github.skm0078.audora.spike.voices.bench.ListeningRow
import io.github.skm0078.audora.spike.voices.bench.PlannedRun
import io.github.skm0078.audora.spike.voices.bench.ReportInput
import io.github.skm0078.audora.spike.voices.bench.VoiceRun
import io.github.skm0078.audora.spike.voices.catalog.Catalog
import io.github.skm0078.audora.spike.voices.catalog.CatalogParser
import io.github.skm0078.audora.spike.voices.device.DeviceInfo
import io.github.skm0078.audora.spike.voices.download.InstallOutcome
import io.github.skm0078.audora.spike.voices.download.ModelStore
import io.github.skm0078.audora.spike.voices.download.UrlHttpFetcher
import io.github.skm0078.audora.spike.voices.engine.SherpaEngine
import io.github.skm0078.audora.spike.voices.engine.SystemTtsEngine
import io.github.skm0078.audora.spike.voices.listen.BlindEntry
import io.github.skm0078.audora.spike.voices.listen.BlindOrder
import io.github.skm0078.audora.spike.voices.listen.ListeningStore
import io.github.skm0078.audora.spike.voices.ui.LanguageStatus
import io.github.skm0078.audora.spike.voices.ui.ListenItem
import io.github.skm0078.audora.spike.voices.ui.VoicesUiState
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException

// One façade for the spike's single screen; splitting it would only scatter shared job state.
@Suppress("TooManyFunctions")
class VoicesController(private val app: Application, private val config: BenchConfig = BenchConfig()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val device = DeviceInfo(app)
    private val listening = ListeningStore(app)
    private val reportFile = File(app.filesDir, REPORT_FILE)
    private val catalog: Catalog? = loadCatalog()
    private val store: ModelStore? = catalog?.let {
        ModelStore(File(app.filesDir, MODELS_DIR), it.modelBaseUrl, UrlHttpFetcher())
    }
    private val runner = BenchRunner(clock = { SystemClock.elapsedRealtime() }, probe = device, config = config)
    private var downloadJob: Job? = null
    private var benchJob: Job? = null
    private var lastRuns: List<VoiceRun> = emptyList()
    private var benchStartedAt: String = ""

    private val _state = MutableStateFlow(
        VoicesUiState(
            fatalError = if (catalog == null) "The voice catalog could not be read. Share the log file." else null,
            report = if (reportFile.isFile) reportFile.readText() else "",
        ),
    )
    val state: StateFlow<VoicesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val catalog = catalog ?: return
        val store = store ?: return
        _state.update {
            it.copy(
                languages = catalog.languages.map { lang ->
                    val voices = catalog.voices.filter { v -> v.lang == lang }
                    val installed = voices.count { v -> store.isInstalled(v.archive) }
                    LanguageStatus(lang, LANGUAGE_NAMES[lang] ?: lang, voices.size, installed)
                },
                storageMb = store.installedBytes() / BYTES_PER_MB,
                listening = listeningItems(catalog),
                revealNames = listening.revealNames,
            )
        }
    }

    fun downloadAll() {
        val (catalog, store) = idleWorkspace() ?: return
        downloadJob = scope.launch {
            _state.update { it.copy(downloadBusy = true, downloadLine = "Checking…") }
            val outcomes = store.install(catalog.archivesFor(catalog.languages), isCancelled = { !isActive }) { p ->
                val mb = p.bytes / BYTES_PER_MB
                val total = if (p.total > 0) " of ${p.total / BYTES_PER_MB} MB" else " MB"
                _state.update { it.copy(downloadLine = "Voice file ${p.index}/${p.count}: ${p.archive}, $mb$total") }
            }
            val failed = outcomes.filterIsInstance<InstallOutcome.Failed>()
            val line = if (failed.isEmpty()) {
                "All voices downloaded."
            } else {
                "${failed.size} download(s) failed: " + failed.joinToString("; ") { "${it.archive}: ${it.reason}" }
            }
            AudoraLog.i(TAG, "Download finished: $line")
            _state.update { it.copy(downloadBusy = false, downloadLine = line) }
            refresh()
        }
    }

    fun deleteModels() {
        if (isBusy()) return
        store?.deleteAll()
        _state.update { it.copy(downloadLine = "Downloaded voices deleted.") }
        refresh()
    }

    fun startBenchmark() {
        val (catalog, store) = idleWorkspace() ?: return
        benchJob = scope.launch {
            val plan = BenchPlan.build(catalog, store::isInstalled, config)
            benchStartedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            AudoraLog.i(TAG, "Benchmark started: ${plan.size} runs")
            val runs = mutableListOf<VoiceRun>()
            _state.update { it.copy(benchBusy = true) }
            for ((index, planned) in plan.withIndex()) {
                if (!isActive) break
                val engine = when (planned) {
                    is PlannedRun.Neural ->
                        SherpaEngine(planned.spec, store.modelDir(planned.spec.archive), planned.threads)

                    is PlannedRun.System -> SystemTtsEngine(app, planned.lang, app.cacheDir)
                }
                val threads = engine.threads?.let { "$it threads" } ?: "system"
                _state.update { it.copy(benchLine = "Run ${index + 1} of ${plan.size}: ${engine.label} ($threads)") }
                runs += runner.run(
                    engine = engine,
                    texts = catalog.textsFor(planned.lang),
                    sampleSink = listening.takeIf { planned.saveSample },
                    isCancelled = { !isActive },
                )
                lastRuns = runs.toList()
                publishReport()
            }
            val done = if (runs.size == plan.size) "Finished ${runs.size} runs." else "Stopped after ${runs.size} runs."
            AudoraLog.i(TAG, "Benchmark ended: $done")
            _state.update { it.copy(benchBusy = false, benchLine = done) }
            refresh()
        }
    }

    fun cancelBenchmark() {
        benchJob?.cancel()
    }

    fun setRating(voiceId: String, rating: Int) {
        listening.setRating(voiceId, rating)
        refresh()
        publishReport()
    }

    fun setRevealNames(reveal: Boolean) {
        listening.revealNames = reveal
        refresh()
    }

    fun sampleFile(voiceId: String): File = listening.sampleFile(voiceId)

    fun setPlaying(voiceId: String?) = _state.update { it.copy(playingVoiceId = voiceId) }

    private fun isBusy() = downloadJob?.isActive == true || benchJob?.isActive == true

    /** The catalog and model store, or null when either failed to load or a job is already running. */
    private fun idleWorkspace(): Pair<Catalog, ModelStore>? {
        if (isBusy()) return null
        return catalog?.let { c -> store?.let { c to it } }
    }

    private fun publishReport() {
        val catalog = catalog ?: return
        val rows = listeningItems(catalog).flatMap { (lang, items) ->
            items.map { ListeningRow(lang, BlindEntry(it.letter, it.voiceId), it.label, it.rating) }
        }
        val text = BenchReport.format(
            ReportInput(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                startedAt = benchStartedAt.ifEmpty { "not run in this session" },
                device = device.describe(),
                runs = lastRuns,
                listening = rows,
            ),
            config,
        )
        try {
            reportFile.writeText(text)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save the report to ${reportFile.path}", e)
        }
        _state.update { it.copy(report = text) }
    }

    private fun listeningItems(catalog: Catalog): Map<String, List<ListenItem>> =
        catalog.languages.associateWith { lang ->
            val ids = catalog.voices.filter { it.lang == lang }.map { it.id } + systemVoiceId(lang)
            BlindOrder.assign(ids, listening.seed).map { entry ->
                ListenItem(
                    voiceId = entry.voiceId,
                    letter = entry.letter,
                    label = labelOf(catalog, entry.voiceId),
                    hasSample = listening.hasSample(entry.voiceId),
                    rating = listening.rating(entry.voiceId),
                )
            }
        }

    private fun labelOf(catalog: Catalog, voiceId: String): String =
        catalog.voices.firstOrNull { it.id == voiceId }?.label ?: "Phone's system voice"

    private fun systemVoiceId(lang: String) = "system-$lang"

    private fun loadCatalog(): Catalog? = try {
        CatalogParser.parse(app.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() })
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Could not read $CATALOG_ASSET", e)
        null
    } catch (e: JSONException) {
        AudoraLog.e(TAG, "Malformed $CATALOG_ASSET", e)
        null
    } catch (e: IllegalArgumentException) {
        AudoraLog.e(TAG, "Invalid $CATALOG_ASSET", e)
        null
    }

    private companion object {
        const val TAG = "S1.Controller"
        const val CATALOG_ASSET = "s1-voices.json"
        const val MODELS_DIR = "models"
        const val REPORT_FILE = "s1-report.md"
        const val BYTES_PER_MB = 1_000_000L
        val LANGUAGE_NAMES = mapOf("en" to "English", "hi" to "Hindi", "bn" to "Bengali")
    }
}
