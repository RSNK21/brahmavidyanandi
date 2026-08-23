package com.korvai.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/* =====================================================================
 * Tala Constraint Engine — deterministic, pure, no AI, no network.
 * Direct port of web/src/engine.js (kept behaviourally identical so the
 * web reference implementation and the Android app produce the same math).
 *
 * HARD RULE (handoff §8): no AI model ever performs tala arithmetic.
 * Everything that reaches the UI has passed [validateResolution].
 * ===================================================================== */
object TalaEngine {

    /* ---------- seeded RNG (mulberry32) ---------- */
    class Rng(seed: Int) {
        private var a = (seed.toLong() and 0xffffffffL).toInt()
        fun next(): Double {
            a += 0x6D2B79F5.toInt()
            var t = a
            t = (t xor (t ushr 15)) * (1 or t)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
        }
        fun int(bound: Int) = (next() * bound).toInt()
        fun <T> shuffled(list: List<T>): List<T> {
            val a = list.toMutableList()
            for (i in a.size - 1 downTo 1) {
                val j = int(i + 1)
                val tmp = a[i]; a[i] = a[j]; a[j] = tmp
            }
            return a
        }
    }

    /* ---------- jati / anga helpers ---------- */
    fun applyJati(tala: Tala, jatiId: String, jatis: List<Jati>): Tala {
        val j = jatis.firstOrNull { it.id == jatiId } ?: jatis.firstOrNull { it.id == tala.jati } ?: return tala
        val angas = tala.angas.map { if (it.type == AngaType.LAGHU) Anga(AngaType.LAGHU, j.laghu) else it }
        return tala.copy(jati = j.id, angas = angas, aksharas = angas.sumOf { it.aksharas })
    }

    fun totalMatras(tala: Tala, nadai: Nadai, kalai: Int, avartanas: Int): Int =
        tala.aksharas * nadai.subdivision * kalai * avartanas

    fun computeLandingUnits(eduppuAksharas: Double, nadai: Nadai, kalai: Int): Int =
        if (eduppuAksharas <= 0.0) 2
        else max(1, (eduppuAksharas * nadai.subdivision * kalai).roundToInt())

    fun clapPattern(tala: Tala): List<ClapMark> {
        val out = mutableListOf<ClapMark>()
        tala.angas.forEachIndexed { ai, anga ->
            repeat(anga.aksharas) { k ->
                val mark = when (anga.type) {
                    AngaType.LAGHU -> if (k == 0) ClapMark.CLAP else ClapMark.COUNT
                    AngaType.DHRUTAM -> if (k == 0) ClapMark.CLAP else ClapMark.WAVE
                    AngaType.ANUDHRUTAM -> ClapMark.WAVE
                    AngaType.SECTION -> if (k == 0) ClapMark.CLAP else ClapMark.REST
                }
                out.add(ClapMark(mark, ai))
            }
        }
        return out
    }

    /* ---------- segment fill: subset-sum over the cell library ---------- */
    private fun eligibleCells(library: Library, slot: Slot, nadaiId: String, maxDifficulty: Int): List<RhythmicCell> =
        library.cells.filter { c ->
            c.matraCount >= 1 &&
                c.usableNadais.contains(nadaiId) &&
                c.difficulty <= maxDifficulty &&
                (slot.allowedFunctions.isEmpty() || slot.allowedFunctions.contains(c.function)) &&
                !(c.function == CellFunction.GAP && !slot.allowGaps) &&
                c.function != CellFunction.LANDING &&
                c.function != CellFunction.MACRO
        }

    private class FillCtx(
        val targetDifficulty: Int,
        val gapAffinity: Double,
        val lastCellId: String?,
        val charactersSeen: Set<CellCharacter>,
    )

    private fun scoreCell(cell: RhythmicCell, ctx: FillCtx): Double {
        var s = 1.0
        s *= 1.0 / (1 + abs(cell.difficulty - ctx.targetDifficulty))
        if (cell.kaarvai) s *= ctx.gapAffinity
        if (ctx.lastCellId == cell.id) s *= 0.25
        if (ctx.charactersSeen.contains(cell.character)) s *= 0.8
        return s
    }

