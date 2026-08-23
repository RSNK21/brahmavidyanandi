package com.korvai.app.ai

import com.korvai.engine.CellFunction
import com.korvai.engine.Library
import com.korvai.engine.RhythmicCell
import com.korvai.engine.SlotRequest
import com.korvai.engine.TalaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/* =====================================================================
 * AI Rhythmic Selector (V3) — optional, swappable, never load-bearing.
 * Mirrors web/src/ai.js exactly: the model only proposes library cells for
 * template slots; the engine validator re-counts every matra. Failures are
 * retried, then silently replaced by the deterministic selector.
 * ===================================================================== */

data class LlmConfig(
    val api: String = "llamacpp",        // "llamacpp" | "openai"
    val baseUrl: String = "http://127.0.0.1:8080",
    val model: String = "konakolswara-llm",
    val temperature: Double = 0.7,
    val maxTokens: Int = 400,
    val timeoutMs: Long = 45000,
    val maxRetries: Int = 2,
    val onLog: (level: String, msg: String) -> Unit = { _, _ -> },
)

data class ProposalResult(
    val ok: Boolean,
    val assignments: Map<String, List<RhythmicCell>> = emptyMap(),
    val source: String = "deterministic",
    val attempts: Int = 0,
    val aiError: String? = null,
    val raw: String? = null,
)

interface RhythmicSelectorAI {
    suspend fun proposeCells(slots: List<SlotRequest>, library: Library, nadaiId: String): ProposalResult
    suspend fun testConnection(): Pair<Boolean, String>
}

/** Deterministic weighted selector — always available, no network. */
class DeterministicSelector(
    private val engine: TalaEngine,
    private val seed: Int,
) : RhythmicSelectorAI {
    override suspend fun proposeCells(slots: List<SlotRequest>, library: Library, nadaiId: String): ProposalResult =
        withContext(Dispatchers.Default) {
            val rng = TalaEngine.Rng(seed)
            val out = mutableMapOf<String, List<RhythmicCell>>()
            for (s in slots) {
                val cells = engine.fillSegment(s.matras, s.slot, library, nadaiId, 5, 3, rng)
                    ?: return@withContext ProposalResult(ok = false, aiError = "deterministic fill failed for slot ${s.id}")
                out[s.id] = cells
            }
            ProposalResult(ok = true, assignments = out, source = "deterministic")
        }

    override suspend fun testConnection(): Pair<Boolean, String> = true to "deterministic selector always available"
}

