package com.korvai.engine

/* =====================================================================
 * Data model — mirrors §3 of the build handoff.
 * Pure Kotlin, no Android dependencies, fully unit-testable on the JVM.
 * ===================================================================== */

enum class AngaType { LAGHU, DHRUTAM, ANUDHRUTAM, SECTION }
enum class Weight { H, L }
enum class CellCharacter { STRAIGHT, SQUARE, FLOWING, RESONANT, BRISK }
enum class CellFunction { CORE, FILLER, TRANSITION, ENDING, LANDING, GAP, MACRO }

data class Anga(val type: AngaType, val aksharas: Int)

data class Tala(
    val id: String,
    val name: String,
    val jati: String,
    val angas: List<Anga>,
    val aksharas: Int,
)

data class Jati(val id: String, val name: String, val laghu: Int)

data class Nadai(val id: String, val name: String, val subdivision: Int)

data class Eduppu(val id: String, val name: String, val aksharas: Double)

data class RhythmicCell(
    val id: String,
    val notation: String,
    val syllables: List<String>,
    val durations: List<Int>,
    val matraCount: Int,
    val weights: List<Weight>,
    val character: CellCharacter,
    val function: CellFunction,
    val usableNadais: List<String>,
    val difficulty: Int,
    val kaarvai: Boolean = false,
    val tags: List<String> = emptyList(),
    val derived: Boolean = false,
    val baseCellId: String? = null,
)

data class Slot(
    val id: String,
    val label: String,
    val minMatra: Int? = null,
    val maxMatra: Int? = null,
    val allowedFunctions: List<CellFunction> = emptyList(),
    val allowGaps: Boolean = false,
    val fixedCell: String? = null,
)

data class Template(
    val id: String,
    val name: String,
    val tags: List<String>,
    val description: String,
    val structure: String,
    val repetitions: Int,
    val landingMode: String,               // "eduppu" | "none"
    val staircase: Int = 0,                // each following slot +n matras (korvai crescendo)
    val multipleOf: String? = null,        // "avartana" for teermana
    val autoAvartanas: Boolean = false,    // engine derives cycle count (mohra, tirmana)
    val kind: String? = null,              // "kuraippu" | "faran" special handlers
    val slots: List<Slot>,
)

data class Alias(val notation: String, val variants: List<String>)

data class Adavu(
    val id: String,
    val name: String,
    val family: String,
    val sollukattu: String,
    val counts: Int,
    val characters: List<CellCharacter>,
    val nadais: List<String>,
    val description: String,
    val difficulty: Int,
)

data class Library(
    val talas: List<Tala>,
    val jatis: List<Jati>,
    val nadais: List<Nadai>,
    val eduppus: List<Eduppu>,
    val cells: List<RhythmicCell>,
    val aliases: List<Alias>,
    val templates: List<Template>,
    val adavus: List<Adavu>,
)

data class ResolutionConfig(
    val talaId: String,
    val talaName: String,
    val jati: String,
    val nadaiId: String,
    val nadaiName: String,
    val kalai: Int,
    val eduppuAksharas: Double,
    val avartanas: Int,
    val templateId: String,
    val templateName: String,
    val seed: Int,
    val maxDifficulty: Int,
    val targetDifficulty: Int,
    val landingMode: String,
)

data class Segment(
    val slotId: String,
    val label: String,
    val cells: List<RhythmicCell>,
    val matras: Int,
    val fixed: Boolean = false,
)

data class Resolution(
    val id: String,
    val config: ResolutionConfig,
    val template: Template,
    val segments: List<Segment>,
    val repetitions: Int,
    val landingCell: RhythmicCell?,
    val landing: Int,
    val pad: Int,
    val totalMatras: Int,
    val source: String,
    val generatedAt: String,
)

data class ValidationResult(val ok: Boolean, val errors: List<String>) {
    companion object {
        fun ok() = ValidationResult(true, emptyList())
        fun fail(vararg errors: String) = ValidationResult(false, errors.toList())
    }
}

data class SolveResult(val ok: Boolean, val resolution: Resolution? = null, val error: String? = null) {
    companion object {
        fun of(r: Resolution) = SolveResult(true, r)
        fun fail(msg: String) = SolveResult(false, null, msg)
    }
}

data class TimelineEvent(
    val matra: Int,
    val matras: Int,
    val syllable: String,
    val weight: Weight,
    val kind: String,        // "pad" | "segment" | "landing"
    val segIndex: Int,
    val cellId: String,
)

data class Timeline(val events: List<TimelineEvent>, val totalMatras: Int)

data class DanceCountBlock(val count: Int, val isSam: Boolean, val aksharaFrom: Int, val aksharaTo: Int, val sollukattu: String)
data class DanceCounts(val countsPerAvartana: Int, val blocks: List<DanceCountBlock>)

data class ClapMark(val mark: String, val angaIndex: Int) {
    companion object {
        val CLAP = "clap"; val WAVE = "wave"; val COUNT = "count"; val REST = "rest"
    }
}

/** Request shape handed to the AI selector (V3). */
data class SlotRequest(
    val id: String,
    val label: String,
    val matras: Int,
    val slot: Slot,
)

/** Generation request solved by [TalaEngine.solve]. */
data class SolveRequest(
    val tala: Tala,
    val nadai: Nadai,
    val kalai: Int,
    val eduppuAksharas: Double,
    val avartanas: Any,          // Int or the string "auto"
    val template: Template,
    val library: Library,
    val seed: Int = 1,
    val maxDifficulty: Int = 5,
    val targetDifficulty: Int = 3,
)
