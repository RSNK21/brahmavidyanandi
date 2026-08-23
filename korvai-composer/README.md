# Korvai Composer — for Bharatanatyam

**A mathematically exact Carnatic korvai composer with an optional, validated AI layer.**

Built from `korvai-composer-android-handoff.md` with one hard architectural rule honored everywhere:

> **Rule-based tala engine + curated cell library + AI recombination layer + deterministic audio.**
> **AI proposes, the engine disposes.** No model ever performs tala arithmetic; every candidate — AI or not — is re-counted by a pure, unit-tested validator before it reaches the UI. Audio is never AI-generated.

Two deliverables share one engine design and one seed dataset:

| | Path | Status |
|---|---|---|
| **Web app** (reference implementation) | `web/index.html` | Single self-contained file — open in any browser. **64/64 engine tests + full DOM smoke tests pass.** |
| **Android app** | `android/` | Kotlin + Compose + Room, Gradle wrapper included. **Engine module compiled & tested on JVM: 62/62 pass.** App module written to standard patterns — build in Android Studio (see below). |
| **Model pipeline** | `colab/build_konakolswara_gguf.ipynb` + `MODEL_SETUP.md` | Free Colab notebook: KonakolSwaraLLM LoRA → merged → Q4_K_M GGUF → llama.cpp / LM Studio / Ollama / on-device. |
| **Seed data (single source of truth)** | `data/seed.json` | 9 talas, 5 nadais, 38 cells, 10 templates, 12 adavus. Web inlines it at build; Kotlin compiles it via `web/gen-kotlin-seed.js`. |

---

## Feature coverage vs. the handoff phases

- **V1 (offline MVP)** — done in both apps: tala/jati/nadai/kalai/eduppu/cycles/template/difficulty selectors → solve → tala grid (akshara columns, avartana rows, sam markers, clap/wave kriya), sollukattu text, nattuvangam table, deterministic audio playback with metronome, history (Room on Android, localStorage on web), seed data bundled offline.
- **V2 (remix engine)** — done: 10/50-variation batches, REVERSE, DENSIFY, SIMPLIFY, CHANGE ENDING, KEEP STRUCTURE/CHANGE SOLKATTU, nadai switching (re-solved + re-validated). All deterministic.
- **V3 (AI layer, optional & off by default)** — done: `RhythmicSelectorAI` interface, deterministic weighted selector always available, KonakolSwaraLLM client (llama.cpp `/completion` + OpenAI-compatible), lenient parser, retry loop, engine validation, silent fallback. Prompt uses the model's own trained format.
- **V4 (Bharatanatyam layer)** — done: 12-adavu catalog with heuristic character/nadai/difficulty mapping, dance-count view (8-count blocks, sam-marked, kalai-aware), nattuvangam clap/wave notation, tirmana template.

## The math (handoff §4) — implemented and tested

```
totalMatras = tala.aksharas × nadai.subdivision × kalai × avartanas
```

The solver is a backtracking constraint search over the cell library (filtered by nadai/function/difficulty), honoring per-template structure: repetitions, staircase (n, n+1, n+2), avartana multiples (teermana), diminishing series (kuraippu), macro-cell divisibility (faran), fixed cadences (mohra), front-padding with kaarvai for auto-cycle templates, and eduppu-determined landing lengths.

Canonical worked example — **Adi, Chaturasra, 2 kalai, 2 avartanas = 128 matras = (13 + 14 + 15) × 3 + 2** — is asserted in both test suites, along with: validator corruption detection (segment sums, totals, non-LANDING landings, nadai mismatches), all-template solvability, cross-tala/nadai solves, determinism (same seed ⇒ identical output), remix validity preservation, timeline/dance-count integrity.

## Run the web app

```
open web/index.html        # or double-click; no server, no network, no dependencies
node web/test/engine.test.js    # engine tests
node web/build.js               # rebuild index.html after editing src/
```

## Build the Android app

```
cd android
./gradlew :engine:test          # pure-JVM engine tests (verified here: 62/62)
./gradlew :app:assembleDebug    # needs Android Studio / SDK 34
```

Then `adb install app/build/outputs/apk/debug/app-debug.apk`.

**Documented deviations from the handoff stack** (all deliberate, all swappable):

1. **Hilt → manual `AppContainer`** — fewer annotation processors, engine stays framework-free and JVM-testable. A Hilt module would bind the same types.
2. **Retrofit → OkHttp** for the optional AI calls — one dependency, same swappability.
3. **Seed data is compiled into the engine** (`SeedData.kt`, generated from `data/seed.json`) with `assets/seed.json` shipped alongside; Room persists generated korvai history now, and the §3 full-library import path is stubbed in `SeedSource` for when the library grows beyond code-gen size.

## Project layout

```
korvai-composer/
├── data/seed.json                  # canonical seed (edit here, regenerate everywhere)
├── web/                            # single-file web app (reference implementation)
│   ├── src/{engine,ai,audio,ui}.js # engine is pure & testable; build.js inlines all
│   ├── test/engine.test.js         # 64 tests
│   ├── gen-kotlin-seed.js          # seed.json → SeedData.kt generator
│   └── index.html                  # built artifact — the app
├── android/
│   ├── engine/                     # pure Kotlin module (TalaEngine, model, tests) — JVM-verified
│   ├── app/                        # Compose UI, Room, OkHttp AI client, AudioTrack synth
│   └── gradle wrapper included
└── colab/build_konakolswara_gguf.ipynb   # KonakolSwaraLLM → GGUF pipeline
```

## Guardrails honored (handoff §8)

- ✅ No AI performs or "double-checks" tala arithmetic — plain Kotlin/JS, unit-tested against known values.
- ✅ No network or account required for V1–V2; AI strictly additive, off by default.
- ✅ No paid APIs — KonakolSwaraLLM is Apache 2.0, run locally; Colab free tier for the one-time merge.
- ✅ No AI-generated audio — synthesized percussion voices on the exact computed grid.
- ✅ No Western-notation library — custom tala-grid renderers (Compose / SVG-free DOM).