/** LLM-backed selector with validate → retry → fallback. */
class LlmSelector(
    private val engine: TalaEngine,
    private val cfg: LlmConfig,
    private val fallback: RhythmicSelectorAI,
) : RhythmicSelectorAI {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private fun log(level: String, msg: String) = cfg.onLog(level, msg)

    companion object {
        const val KONAKOL_SYSTEM =
            "You are an expert in Carnatic classical music, specializing in Konakol (Solkattu) — " +
                "the vocal recitation of rhythmic syllables — and Swara sequence composition. " +
                "You can explain Tala theory, compose creative Konakol patterns, generate melodic " +
                "Swara sequences, and teach the grammar of South Indian rhythm and melody."
    }

    fun buildPrompt(slots: List<SlotRequest>, library: Library, talaName: String, nadaiName: String, kalai: Int, targetDifficulty: Int): String {
        val lib = library.cells
            .filter { it.function != CellFunction.LANDING }
            .joinToString("\n") { c ->
                "  ${c.notation} = ${c.matraCount} matra${if (c.matraCount > 1) "s" else ""} " +
                    "(${c.function.name.lowercase()}, ${c.character.name.lowercase()}, difficulty ${c.difficulty})"
            }
        val slotText = slots.joinToString("\n") { "  Slot ${it.id} (${it.label}): exactly ${it.matras} matras." }
        val question =
            "Context: $talaName tala, $nadaiName nadai, $kalai kalai.\n\n" +
                "Available rhythm cells (matra counts are exact):\n$lib\n\n" +
                "Task: build a korvai by choosing cells for each slot. Constraints:\n$slotText\n" +
                "Aim for difficulty around $targetDifficulty of 5 and a musically pleasing mix.\n\n" +
                "RULES (obey exactly):\n" +
                "1. For each slot, output ONE LINE: \"SLOT <id>: cell1 + cell2 + ...\" using the cell notations exactly as listed.\n" +
                "2. The matra counts of the cells on each line MUST sum to exactly the slot's matras.\n" +
                "3. Use only the listed cells. No new syllables. No arithmetic in the answer.\n" +
                "4. Output the slot lines and nothing else."
        return "$KONAKOL_SYSTEM\n\n### Question:\n$question\n\n### Answer:\n"
    }

    override suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val r = rawCall("Reply with exactly: OK")
            true to r.take(120)
        } catch (e: Exception) {
            false to (e.message ?: e.toString())
        }
    }

    private suspend fun rawCall(userText: String): String = withContext(Dispatchers.IO) {
        val url = cfg.baseUrl.trimEnd('/') + if (cfg.api == "openai") "/v1/chat/completions" else "/completion"
        val body: String = if (cfg.api == "openai") {
            JSONObject()
                .put("model", cfg.model)
                .put("temperature", cfg.temperature)
                .put("max_tokens", cfg.maxTokens)
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", KONAKOL_SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", userText)))
                .toString()
        } else {
            JSONObject()
                .put("prompt", userText)
                .put("n_predict", cfg.maxTokens)
                .put("temperature", cfg.temperature)
                .put("stop", JSONArray().put("### Question:").put("</s>"))
                .toString()
        }
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val text = resp.body?.string() ?: throw IllegalStateException("empty body")
            val json = JSONObject(text)
            if (cfg.api == "openai") {
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else {
                json.optString("content", json.optString("response", "")).trim()
            }
        }
    }

    override suspend fun proposeCells(slots: List<SlotRequest>, library: Library, nadaiId: String): ProposalResult {
        val prompt = buildPrompt(slots, library, "the current tala", "the current nadai", 1, 3)
        var lastErr = ""
        for (attempt in 1..1 + cfg.maxRetries) {
            try {
                log("info", "asking ${cfg.api} endpoint (attempt $attempt)")
                val text = rawCall(prompt)
                val assignments = parseProposal(text, library)
                val errors = validateAssignments(assignments, slots, nadaiId)
                if (errors.isEmpty()) {
                    log("ok", "model proposal passed the mathematical validator on attempt $attempt")
                    return ProposalResult(true, assignments, "ai", attempt, raw = text)
                }
                lastErr = errors.joinToString("; ")
                log("warn", "proposal rejected by validator: $lastErr — regenerating")
            } catch (e: Exception) {
                lastErr = e.message ?: e.toString()
                log("warn", "endpoint error: $lastErr")
            }
        }
        log("warn", "falling back to the deterministic selector (output stays mathematically exact)")
        val fb = fallback.proposeCells(slots, library, nadaiId)
        return fb.copy(aiError = lastErr, source = "fallback")
    }

    fun validateAssignments(assignments: Map<String, List<RhythmicCell>>, slots: List<SlotRequest>, nadaiId: String): List<String> {
        val errors = mutableListOf<String>()
        for (slot in slots) {
            val cells = assignments[slot.id]
            if (cells.isNullOrEmpty()) { errors.add("slot ${slot.id}: no cells proposed"); continue }
            val sum = cells.sumOf { it.matraCount }
            if (sum != slot.matras) errors.add("slot ${slot.id}: sums to $sum, needs ${slot.matras}")
            cells.forEach { c ->
                if (!c.usableNadais.contains(nadaiId)) errors.add("slot ${slot.id}: cell ${c.notation} unusable in $nadaiId")
                if (c.function == CellFunction.LANDING || c.function == CellFunction.MACRO) errors.add("slot ${slot.id}: cell ${c.notation} has forbidden function")
            }
        }
        return errors
    }

    /** Lenient parser — accepts "SLOT A: ta ki ta + ta ka di mi" and ×N repeats. */
    fun parseProposal(text: String, library: Library): Map<String, List<RhythmicCell>> {
        val byNotation = HashMap<String, RhythmicCell>()
        library.cells.forEach { byNotation[it.notation.lowercase().replace(Regex("[\\s,]+"), "")] = it }
        val assignments = mutableMapOf<String, List<RhythmicCell>>()
        val slotRegex = Regex("^\\s*(?:slot\\s*)?([A-Za-z0-9_]+)\\s*[:\\-]\\s*(.+)\$", RegexOption.IGNORE_CASE)
        for (line in text.split("\r\n", "\n")) {
            val m = slotRegex.find(line) ?: continue
            val slotId = m.groupValues[1].uppercase()
            val cells = mutableListOf<RhythmicCell>()
            for (tok in m.groupValues[2].split('+', ',', '·', '|', ';')) {
                val clean = tok.replace(Regex("\\(.*?\\)"), "").replace("\"", "").replace("'", "").trim()
                if (clean.isEmpty()) continue
                var cell = byNotation[clean.lowercase().replace(Regex("[\\s,×]+"), "")]
                    ?: byNotation[clean.lowercase().replace(" ", "")]
                if (cell == null) {
                    val rep = Regex("^(.*?)\\s*[×x]\\s*(\\d+)\$", RegexOption.IGNORE_CASE).find(clean)
                    if (rep != null) {
                        val base = byNotation[rep.groupValues[1].lowercase().replace(Regex("[\\s,]+"), "")]
                        if (base != null) {
                            repeat(rep.groupValues[2].toInt()) { cells.add(base) }
                            continue
                        }
                    }
                }
                cell?.let { cells.add(it) }
            }
            if (cells.isNotEmpty()) assignments[slotId] = cells
        }
        return assignments
    }
}