    fun fillSegment(
        length: Int,
        slot: Slot,
        library: Library,
        nadaiId: String,
        maxDifficulty: Int,
        targetDifficulty: Int,
        rng: Rng,
        maxPieces: Int = 12,
        firstCellMustDifferFrom: String? = null,
    ): List<RhythmicCell>? {
        val pool = eligibleCells(library, slot, nadaiId, maxDifficulty)
        if (pool.isEmpty() || length <= 0) return null
        val result = mutableListOf<RhythmicCell>()

        fun bt(remaining: Int, depth: Int): Boolean {
            if (remaining == 0) return true
            if (depth >= maxPieces) return false
            val ctx = FillCtx(
                targetDifficulty,
                0.55,
                if (result.isEmpty()) null else result.last().id,
                result.map { it.character }.toSet(),
            )
            var candidates = pool.filter { it.matraCount <= remaining }
            if (depth == 0 && firstCellMustDifferFrom != null) {
                candidates = candidates.filter { it.id != firstCellMustDifferFrom }.ifEmpty { pool.filter { it.matraCount <= remaining } }
            }
            val ordered = candidates
                .map { it to scoreCell(it, ctx) * (0.5 + rng.next()) }
                .sortedByDescending { it.second }
                .map { it.first }
            for (c in ordered) {
                result.add(c)
                if (bt(remaining - c.matraCount, depth + 1)) return true
                result.removeAt(result.size - 1)
            }
            return false
        }
        return if (bt(length, 0)) result.toList() else null
    }

    /* ---------- kuraippu lengths ---------- */
    fun kuraippuLengths(body: Int): List<Int> {
        val segs = mutableListOf<Int>()
        var rem = body
        var L = 2.0.pow(floor(log2(max(2, body).toDouble()))).toInt()
        if (L > body) L /= 2
        while (rem > 0) {
            val take = min(L, rem)
            segs.add(take)
            rem -= take
            var next = max(2, take / 2)
            if (next % 2 == 1 && next > 2) next -= 1
            L = next
        }
        return segs
    }

    /* ---------- landing ---------- */
    fun pickLanding(library: Library, units: Int, nadaiId: String, rng: Rng): RhythmicCell? {
        val cands = library.cells.filter {
            it.function == CellFunction.LANDING && it.matraCount == units && it.usableNadais.contains(nadaiId)
        }
        return if (cands.isEmpty()) null else cands[rng.int(cands.size)]
    }

    /* ---------- template expansion ---------- */
    data class Step(val slotId: String, val label: String, val fixed: String?)

    fun templateOrder(template: Template): List<Step> {
        val steps = mutableListOf<Pair<String, String?>>() // slotId, fixedCellId?
        when (template.id) {
            "mohra_korvai" -> {
                repeat(3) { listOf("A", "A", "A", "B").forEach { steps.add(it to null) } }
                repeat(3) { steps.add("C" to "c_thathaiatham") }
                repeat(3) { steps.add("X" to null) }
            }
            "tirmana" -> {
                repeat(3) { steps.add("T3" to null) }
                repeat(3) { steps.add("T4" to null) }
                repeat(3) { steps.add("T5" to null) }
            }
            "gap_korvai" -> listOf("X", "G", "X", "G", "X").forEach { steps.add(it to null) }
            else -> {
                val reps = template.repetitions.coerceAtLeast(1)
                val ids = template.slots.map { it.id }
                repeat(reps) { ids.forEach { steps.add(it to null) } }
            }
        }
        val byId = template.slots.associateBy { it.id }
        return steps.map { (slotId, forced) ->
            Step(slotId, byId[slotId]?.label ?: slotId, forced ?: byId[slotId]?.fixedCell)
        }
    }

