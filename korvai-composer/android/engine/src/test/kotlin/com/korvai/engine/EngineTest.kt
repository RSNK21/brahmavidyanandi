package com.korvai.engine

/* =====================================================================
 * Engine unit tests — plain-JVM main() runner (no framework needed).
 * Run: kotlinc engine + this file, then java com.korvai.engine.EngineTestKt
 * Mirrors web/test/engine.test.js.
 * ===================================================================== */

private var pass = 0
private var fail = 0
private fun assert(cond: Boolean, msg: String) {
    if (cond) pass++ else { fail++; System.err.println("  ✗ FAIL: $msg") }
}
private fun section(name: String) = println("■ $name")

private val lib: Library = SeedData.library
private fun <T : Any> List<T>.byId(id: String, f: (T) -> String): T = first { f(it) == id }
private val talaOf = { id: String -> lib.talas.byId(id) { it.id } }
private val nadaiOf = { id: String -> lib.nadais.byId(id) { it.id } }
private val tplOf = { id: String -> lib.templates.byId(id) { it.id } }
private val cellOf = { id: String -> lib.cells.byId(id) { it.id } }

fun main() {
    val E = TalaEngine

    /* ---------- 1. tala math ---------- */
    section("Tala arithmetic")
    run {
        val adi = talaOf("adi"); val ch = nadaiOf("chaturasra")
        assert(E.totalMatras(adi, ch, 1, 1) == 32, "Adi chaturasra 1kalai 1av = 32")
        assert(E.totalMatras(adi, ch, 2, 2) == 128, "Adi chaturasra 2kalai 2av = 128")
        assert(E.totalMatras(adi, nadaiOf("tisra"), 1, 1) == 24, "Adi tisra = 24")
        assert(E.applyJati(adi, "tisra", lib.jatis).aksharas == 7, "Adi + Tisra jati = 7 aksharas")
        assert(E.applyJati(adi, "khanda", lib.jatis).aksharas == 9, "Adi + Khanda jati = 9 aksharas")
    }

    /* ---------- 2. canonical 13-14-15 korvai ---------- */
    section("Canonical korvai 13-14-15 (Adi, Chaturasra, 2 kalai, 2 avartanas)")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val ch = nadaiOf("chaturasra")
        val out = E.solve(
            SolveRequest(adi, ch, 2, 0.0, 2, tplOf("korvai_crescendo"), lib, seed = 42, maxDifficulty = 5, targetDifficulty = 3)
        )
        assert(out.ok, "solver succeeds: ${out.error ?: ""}")
        out.resolution?.let { r ->
            val lens = r.segments.map { it.matras }
            assert(lens.size == 9, "9 segments, got ${lens.size}")
            assert(lens[0] == 13 && lens[1] == 14 && lens[2] == 15, "staircase 13/14/15, got ${lens[0]}/${lens[1]}/${lens[2]}")
            assert(r.landing == 2, "landing = 2")
            assert(r.totalMatras == 128, "total = 128, got ${r.totalMatras}")
            assert(E.validateResolution(r, adi, ch).ok, "validator passes")
            println("    " + E.resolutionSollukattu(r).take(110) + "…")
        }
    }

    /* ---------- 3. validator catches corruption ---------- */
    section("Validator rejects broken output")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val ch = nadaiOf("chaturasra")
        val out = E.solve(SolveRequest(adi, ch, 1, 0.0, 1, tplOf("korvai_x3"), lib, seed = 7))
        assert(out.ok, "korvai_x3 solvable")
        out.resolution?.let { r ->
            val bad = r.copy(segments = r.segments.mapIndexed { i, s -> if (i == 0) s.copy(cells = s.cells.drop(1)) else s })
            assert(!E.validateResolution(bad, adi, ch).ok, "detects segment sum mismatch")
            assert(!E.validateResolution(r.copy(totalMatras = r.totalMatras + 1), adi, ch).ok, "detects totalMatras mismatch")
            assert(!E.validateResolution(r.copy(landingCell = cellOf("c_taka")), adi, ch).ok, "detects non-LANDING landing cell")
        }
    }

    /* ---------- 4. all templates solvable ---------- */
    section("All templates solvable (Chaturasra)")
    run {
        for (tpl in lib.templates) {
            var ok = false
            outer@ for (kalai in intArrayOf(1, 2)) {
                for (cycles in intArrayOf(1, 2, 3, 4)) {
                    val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
                    val ch = nadaiOf("chaturasra")
                    val av: Any = if (tpl.autoAvartanas) "auto" else cycles
                    val out = E.solve(SolveRequest(adi, ch, kalai, 0.0, av, tpl, lib, seed = 11))
                    if (out.ok && E.validateResolution(out.resolution!!, adi, ch).ok) { ok = true; break@outer }
                }
            }
            assert(ok, "template ${tpl.id} solved+validated")
        }
    }

    /* ---------- 5. other talas ---------- */
    section("Other talas")
    run {
        for (tid in listOf("rupaka", "misra_chapu", "ata", "tisra_triputa", "khanda_chapu")) {
            val tala = E.applyJati(talaOf(tid), talaOf(tid).jati, lib.jatis)
            val ch = nadaiOf("chaturasra")
            var ok = false
            for (cycles in 1..6) {
                val out = E.solve(SolveRequest(tala, ch, 1, 0.0, cycles, tplOf("korvai_x3"), lib, seed = 5))
                if (out.ok) { ok = true; break }
            }
            assert(ok, "korvai_x3 on $tid")
        }
    }

    /* ---------- 6. nadais ---------- */
    section("Nadais")
    run {
        for (nid in listOf("tisra", "khanda")) {
            val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
            val nadai = nadaiOf(nid)
            var ok = false
            for (cycles in 1..4) {
                val out = E.solve(SolveRequest(adi, nadai, 1, 0.0, cycles, tplOf("korvai_x3"), lib, seed = 9))
                if (out.ok && E.validateResolution(out.resolution!!, adi, nadai).ok) { ok = true; break }
            }
            assert(ok, "korvai_x3 in $nid nadai")
        }
    }

    /* ---------- 7. kuraippu ---------- */
    section("Kuraippu")
    run {
        assert(E.kuraippuLengths(126) == listOf(64, 32, 16, 8, 4, 2), "126 → 64+32+16+8+4+2")
        assert(E.kuraippuLengths(50).sum() == 50, "arbitrary body sums exactly")
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val out = E.solve(SolveRequest(adi, nadaiOf("chaturasra"), 2, 0.0, 2, tplOf("kuraippu"), lib, seed = 3))
        assert(out.ok, "kuraippu solves on Adi 2-kalai 2-av")
        out.resolution?.let { r ->
            val lens = r.segments.map { it.matras }
            assert(lens.first() > lens.last(), "diminishes")
            println("    lengths: ${lens.joinToString(", ")} landing ${r.landing}")
        }
    }

    /* ---------- 8. auto templates ---------- */
    section("Mohra→Korvai and Tirmana (auto cycles)")
    run {
        for (tid in listOf("mohra_korvai", "tirmana")) {
            val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
            val ch = nadaiOf("chaturasra")
            val out = E.solve(SolveRequest(adi, ch, 1, 0.0, "auto", tplOf(tid), lib, seed = 13))
            assert(out.ok, "$tid solves (auto)")
            out.resolution?.let { r ->
                assert(E.validateResolution(r, adi, ch).ok, "$tid validates")
                println("    $tid: cycles=${r.config.avartanas} pad=${r.pad} total=${r.totalMatras} segs=${r.segments.size}")
            }
        }
    }

    /* ---------- 9. faran ---------- */
    section("Faran")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val out = E.solve(SolveRequest(adi, nadaiOf("chaturasra"), 1, 0.0, "auto", tplOf("faran"), lib, seed = 21))
        assert(out.ok, "faran solves")
        out.resolution?.let { r ->
            val m = r.segments[0].cells[0].matraCount
            assert(m * r.segments.size == r.totalMatras, "macro × reps = total")
            println("    faran: ${m}×${r.segments.size} = ${r.totalMatras}")
        }
    }

    /* ---------- 10. determinism ---------- */
    section("Determinism")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val base = SolveRequest(adi, nadaiOf("chaturasra"), 2, 0.0, 2, tplOf("korvai_crescendo"), lib, maxDifficulty = 5, targetDifficulty = 3)
        val a = E.solve(base.copy(seed = 100)); val b = E.solve(base.copy(seed = 100)); val c = E.solve(base.copy(seed = 101))
        assert(a.ok && b.ok && c.ok, "all solve")
        assert(E.resolutionSollukattu(a.resolution!!) == E.resolutionSollukattu(b.resolution!!), "same seed ⇒ identical")
        assert(E.resolutionSollukattu(a.resolution!!) != E.resolutionSollukattu(c.resolution!!), "diff seed ⇒ different")
    }

    /* ---------- 11. remix ops ---------- */
    section("Remix ops preserve validity")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val ch = nadaiOf("chaturasra")
        val out = E.solve(SolveRequest(adi, ch, 2, 0.0, 2, tplOf("korvai_crescendo"), lib, seed = 42))
        assert(out.ok, "base solve")
        out.resolution?.let { r ->
            val ops = listOf(
                "reverse" to E.reverseRemix(r, 77),
                "densify" to E.densifyRemix(r, lib, 77),
                "simplify" to E.simplifyRemix(r, lib, 77),
                "changeEnding" to E.changeEndingRemix(r, lib, 77),
                "changeSolkattu" to E.changeSolkattuRemix(r, lib, 77),
            )
            for ((name, remixed) in ops) {
                val v = E.validateResolution(remixed, adi, ch)
                assert(v.ok, "remix $name validates" + (if (v.ok) "" else ": ${v.errors.joinToString("; ")}"))
                assert(remixed.totalMatras == r.totalMatras, "remix $name keeps total")
            }
        }
    }

    /* ---------- 12. dance counts + timeline ---------- */
    section("Dance counts, adavus, timeline")
    run {
        val adi = E.applyJati(talaOf("adi"), "chaturasra", lib.jatis)
        val ch = nadaiOf("chaturasra")
        val out = E.solve(SolveRequest(adi, ch, 1, 0.0, 1, tplOf("korvai_x3"), lib, seed = 42))
        assert(out.ok, "base solve")
        out.resolution?.let { r ->
            val dc = E.danceCounts(r, adi, ch)
            assert(dc.blocks.size == 8, "Adi 1-kalai ⇒ 8 counts, got ${dc.blocks.size}")
            val ad = E.suggestAdavus(r, lib, "chaturasra")
            assert(ad.isNotEmpty(), "adavu suggestions exist")
            println("    adavus: " + ad.joinToString(" · ") { it.name })
            val tl = E.buildTimeline(r)
            assert(tl.totalMatras == r.totalMatras, "timeline covers every matra")
            assert(tl.events.first().matra == 0, "starts at 0")
            assert(tl.events.last().let { it.matra + it.matras } == tl.totalMatras, "ends exactly at total")
        }
    }

    println()
    println("$pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
