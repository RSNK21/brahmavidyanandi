# AI Build Handoff: Korvai Composer for Bharatanatyam (Android)

**Target reader:** an AI coding agent (or human developer) picking this up cold, with no prior context beyond this document.

**Constraint that overrides all model choices below: NO PAID AI APIs.** Every AI component must run on a free tier, a free/open-weight model, or fully on-device. This is non-negotiable — see §6 for the exact free-model matrix.

---

## 0. One-paragraph summary

Build an Android app that composes mathematically valid Carnatic rhythmic patterns (korvais) for Bharatanatyam dancers. The system is **NOT an "AI writes a korvai" app**. It is a deterministic tala/rhythm engine with a curated, parameterized library of rhythmic cells and structural templates, where an AI model is used only as a *selector/creative-suggestion layer* inside constraints the engine defines and validates. The AI never has authority over beat-count correctness — the engine does.

```
Rule-based tala engine  +  curated jathi/korvai pattern library  +  AI recombination layer  +  audio/solkattu renderer
```

---

## 1. Why this architecture (do not deviate)

- Carnatic tala math (aksharas, angas, nadai, kalai, eduppu) is exact arithmetic. LLMs are not reliable at exact arithmetic over long sequences.
- A specialized open model for this domain (KonakolSwaraLLM, on Hugging Face) explicitly states in its own model card that beat-count precision is **not** guaranteed.
- Therefore: **AI proposes, engine disposes.** Every AI-generated candidate is validated against the mathematical tala model before it ever reaches the UI. Failed candidates are silently regenerated or rejected — never shown to the user as "maybe right."
- A prior-art app, KorvAI, already generates korvais across talas/eduppus/akshara counts — confirming the deterministic-generation approach is viable and expected by users in this space. This app differentiates by being Bharatanatyam-specific (adavu compatibility, dance-count structure, nattuvangam rendering) rather than a generic Carnatic tool.

---

## 2. High-level pipeline

```
USER (Android UI)
      ↓
TALAM CONSTRAINT ENGINE      (pure Kotlin, deterministic, no network)
      ↓
TEMPLATE SELECTOR             (picks a structural skeleton: A-A-A, A-B-A, mohra→korvai, kuraippu, etc.)
      ↓
AI RHYTHMIC SELECTOR          (free/local model chooses which cells fill the template's slots)
      ↓
MATHEMATICAL VALIDATOR        (pure Kotlin — re-derives akshara/matra counts, rejects on mismatch)
      ↓ pass                          ↓ fail → loop back to AI selector (max N retries, then fall back
      ↓                                          to a non-AI deterministic cell-picker so the app never
      ↓                                          hangs or dead-ends without network/model access)
RENDERER
 ├── Tala grid (Compose Canvas)
 ├── Sollukattu text (with IAST-safe rendering)
 ├── Nattuvangam notation
 ├── Dance-count structure (adavu mapping)
 └── Audio (sequenced samples, deterministic — never AI-generated audio)
```

**Critical rule for the coding agent:** the AI call is *optional* at runtime. If there is no network and no local model loaded, the app must still function using template + cell library alone (random/weighted deterministic selection). AI is a quality enhancement, never a hard dependency.

---

## 3. Data model (implement first, before any UI or AI code)

This is the actual foundation. Get this right and everything else is comparatively easy.

```
Tala
 ├── id, name (e.g. "Adi")
 ├── jati (Chaturasra/Tisra/Khanda/Misra/Sankirna)
 ├── angas: List<Int>            // e.g. [4,2,2] for Adi
 └── aksharas: Int               // sum(angas)

Nadai (gati)
 ├── id, name
 └── subdivision: Int            // Tisra=3, Chaturasra=4, Khanda=5, Misra=7, Sankirna=9

Kalai: Int                       // speed multiplier (1, 2, ...)

RhythmicCell
 ├── id
 ├── notation: String            // e.g. "ta ki ta"
 ├── syllables: List<String>
 ├── matraCount: Int
 ├── weightPattern: List<Weight> // light/heavy per syllable, for accent rendering
 ├── character: Enum             // STRAIGHT, SQUARE, FLOWING, etc.
 ├── function: Enum              // CORE, FILLER, TRANSITION, ENDING
 ├── usableNadais: List<NadaiRef>
 └── difficulty: Int (1-5)

Template
 ├── id, name
 ├── structure: String           // e.g. "A B A", "A A B ×3", "M M M / K K K"
 ├── slotConstraints: Map<SlotId, {minMatra, maxMatra, allowedCellFunctions}>
 ├── minLength / maxLength
 └── tags: List<String>          // e.g. "mohra", "korvai", "kuraippu", "tirmana"

Korvai (generated output — persisted)
 ├── id
 ├── talaId, jatiId, nadaiId, kalai, eduppu
 ├── templateId
 ├── filledCells: List<RhythmicCellRef>   // the resolved A/B/C slot values
 ├── solkattuText: String
 ├── totalMatras: Int
 ├── resolutionType: Enum         // SAM, ARUDI, MORA, etc.
 ├── adavuSuggestions: List<AdavuRef>     // Bharatanatyam-specific layer
 ├── difficulty: Int
 └── audioRenderRef: String?      // path to rendered/cached audio
```