    /* ---------- structure enumeration ---------- */
    fun enumerateStructures(
        template: Template,
        variableSlots: List<Slot>,
        bodyRemaining: Int,
        occurrences: Map<String, Int>,
        avartanaMatras: Int,
    ): List<Map<String, Int>> {
        val results = mutableListOf<Map<String, Int>>()
        val n = variableSlots.size
        if (n == 0) return if (bodyRemaining >= 0) listOf(emptyMap()) else emptyList()
        val stair = template.staircase
        val multipleOfAv = template.multipleOf == "avartana"

        fun rec(i: Int, remaining: Int, acc: MutableMap<String, Int>) {
            if (results.size >= 60) return
            if (i == n) { results.add(acc.toMap()); return }
            val slot = variableSlots[i]
            val occ = occurrences[slot.id] ?: 1
            var lo = max(1, slot.minMatra ?: 1)
            var hi = min(slot.maxMatra ?: 64, remaining / occ)
            if (hi < lo) return
            if (stair > 0 && i > 0) lo = max(lo, (acc[variableSlots[i - 1].id] ?: 0) + stair)
            if (multipleOfAv) {
                lo = max(lo, avartanaMatras)
                hi -= hi % avartanaMatras
                if (hi < lo) return
            }
            val aim = bodyRemaining.toDouble() / max(1, n - i)
            val candidates = mutableListOf<Int>()
            var v = lo
            while (v <= hi) {
                if (!multipleOfAv || v % avartanaMatras == 0) candidates.add(v)
                v++
            }
            candidates.sortedBy { abs(it - aim) }.forEach { value ->
                acc[slot.id] = value
                rec(i + 1, remaining - occ * value, acc)
                acc.remove(slot.id)
            }
        }
        rec(0, bodyRemaining, LinkedHashMap())
        return results
    }

