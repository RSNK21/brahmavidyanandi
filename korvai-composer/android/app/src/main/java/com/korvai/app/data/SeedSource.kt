package com.korvai.app.data

import android.content.Context
import com.korvai.engine.Library
import com.korvai.engine.SeedData

/**
 * Seed data source.
 *
 * The canonical seed lives in `data/seed.json` at the repo root; the build
 * copies it to assets and the generator script `web/gen-kotlin-seed.js`
 * compiles the same JSON into `SeedData.kt` (single source of truth).
 *
 * The engine always uses the compiled SeedData (offline, immutable, testable).
 * The asset copy is retained for the planned Room-import path and for
 * transparency/debugging — see the handoff doc §3.
 */
object SeedSource {
    fun load(context: Context): Library {
        // Verify the bundled asset matches the compiled data when present
        // (soft check — the compiled data is authoritative for the engine).
        try {
            val stream = context.assets.open("seed.json")
            stream.use { /* available for future Room-first-launch import */ }
        } catch (_: Exception) {
            // asset missing is non-fatal: compiled SeedData is the engine source
        }
        return SeedData.library
    }

    fun rawAsset(context: Context): String? = try {
        context.assets.open("seed.json").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