Store this in **Room** (SQLite) on-device. Ship the seed data (talas, nadais, an initial rhythmic-cell library, and a template library — start with 15-30 cells and 8-10 templates) bundled as a JSON asset that's imported into Room on first launch. This is the same shape as the JSON examples in the source design doc — reuse those verbatim as seed data.

---

## 4. The math (implement as pure, unit-tested Kotlin — no AI, no network)

```kotlin
fun totalMatras(tala: Tala, nadai: Nadai, kalai: Int, avartanas: Int): Int =
    tala.aksharas * nadai.subdivision * kalai * avartanas
```

Given a chosen template with slots (e.g. A, B, C repeated 3×), the engine must solve for slot lengths that sum to the target matra count exactly, accounting for the required resolution phrase (sam/arudi/mora landing point). This is a constraint-satisfaction problem — implement it as plain backtracking/search over the cell library filtered by `usableNadais` and `matraCount`, **not** as an AI call. See §11 of the source design doc for the worked example (13+14+15 ×3 = 126, +2 landing units).

The validator re-runs this exact arithmetic against whatever the template selector (AI or deterministic) produced. Any mismatch = reject, no exceptions.

---

## 5. Android tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | standard, required for Compose |
| UI | Jetpack Compose | matches the "drag cells around" advanced editor requirement (§13 of source doc) and Canvas-based tala grid |
| Local DB | Room (SQLite) | seed data + generated korvai history, fully offline-capable |
| Networking | Retrofit + OkHttp (only for optional cloud AI calls) | swappable, easy to strip out for pure-offline builds |
| On-device inference | **MediaPipe LLM Inference API** or **llama.cpp via JNI (llama.cpp Android example)** | run a free, open-weight small model fully offline |
| Audio | Android's `SoundPool`/`AudioTrack` for MVP, migrate to **Oboe** (C++/JNI, Google's low-latency audio lib, free/open-source) for tight rhythmic sequencing later | deterministic sample playback, not AI-generated audio (§12 of source doc) |
| Dependency injection | Hilt | standard Android choice |
| Background/async | Kotlin Coroutines + Flow | |
| Notation rendering | Custom Compose Canvas for the tala grid/sollukattu (do not depend on a music-notation library — Carnatic tala structure doesn't map cleanly onto Western notation libs, per §13 of source doc) | |

---

## 6. Free-AI-only model matrix (§6/§7 of source doc, adapted for "free only")

The AI's *only* job is: given a template's slots and their matra/character constraints, propose which rhythmic cells (from the curated library) fill each slot, and optionally propose small syllable-level variations. It is never asked to do arithmetic or invent tala structure from scratch.

Pick **one** of these, in order of recommended preference:

1. **Fully offline, on-device (recommended default — zero cost, zero network dependency, works for a rehearsal-tool use case):**
   - Run a small open-weight model (e.g. a distilled/quantized Llama, Gemma, or Phi model in GGUF format, 1-3B parameters) via **llama.cpp**'s Android bindings or **MediaPipe LLM Inference API**. Both are free/open-source and run entirely on-device — no API key, no rate limit, no ongoing cost.
   - Quantize to 4-bit (Q4_K_M or similar) so the model fits comfortably on a phone (~1-2 GB).
   - This model is only ever given short, structured prompts like: "Given slots A (3-4 matras, function=CORE, nadai=chaturasra) and B (4 matras, function=FILLER), and this cell library [...], propose one cell per slot." It returns cell IDs, not raw audio or unconstrained text.

2. **Free-tier cloud API (fallback when network is available and the user opts in — never required):**
   - Use a provider's free tier (e.g. Google AI Studio's free Gemini API tier, or Groq's free tier for open-weight models like Llama). Whichever is used, gate it behind a settings toggle ("Use online AI suggestions") that defaults **off**, so the app is free-by-default with no account needed.
   - Never send anything beyond the anonymized structural prompt (tala/slot constraints, cell library metadata) — no user-identifying data.

3. **Domain-specialized (research/stretch goal, §6-7 of source doc):**
   - `KonakolSwaraLLM` on Hugging Face is free and specialized for Carnatic rhythmic generation. Its outputs still **must** pass through the same mathematical validator as everything else — its own documentation disclaims beat-count guarantees.
   - Longer-term (§7 of source doc): once the app has generated/curated enough validated korvai records in the JSON shape shown in §7, fine-tune a small open model (e.g. via free Colab/Kaggle compute, LoRA on a 1-3B base model) specifically on this app's own representation. This is a v3/v4 milestone, not part of MVP.

**Hard rule for the coding agent:** whichever option is chosen, the interface must be an abstraction (`interface RhythmicSelectorAI { suspend fun proposeCells(slots: List<Slot>, library: List<RhythmicCell>): Result<Map<SlotId, CellId>> }`) with a **non-AI deterministic fallback implementation** (weighted-random selection from the cell library respecting slot constraints) always available. AI must be swappable/optional, never load-bearing.

**Audio must never be AI-generated.** Deterministic sample sequencing only (§12 of source doc): pre-recorded or synthesized syllable samples (ta/ka/dhi/mi/tom/nam/...) played back exactly on the calculated timing grid.

---

## 7. Build phases (adapt §"4 stages" of the source doc to Android milestones)

**V1 — No AI, offline-only MVP**
- Room DB + seed data (talas, nadais, ~20 rhythmic cells, ~6 templates)
- Deterministic constraint-solver engine (§4 above)
- Basic Compose UI: tala/jathi/nadai/kalai/eduppu/cycles/difficulty selectors → Generate button
- Tala grid + sollukattu text rendering
- Deterministic sample-based audio playback
- **Ship this first. It's a complete, useful, free, offline app on its own.**

**V2 — Remix engine**
- "Generate 10/50 variations" from a fixed rhythmic DNA (§14 of source doc): same structural skeleton (e.g. `3-4-3 | 3-4-3 | 3-4-3`), varying syllables/nadai/accent
- Remix buttons: REVERSE, DENSIFY, SIMPLIFY, CHANGE JATHI, CHANGE NADAI, CHANGE ENDING, KEEP STRUCTURE/CHANGE SOLKATTU, KEEP SOLKATTU/CHANGE STRUCTURE (§9 of source doc)
- All still deterministic — no AI required yet

**V3 — AI layer (optional, off by default)**
- Wire in the on-device model from §6 option 1
- Natural-language remix requests: "make this more complex," "use tisra inside chaturasra," "give a more traditional-sounding version"
- Every AI output still routes through the validator from §4

**V4 — Bharatanatyam composer layer**
- Adavu compatibility mapping (Tatta, Natta, Visharu, Tirmana, Kuditthu mettu, Sarikkal, Mandi, etc. — §8 of source doc)
- Dance-count structure display alongside sollukattu
- Nattuvangam-specific notation/voice rendering
- "Show me a 16-akshara tirmāna suitable for this jathi" style targeted generation

---

## 8. Explicit non-goals / guardrails for the coding agent

- Do not let any AI model perform or "double-check" tala arithmetic — that's the validator's job, in plain Kotlin, unit-tested against known-correct examples (Adi tala Chaturasra = 8 aksharas, etc.).
- Do not require a network connection or an account for V1-V2. AI is strictly additive and optional, and only in V3+.
- Do not use a paid API at any point without an explicit, separate decision by the project owner — this document assumes free-only for the entire build.
- Do not generate audio via AI/generative-audio models; use deterministic sample sequencing.
- Do not adopt a Western music-notation library as the core renderer; build a custom tala-grid view.

---

## 9. First concrete tasks for whoever/whatever picks this up

1. Scaffold the Android project (Kotlin, Compose, Hilt, Room).
2. Encode the `Tala`/`Nadai`/`RhythmicCell`/`Template`/`Korvai` data classes exactly as in §3, with Room entities + DAOs.
3. Write the seed-data JSON (start with Adi tala, Chaturasra jati/nadai, ~20 cells drawn from the examples in the source design doc: takita, takadhimi, tarikita, takajonu, dhinagatom, etc.) and an import routine that runs once on first app launch.
4. Implement and unit-test the matra-count solver from §4 against the worked example (13+14+15 ×3 + 2 = 128 for Adi/Chaturasra/2kalai/2avartanas).
5. Build the V1 Compose UI (selectors → Generate → tala grid + sollukattu + play button), wired to the deterministic engine only. No AI yet.
6. Only after V1 is working end-to-end offline, add the `RhythmicSelectorAI` interface and the on-device free-model implementation from §6.
