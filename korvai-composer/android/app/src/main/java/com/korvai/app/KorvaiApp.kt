package com.korvai.app

import android.app.Application
import com.korvai.app.ai.DeterministicSelector
import com.korvai.app.ai.LlmSelector
import com.korvai.app.data.AppDatabase
import com.korvai.app.data.ResolutionJson
import com.korvai.app.data.SeedSource
import com.korvai.engine.Library
import com.korvai.engine.TalaEngine

/**
 * Manual DI container.
 *
 * NOTE (deviation from the handoff doc): Hilt is intentionally replaced by this
 * tiny AppContainer — fewer annotation processors, and the pure `:engine` module
 * stays framework-free so its math can be tested on the JVM without Android.
 * Swapping to Hilt later is mechanical (bind these same types in a @Module).
 */
class AppContainer(app: Application) {
    val library: Library by lazy { SeedSource.load(app) }
    val database: AppDatabase by lazy { AppDatabase.build(app) }
    val historyDao get() = database.historyDao()

    fun makeAiSelector(cfg: AiConfig, log: (String, String) -> Unit): LlmSelector =
        LlmSelector(
            engine = TalaEngine,
            cfg = com.korvai.app.ai.LlmConfig(
                api = cfg.api,
                baseUrl = cfg.baseUrl,
                model = cfg.model,
                temperature = cfg.temperature,
                maxRetries = cfg.maxRetries,
                onLog = log,
            ),
            fallback = DeterministicSelector(TalaEngine, seed = 1),
        )

    fun serializeResolution(res: com.korvai.engine.Resolution): String = ResolutionJson.toJson(res)
    fun deserializeResolution(json: String): com.korvai.engine.Resolution? = ResolutionJson.fromJson(json, library)
}

class KorvaiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogger()
    }

    /**
     * If anything still manages to kill the process, write the stack trace to
     * filesDir/korvai-crash-log.txt first (capped, appended). The next launch
     * shows it so the failure can be reported instead of staying invisible.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = java.io.File(filesDir, "korvai-crash-log.txt")
                val entry = "--- ${java.time.LocalDateTime.now()} thread=${thread.name} ---\n" +
                    throwable.toString() + "\n" +
                    throwable.stackTraceToString().take(1800) + "\n"
                val existing = if (f.exists()) f.readText() else ""
                f.writeText((entry + existing).take(8000))
            } catch (_: Exception) { /* never block the crash */ }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

/** User-tunable settings for the optional AI layer (persisted as JSON by the ViewModel). */
data class AiConfig(
    val api: String = "llamacpp",              // "llamacpp" | "openai"
    val baseUrl: String = "http://127.0.0.1:8080",
    val model: String = "konakolswara-llm",
    val temperature: Double = 0.7,
    val maxRetries: Int = 2,
    val enabled: Boolean = false,
)