    /* ---------- main solver ---------- */
    fun solve(config: SolveRequest): SolveResult {
        val tala = config.tala
        val nadai = config.nadai
        val template = config.template
        val library = config.library
        val rng = Rng(config.seed)
        val maxDifficulty = config.maxDifficulty
        val targetDifficulty = config.targetDifficulty
        val avartanaMatras = tala.aksharas * nadai.subdivision * config.kalai

        /* ---- kuraippu ---- */
        if (template.kind == "kuraippu") {
            for (cycles in 1..8) {
                val target = avartanaMatras * cycles
                val landing = computeLandingUnits(config.eduppuAksharas, nadai, config.kalai)
                val body = target - landing
                if (body < 4) continue
                val slot = template.slots[0]
                val segs = mutableListOf<Segment>()
                var failed = false
                for (len in kuraippuLengths(body)) {
                    val cells = fillSegment(len, slot, library, nadai.id, maxDifficulty, targetDifficulty, rng)
                    if (cells == null) { failed = true; break }
                    segs.add(Segment(slot.id, slot.label, cells, len))
                }
                if (failed) continue
                val landingCell = pickLanding(library, landing, nadai.id, rng) ?: continue
                val res = buildResolution(config, template, cycles, segs, 1, landingCell, 0, tala, nadai, rng, "eduppu")
                val v = validateResolution(res, tala, nadai)
                if (v.ok) return SolveResult.of(res)
            }
            return SolveResult.fail("No valid kuraippu fits this tala/nadai/kalai at the chosen difficulty.")
        }

        /* ---- faran (cross-rhythm macro cells) ---- */
        if (template.kind == "faran") {
            val macros = library.cells.filter {
                it.function == CellFunction.MACRO && it.usableNadais.contains(nadai.id) && it.difficulty <= maxDifficulty
            }
            for (cycles in 1..8) {
                val target = avartanaMatras * cycles
                val body = target // farans resolve on sam; no extra landing
                for (m in rng.shuffled(macros)) {
                    if (m.matraCount > body || body % m.matraCount != 0) continue
                    val reps = body / m.matraCount
                    val segs = (0 until reps).map { Segment("F", "Faran cell", listOf(m), m.matraCount) }
                    val res = buildResolution(config, template, cycles, segs, 1, null, 0, tala, nadai, rng, "none")
                    val v = validateResolution(res, tala, nadai)
                    if (v.ok) return SolveResult.of(res)
                }
            }
            return SolveResult.fail("No faran cell divides this cycle length evenly. Try another cycles count or nadai.")
        }

        /* ---- standard / auto-avartanas templates ---- */
        val auto = template.autoAvartanas || config.avartanas == "auto"
        val landingMode = template.landingMode.ifEmpty { "eduppu" }
        val order = templateOrder(template)
        val occurrences = mutableMapOf<String, Int>()
        var fixedTotal = 0
        for (st in order) {
            val c = st.fixed?.let { library.cells.firstOrNull { x -> x.id == it } }
            if (st.fixed != null) fixedTotal += c?.matraCount ?: 0
            else occurrences[st.slotId] = (occurrences[st.slotId] ?: 0) + 1
        }
        val variableSlots = template.slots.filter { it.fixedCell == null }

        // Landing-length candidates: strict when a real eduppu is set; at samam
        // any of the available LANDING lengths (1/2/3) may be used so the three
        // phrases can divide the cycle exactly — as musicians choose endings.
        val landingCandidates: List<Int> = if (landingMode == "eduppu") {
            val preferred = computeLandingUnits(config.eduppuAksharas, nadai, config.kalai)
            if (config.eduppuAksharas > 0.0) listOf(preferred)
            else {
                val sizes = library.cells
                    .filter { it.function == CellFunction.LANDING && it.usableNadais.contains(nadai.id) }
                    .map { it.matraCount }.distinct()
                    .sortedBy { abs(it - preferred) }
                if (sizes.isEmpty()) listOf(preferred) else sizes
            }
        } else listOf(0)

        val attempts: List<Int> = if (auto) (1..8).toList() else listOf((config.avartanas as? Int) ?: 2)
        for (cycles in attempts) {
            val target = avartanaMatras * cycles
            for (L in landingCandidates) {
                val bodyRemaining = target - L - fixedTotal
                if (bodyRemaining < 0) continue
                val structures = enumerateStructures(template, variableSlots, bodyRemaining, occurrences, avartanaMatras)
                val withPad = structures
                    .map { lens ->
                        val sum = variableSlots.sumOf { (occurrences[it.id] ?: 1) * (lens[it.id] ?: 0) }
                        lens to (bodyRemaining - sum)
                    }
                    .filter { (_, pad) -> if (auto) pad in 0 until avartanaMatras else pad == 0 }
                    .sortedBy { it.second }
                for ((lens, pad) in withPad) {
                    val filled = mutableMapOf<String, Segment>()
                    var failed = false
                    for (slot in variableSlots) {
                        if (filled.containsKey(slot.id)) continue
                        val len = lens[slot.id] ?: continue
                        val cells = fillSegment(len, slot, library, nadai.id, maxDifficulty, targetDifficulty, rng)
                        if (cells == null) { failed = true; break }
                        filled[slot.id] = Segment(slot.id, slot.label, cells, len)
                    }
                    if (failed) continue

                    val segs = order.map { st ->
                        if (st.fixed != null) {
                            val c = library.cells.first { it.id == st.fixed }
                            Segment(st.slotId, st.label, listOf(c), c.matraCount, fixed = true)
                        } else filled[st.slotId]!!.copy(label = st.label)
                    }

                    val landingCell = if (L > 0) pickLanding(library, L, nadai.id, rng) else null
                    if (L > 0 && landingCell == null) continue

                    val res = buildResolution(config, template, cycles, segs, 1, landingCell, pad, tala, nadai, rng, landingMode)
                    val v = validateResolution(res, tala, nadai)
                    if (v.ok) return SolveResult.of(res)
                }
            }
        }
        return SolveResult.fail(
            "No exact fit for this tala/nadai/kalai/template combination. " +
                "Try a different number of cycles (or Auto), another template, or a lower difficulty."
        )
    }

    private var idCounter = 0L
    private fun nextId(): String = "k${System.currentTimeMillis().toString(36)}${(idCounter++).toString(36)}"

