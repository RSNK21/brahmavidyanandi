package com.korvai.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.korvai.app.ai.LlmSelector
import com.korvai.app.ai.ProposalResult
import com.korvai.app.audio.SollukattuPlayer
import com.korvai.app.data.KorvaiHistoryEntity
import com.korvai.engine.Library
import com.korvai.engine.Nadai
import com.korvai.engine.Resolution
import com.korvai.engine.SlotRequest
import com.korvai.engine.SolveRequest
import com.korvai.engine.Tala
import com.korvai.engine.TalaEngine
import com.korvai.engine.Template
import com.korvai.engine.ValidationResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/* ---------- UI state ---------- */

data class ComposerConfig(
    val talaId: String = "adi",
    val jatiId: String = "chaturasra",
    val nadaiId: String = "chaturasra",
    val kalai: Int = 1,
    val eduppuId: String = "samam",
    val avartanas: Int = 2,            // used unless template.autoAvartanas
    val templateId: String = "korvai_crescendo",
    val targetDifficulty: Int = 3,
    val seed: Int = Random.nextInt(1, 9999),
    val bpm: Int = 60,
    val metronome: Boolean = true,
)

data class UiState(
    val config: ComposerConfig = ComposerConfig(),
    val resolution: Resolution? = null,
    val validation: ValidationResult? = null,
    val variations: List<Resolution> = emptyList(),
    val history: List<KorvaiHistoryEntity> = emptyList(),
    val aiConfig: AiConfig = AiConfig(),
    val aiLog: List<Pair<String, String>> = emptyList(),
    val aiBusy: Boolean = false,
    val playing: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
)

class KorvaiViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KorvaiApp).container
    val library: Library = container.library

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val player = SollukattuPlayer()
    private val prefs = app.getSharedPreferences("korvai", Application.MODE_PRIVATE)

    /* ---------- crash armor ----------
     * Nothing that runs from the UI may kill the process. Any failure is
     * surfaced as state.error (shown in red on the config panel) with enough
     * detail to diagnose. The engine itself is deterministic and tested, but
     * a phone is a hostile place: Room disk errors, OEM quirks, OOM-killed
     * cursors — all become a message, never a crash. */

    private val asyncHandler = CoroutineExceptionHandler { _, t ->
        report("async", t)
    }

    private fun report(where: String, t: Throwable) {
        t.printStackTrace()
        _state.update {
            it.copy(error = "$where: ${t::class.java.simpleName}: ${t.message ?: "(no message)"}")
        }
    }

    private fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            report(where, t)
        }
    }

    private fun launchSafe(where: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO + asyncHandler) {
            try {
                block()
            } catch (t: Throwable) {
                report(where, t)
            }
        }
    }

    init {
        safe("startup") {
            _state.update { it.copy(aiConfig = loadAiConfig()) }
            refreshHistory()
            generate()
        }
    }

    /* ---------- helpers ---------- */

    private fun tala(cfg: ComposerConfig = _state.value.config): Tala =
        TalaEngine.applyJati(library.talas.first { it.id == cfg.talaId }, cfg.jatiId, library.jatis)

    private fun nadai(cfg: ComposerConfig = _state.value.config): Nadai =
        library.nadais.first { it.id == cfg.nadaiId }

    private fun template(cfg: ComposerConfig = _state.value.config): Template =
        library.templates.first { it.id == cfg.templateId }

    private fun eduppuAksharas(cfg: ComposerConfig = _state.value.config): Double =
        library.eduppus.first { it.id == cfg.eduppuId }.aksharas

    fun updateConfig(transform: (ComposerConfig) -> ComposerConfig) = safe("updateConfig") {
        _state.update { it.copy(config = transform(it.config)) }
    }

    fun selectTab(i: Int) = _state.update { it.copy(selectedTab = i) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /* ---------- generation (engine-only, deterministic) ---------- */

    fun generate() = safe("compose") {
        val cfg = _state.value.config
        val t = tala(cfg); val n = nadai(cfg); val tpl = template(cfg)
        val request = SolveRequest(
            tala = t, nadai = n, kalai = cfg.kalai, eduppuAksharas = eduppuAksharas(cfg),
            avartanas = if (tpl.autoAvartanas) "auto" else cfg.avartanas,
            template = tpl, library = library, seed = cfg.seed,
            maxDifficulty = 5, targetDifficulty = cfg.targetDifficulty,
        )
        val out = TalaEngine.solve(request)
        if (!out.ok) {
            _state.update { it.copy(error = out.error ?: "no solution for this combination") }
            return@safe
        }
        val res = out.resolution ?: run {
            _state.update { it.copy(error = "solver returned no resolution") }
            return@safe
        }
        val v = TalaEngine.validateResolution(res, t, n)
        if (!v.ok) {
            // should never happen: engine output failing its own validator
            _state.update { it.copy(error = "internal validation failed: " + v.errors.joinToString("; ")) }
            return@safe
        }
        _state.update { it.copy(resolution = res, validation = v, error = null, variations = emptyList()) }
        persist(res)
    }

    fun reseed() = safe("reseed") {
        _state.update { it.copy(config = it.config.copy(seed = Random.nextInt(1, 9999))) }
        generate()
    }

    fun generateVariations(n: Int) = safe("variations") {
        val cfg = _state.value.config
        val t = tala(cfg); val nadaiObj = nadai(cfg); val tpl = template(cfg)
        val results = mutableListOf<Resolution>()
        val seen = mutableSetOf<String>()
        var seed = cfg.seed
        while (results.size < n && seed < cfg.seed + n * 6) {
            seed += 1
            val out = TalaEngine.solve(
                SolveRequest(
                    tala = t, nadai = nadaiObj, kalai = cfg.kalai, eduppuAksharas = eduppuAksharas(cfg),
                    avartanas = if (tpl.autoAvartanas) "auto" else cfg.avartanas,
                    template = tpl, library = library, seed = seed,
                    maxDifficulty = 5, targetDifficulty = cfg.targetDifficulty,
                )
            )
            if (!out.ok) continue
            val res = out.resolution ?: continue
            val key = TalaEngine.resolutionSollukattu(res)
            if (seen.contains(key)) continue
            val v = TalaEngine.validateResolution(res, t, nadaiObj)
            if (!v.ok) continue
            seen.add(key)
            results.add(res)
        }
        _state.update { it.copy(variations = results, error = if (results.isEmpty()) "no variations found for this combination" else null) }
    }

    fun loadVariation(index: Int) = safe("loadVariation") {
        val res = _state.value.variations.getOrNull(index) ?: return@safe
        _state.update { it.copy(resolution = res, variations = emptyList()) }
        persist(res)
    }

    /* ---------- remix (V2) ---------- */

    fun remix(op: String) = safe("remix:$op") {
        val res = _state.value.resolution ?: return@safe
        val seed = _state.value.config.seed
        val remixed = when (op) {
            "reverse" -> TalaEngine.reverseRemix(res, seed)
            "densify" -> TalaEngine.densifyRemix(res, library, seed)
            "simplify" -> TalaEngine.simplifyRemix(res, library, seed)
            "changeEnding" -> TalaEngine.changeEndingRemix(res, library, seed)
            "changeSolkattu" -> TalaEngine.changeSolkattuRemix(res, library, seed)
            else -> return@safe
        }
        val v = TalaEngine.validateResolution(remixed, tala(), nadai())
        if (!v.ok) {
            _state.update { it.copy(error = "remix rejected: " + v.errors.joinToString("; ")) }
            return@safe
        }
        _state.update { it.copy(resolution = remixed, validation = v, error = null) }
        persist(remixed)
    }

    fun changeNadai(newNadaiId: String) = safe("changeNadai") {
        val res = _state.value.resolution ?: return@safe
        val newNadai = library.nadais.first { it.id == newNadaiId }
        val out = TalaEngine.resolveWithConfig(
            res,
            SolveRequest(
                tala = tala(), nadai = newNadai, kalai = res.config.kalai,
                eduppuAksharas = res.config.eduppuAksharas, avartanas = res.config.avartanas,
                template = library.templates.first { it.id == res.config.templateId },
                library = library, seed = _state.value.config.seed,
                maxDifficulty = res.config.maxDifficulty, targetDifficulty = res.config.targetDifficulty,
            ),
        )
        if (!out.ok) {
            _state.update { it.copy(error = "cannot re-fit in $newNadaiId nadai here") }
            return@safe
        }
        val newRes = out.resolution ?: return@safe
        _state.update {
            it.copy(
                resolution = newRes,
                validation = TalaEngine.validateResolution(newRes, tala(), newNadai),
                config = it.config.copy(nadaiId = newNadaiId),
            )
        }
        persist(newRes)
    }

    /* ---------- AI layer (V3) ---------- */

    fun updateAiConfig(transform: (AiConfig) -> AiConfig) = safe("aiConfig") {
        _state.update { it.copy(aiConfig = transform(it.aiConfig)) }
        saveAiConfig(_state.value.aiConfig)
    }

    private fun loadAiConfig(): AiConfig {
        val raw = prefs.getString("aiConfig", null) ?: return AiConfig()
        return try {
            val o = JSONObject(raw)
            AiConfig(
                api = o.optString("api", "llamacpp"),
                baseUrl = o.optString("baseUrl", "http://127.0.0.1:8080"),
                model = o.optString("model", "konakolswara-llm"),
                temperature = o.optDouble("temperature", 0.7),
                maxRetries = o.optInt("maxRetries", 2),
                enabled = o.optBoolean("enabled", false),
            )
        } catch (_: Exception) { AiConfig() }
    }

    private fun saveAiConfig(cfg: AiConfig) {
        try {
            prefs.edit().putString(
                "aiConfig",
                JSONObject()
                    .put("api", cfg.api).put("baseUrl", cfg.baseUrl).put("model", cfg.model)
                    .put("temperature", cfg.temperature).put("maxRetries", cfg.maxRetries)
                    .put("enabled", cfg.enabled)
                    .toString(),
            ).apply()
        } catch (_: Exception) { /* non-critical */ }
    }

    private fun aiLog(level: String, msg: String) =
        _state.update { it.copy(aiLog = (it.aiLog + (level to msg)).takeLast(80)) }

    fun testAiEndpoint() = safe("aiTest") {
        val aiCfg = _state.value.aiConfig
        viewModelScope.launch(Dispatchers.IO + asyncHandler) {
            try {
                aiLog("info", "testing ${aiCfg.api} at ${aiCfg.baseUrl} …")
                val selector = container.makeAiSelector(aiCfg) { l, m -> aiLog(l, m) }
                val (ok, msg) = selector.testConnection()
                aiLog(if (ok) "ok" else "warn", if (ok) "endpoint reachable — $msg" else "unreachable: $msg")
            } catch (t: Throwable) {
                aiLog("warn", "test failed: ${t.message}")
            }
        }
    }

    /** Compose with the AI selector: engine solves the structure; AI re-picks cells; validator re-counts. */
    fun generateWithAi() = safe("aiCompose") {
        val base = _state.value.resolution ?: run { generate(); _state.value.resolution } ?: run {
            _state.update { it.copy(error = "generate a korvai first") }
            return@safe
        }
        val aiCfg = _state.value.aiConfig
        if (!aiCfg.enabled) { aiLog("warn", "AI layer is off — enable it in AI settings first"); return@safe }
        _state.update { it.copy(aiBusy = true) }
        viewModelScope.launch(Dispatchers.IO + asyncHandler) {
            try {
                val tpl = library.templates.first { it.id == base.config.templateId }
                val slotDefs = tpl.slots.associateBy { it.id }
                val bySlot = LinkedHashMap<String, SlotRequest>()
                base.segments.forEach { seg ->
                    if (!bySlot.containsKey(seg.slotId)) {
                        bySlot[seg.slotId] = SlotRequest(seg.slotId, seg.label, seg.matras, slotDefs[seg.slotId] ?: com.korvai.engine.Slot(seg.slotId, seg.label))
                    }
                }
                aiLog("info", "— AI pass —")
                val selector: LlmSelector = container.makeAiSelector(aiCfg) { l, m -> aiLog(l, m) }
                val out: ProposalResult = selector.proposeCells(bySlot.values.toList(), library, base.config.nadaiId)
                if (out.source != "ai") {
                    aiLog("warn", "AI unusable (${out.aiError ?: "rejected"}) — keeping engine cells")
                    return@launch
                }
                val segs = base.segments.map { seg ->
                    val proposed = out.assignments[seg.slotId]
                    if (proposed != null && proposed.sumOf { it.matraCount } == seg.matras) seg.copy(cells = proposed)
                    else seg
                }
                val candidate = base.copy(segments = segs, source = "ai+engine")
                val t = library.talas.firstOrNull { it.id == candidate.config.talaId }?.let { TalaEngine.applyJati(it, candidate.config.jati, library.jatis) }
                val n = library.nadais.firstOrNull { it.id == candidate.config.nadaiId }
                val v = TalaEngine.validateResolution(candidate, t, n)
                if (!v.ok) {
                    aiLog("warn", "engine validator rejected AI assignment: " + v.errors.joinToString("; "))
                    return@launch
                }
                aiLog("ok", "AI cells accepted (attempt ${out.attempts}) — all counts verified")
                _state.update { it.copy(resolution = candidate, validation = v) }
                persist(candidate)
            } catch (t: Throwable) {
                aiLog("warn", "AI compose failed: ${t::class.java.simpleName}: ${t.message} — keeping engine cells")
            } finally {
                _state.update { it.copy(aiBusy = false) }
            }
        }
    }

    /* ---------- history (Room) ---------- */

    private fun persist(res: Resolution) = launchSafe("history-save") {
        container.historyDao.insert(
            KorvaiHistoryEntity(
                savedAt = System.currentTimeMillis(),
                templateId = res.config.templateId,
                talaName = res.config.talaName,
                nadaiName = res.config.nadaiName,
                kalai = res.config.kalai,
                avartanas = res.config.avartanas,
                totalMatras = res.totalMatras,
                source = res.source,
                json = container.serializeResolution(res),
            )
        )
        refreshHistory()
    }

    private fun refreshHistory() = launchSafe("history-load") {
        val rows = container.historyDao.recent()
        withContext(Dispatchers.Main) { _state.update { it.copy(history = rows) } }
    }

    fun loadHistory(entity: KorvaiHistoryEntity) = safe("history-open") {
        val res = container.deserializeResolution(entity.json) ?: run {
            _state.update { it.copy(error = "stored korvai references cells missing from the library") }
            return@safe
        }
        val t = library.talas.firstOrNull { it.id == res.config.talaId }?.let { TalaEngine.applyJati(it, res.config.jati, library.jatis) }
        val n = library.nadais.firstOrNull { it.id == res.config.nadaiId }
        _state.update {
            it.copy(
                resolution = res,
                validation = TalaEngine.validateResolution(res, t, n),
                config = it.config.copy(talaId = res.config.talaId, nadaiId = res.config.nadaiId, kalai = res.config.kalai, templateId = res.config.templateId),
            )
        }
    }

    fun clearHistory() = launchSafe("history-clear") {
        container.historyDao.clear()
        refreshHistory()
    }

    /* ---------- audio ---------- */

    fun play() = safe("play") {
        val res = _state.value.resolution ?: return@safe
        val cfg = _state.value.config
        val tl = TalaEngine.buildTimeline(res)
        val n = nadai(cfg)
        val t = tala(cfg)
        _state.update { it.copy(playing = true) }
        player.play(
            timeline = tl,
            subdivision = n.subdivision,
            kalai = res.config.kalai,
            aksharasPerAvartana = t.aksharas,
            bpm = cfg.bpm,
            metronome = cfg.metronome,
            loops = 1,
            onFinished = { _state.update { it.copy(playing = false) } },
        )
    }

    fun stopAudio() = safe("stop") {
        player.stop()
        _state.update { it.copy(playing = false) }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    companion object {
        /** Last crash written by KorvaiApp's handler, if any (shown to the user for diagnosis). */
        fun readCrashLog(app: Application): String? = try {
            val f = File(app.filesDir, "korvai-crash-log.txt")
            if (f.exists()) f.readText().take(900) else null
        } catch (_: Exception) { null }

        fun clearCrashLog(app: Application) = try {
            File(app.filesDir, "korvai-crash-log.txt").delete()
            Unit
        } catch (_: Exception) { }
    }
}