    fun buildResolution(
        config: SolveRequest,
        template: Template,
        avartanas: Int,
        segments: List<Segment>,
        repetitions: Int,
        landingCell: RhythmicCell?,
        pad: Int,
        tala: Tala,
        nadai: Nadai,
        rng: Rng,
        landingMode: String,
    ): Resolution {
        val landing = landingCell?.matraCount ?: 0
        return Resolution(
            id = nextId(),
            config = ResolutionConfig(
                talaId = tala.id, talaName = tala.name, jati = tala.jati,
                nadaiId = nadai.id, nadaiName = nadai.name,
                kalai = config.kalai, eduppuAksharas = config.eduppuAksharas,
                avartanas = avartanas, templateId = template.id, templateName = template.name,
                seed = config.seed, maxDifficulty = config.maxDifficulty,
                targetDifficulty = config.targetDifficulty,
                landingMode = landingMode,
            ),
            template = template,
            segments = segments,
            repetitions = repetitions,
            landingCell = landingCell,
            landing = landing,
            pad = pad,
            totalMatras = pad + segments.sumOf { it.matras } + landing,
            source = "engine",
            generatedAt = java.time.LocalDateTime.now().toString(),
        )
    }

    /* ---------- THE VALIDATOR (single source of truth) ---------- */
    fun validateResolution(res: Resolution, tala: Tala?, nadai: Nadai?): ValidationResult {
        val errors = mutableListOf<String>()

        if (res.segments.isEmpty()) errors.add("segments missing")
        var segTotal = 0
        res.segments.forEachIndexed { i, seg ->
            var segMatras = 0
            seg.cells.forEach { c ->
                val dur = c.durations.sum()
                if (dur != c.matraCount) errors.add("segment ${i + 1}: cell ${c.id} durations ($dur) != matraCount (${c.matraCount})")
                if (c.syllables.size != c.durations.size) errors.add("segment ${i + 1}: cell ${c.id} syllables/durations mismatch")
                if (nadai != null && !c.usableNadais.contains(nadai.id)) errors.add("segment ${i + 1}: cell ${c.id} not usable in nadai ${nadai.id}")
                segMatras += c.matraCount
            }
            if (segMatras != seg.matras) errors.add("segment ${i + 1}: cells sum to $segMatras but declared ${seg.matras}")
            segTotal += segMatras
        }

        val landing = res.landingCell?.matraCount ?: 0
        if (res.landing != landing) errors.add("landing mismatch: declared ${res.landing}, cell gives $landing")
        if (res.landingCell != null) {
            if (nadai != null && !res.landingCell.usableNadais.contains(nadai.id)) errors.add("landing cell not usable in this nadai")
            if (res.landingCell.function != CellFunction.LANDING) errors.add("landing cell must have function LANDING")
        }

        val recomputed = res.pad + segTotal + landing
        if (recomputed != res.totalMatras) errors.add("totalMatras declared ${res.totalMatras}, recomputed $recomputed")

        if (tala != null && nadai != null) {
            val avartanaMatras = tala.aksharas * nadai.subdivision * res.config.kalai
            if (avartanaMatras * res.config.avartanas != recomputed)
                errors.add("does not fill ${res.config.avartanas} avartanas exactly: needs ${avartanaMatras * res.config.avartanas}, got $recomputed")
            if (res.pad < 0 || res.pad >= avartanaMatras) errors.add("front pad out of range")
            if (res.config.landingMode == "none" && landing != 0) errors.add("landingMode none must have no landing cell")
            if (res.config.eduppuAksharas > 0.0 && res.config.landingMode != "none") {
                val expect = max(1, (res.config.eduppuAksharas * nadai.subdivision * res.config.kalai).roundToInt())
                if (landing != expect) errors.add("eduppu ${res.config.eduppuAksharas} requires landing of $expect, got $landing")
            }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    /* ---------- rendering helpers ---------- */
    fun segmentSollukattu(seg: Segment): String = seg.cells.joinToString(" ") { it.syllables.joinToString(" ") }

    fun resolutionSollukattu(res: Resolution): String {
        val parts = mutableListOf<String>()
        if (res.pad > 0) parts.add(List(res.pad) { "—" }.joinToString(" "))
        res.segments.forEach { parts.add(segmentSollukattu(it)) }
        res.landingCell?.let { parts.add(it.syllables.joinToString(" ")) }
        return parts.filter { it.isNotBlank() }.joinToString("  |  ")
    }

    fun buildTimeline(res: Resolution): Timeline {
        val events = mutableListOf<TimelineEvent>()
        var m = 0
        if (res.pad > 0) {
            repeat(res.pad) {
                events.add(TimelineEvent(m, 1, "—", Weight.L, "pad", -1, "pad"))
                m += 1
            }
        }
        res.segments.forEachIndexed { si, seg ->
            seg.cells.forEach { c ->
                c.syllables.forEachIndexed { i, syl ->
                    val dur = c.durations[i]
                    events.add(TimelineEvent(m, dur, syl, c.weights[i], "segment", si, c.id))
                    m += dur
                }
            }
        }
        res.landingCell?.let { c ->
            c.syllables.forEachIndexed { i, syl ->
                val dur = c.durations[i]
                events.add(TimelineEvent(m, dur, syl, c.weights[i], "landing", -2, c.id))
                m += dur
            }
        }
        return Timeline(events, m)
    }

    /* ---------- dance counts ---------- */
    fun danceCounts(res: Resolution, tala: Tala, nadai: Nadai): DanceCounts {
        val tl = buildTimeline(res)
        val matrasPerAkshara = nadai.subdivision * res.config.kalai
        val aksharasPerCount = max(1, res.config.kalai)
        val matrasPerCount = matrasPerAkshara * aksharasPerCount
        val totalAksharas = ceil(tl.totalMatras.toDouble() / matrasPerAkshara).toInt()
        val blocks = mutableListOf<DanceCountBlock>()
        for (c in 0 until ceil(totalAksharas.toDouble() / aksharasPerCount).toInt()) {
            val from = c * matrasPerCount
            val to = from + matrasPerCount
            val evs = tl.events.filter { it.matra >= from && it.matra < to }
            blocks.add(
                DanceCountBlock(
                    count = (c % 8) + 1,
                    isSam = c % 8 == 0,
                    aksharaFrom = from / matrasPerAkshara,
                    aksharaTo = to / matrasPerAkshara,
                    sollukattu = evs.joinToString(" ") { it.syllable },
                )
            )
        }
        return DanceCounts(tala.aksharas / aksharasPerCount, blocks)
    }

    /* ---------- adavu suggestions ---------- */
    fun suggestAdavus(res: Resolution, library: Library, nadaiId: String): List<Adavu> {
        val charWeights = mutableMapOf<CellCharacter, Int>()
        res.segments.forEach { s -> s.cells.forEach { c -> charWeights[c.character] = (charWeights[c.character] ?: 0) + c.matraCount } }
        val ranked = charWeights.entries.sortedByDescending { it.value }.map { it.key }
        return library.adavus
            .filter { it.nadais.contains(nadaiId) && it.characters.any { ch -> ranked.contains(ch) } }
            .map { a ->
                val charScore = a.characters.mapNotNull { ch -> ranked.indexOf(ch).takeIf { it >= 0 } }
                    .map { ranked.size - it }.maxOrNull() ?: 0
                val diffPenalty = abs(a.difficulty - res.config.targetDifficulty)
                a to (charScore - diffPenalty * 0.5)
            }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }
    }

    /* ---------- remix ops (V2, deterministic) ---------- */
    private fun derivedCell(cell: RhythmicCell, syllables: List<String>, durations: List<Int>, rng: Rng): RhythmicCell {
        val weights = syllables.map { if (HEAVY_REGEX.containsMatchIn(it)) Weight.H else Weight.L }
        return cell.copy(
            id = cell.id + "#d" + rng.int(100000),
            syllables = syllables, durations = durations,
            matraCount = durations.sum(), weights = weights,
            derived = true, baseCellId = cell.id,
        )
    }

    private fun derivedLanding(cell: RhythmicCell, syllables: List<String>, durations: List<Int>, rng: Rng): RhythmicCell =
        derivedCell(cell, syllables, durations, rng).copy(function = CellFunction.LANDING)

    fun reverseRemix(res: Resolution, seed: Int): Resolution {
        val rng = Rng(seed + 101)
        return res.copy(
            segments = res.segments.map { seg ->
                seg.cells.reversed().map { c ->
                    derivedCell(c, c.syllables.reversed(), c.durations.reversed(), rng)
                }.let { seg.copy(cells = it) }
            },
            id = res.id + "-rev", source = "remix:reverse",
        )
    }

    fun densifyRemix(res: Resolution, library: Library, seed: Int): Resolution {
        val rng = Rng(seed + 202)
        val maxD = min(5, res.config.targetDifficulty + 2)
        return res.copy(
            segments = res.segments.map { seg ->
                val out = mutableListOf<RhythmicCell>()
                for (c in seg.cells) {
                    if (c.kaarvai || c.function == CellFunction.GAP) {
                        val fill = fillSegment(
                            c.matraCount,
                            Slot("tmp", "tmp", allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER)),
                            library, res.config.nadaiId, maxD, res.config.targetDifficulty + 1, rng, maxPieces = 4,
                        )
                        if (fill != null) out.addAll(fill) else out.add(c)
                    } else {
                        val alt = library.cells.filter {
                            it.matraCount == c.matraCount && it.usableNadais.contains(res.config.nadaiId) &&
                                it.function != CellFunction.LANDING && it.function != CellFunction.GAP &&
                                it.syllables.size > c.syllables.size && it.difficulty <= maxD
                        }
                        if (alt.isNotEmpty() && rng.next() < 0.7) out.add(alt[rng.int(alt.size)]) else out.add(c)
                    }
                }
                seg.copy(cells = out)
            },
            id = res.id + "-den", source = "remix:densify",
        )
    }

    fun simplifyRemix(res: Resolution, library: Library, seed: Int): Resolution {
        val rng = Rng(seed + 303)
        return res.copy(
            segments = res.segments.map { seg ->
                seg.cells.map { c ->
                    val alt = library.cells.filter {
                        it.matraCount == c.matraCount && it.usableNadais.contains(res.config.nadaiId) &&
                            it.function != CellFunction.LANDING &&
                            (it.kaarvai || it.syllables.size < c.syllables.size)
                    }
                    if (alt.isNotEmpty() && rng.next() < 0.75) alt[rng.int(alt.size)] else c
                }.let { seg.copy(cells = it) }
            },
            id = res.id + "-sim", source = "remix:simplify",
        )
    }

    fun changeEndingRemix(res: Resolution, library: Library, seed: Int): Resolution {
        val rng = Rng(seed + 404)
        val current = res.landingCell ?: return res.copy(id = res.id + "-end", source = "remix:ending")
        val alts = library.cells.filter {
            it.function == CellFunction.LANDING && it.matraCount == current.matraCount &&
                it.usableNadais.contains(res.config.nadaiId) && it.id != current.id
        }
        if (alts.isEmpty()) {
            val one = library.cells.firstOrNull { it.id == "c_tham" }
            if (one != null) {
                val merged = derivedLanding(one, List(current.matraCount) { one.syllables[0] }, List(current.matraCount) { 1 }, rng)
                return res.copy(landingCell = merged, landing = merged.matraCount, id = res.id + "-end", source = "remix:ending")
            }
            return res.copy(id = res.id + "-end", source = "remix:ending")
        }
        val pick = alts[rng.int(alts.size)]
        return res.copy(landingCell = pick, landing = pick.matraCount, id = res.id + "-end", source = "remix:ending")
    }

    fun changeSolkattuRemix(res: Resolution, library: Library, seed: Int): Resolution {
        val rng = Rng(seed + 505)
        val byId = res.template.slots.associateBy { it.id }
        return res.copy(
            segments = res.segments.map { seg ->
                val slot = byId[seg.slotId] ?: Slot(
                    seg.slotId, seg.label,
                    allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION),
                    allowGaps = true,
                )
                val fill = fillSegment(
                    seg.matras, slot, library, res.config.nadaiId,
                    res.config.maxDifficulty, res.config.targetDifficulty, rng,
                    firstCellMustDifferFrom = seg.cells.firstOrNull()?.id,
                ) ?: seg.cells
                seg.copy(cells = fill)
            },
            id = res.id + "-sol", source = "remix:solkattu",
        )
    }

    fun resolveWithConfig(res: Resolution, request: SolveRequest): SolveResult =
        solve(request.copy(seed = request.seed + 707))

    private val HEAVY_REGEX = Regex("^(tha|thom|tham|tom|nam|tai)", RegexOption.IGNORE_CASE)
}
