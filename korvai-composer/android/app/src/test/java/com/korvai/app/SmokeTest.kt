package com.korvai.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Replays the exact app flow on the JVM with the real Android framework
 * (Room, Looper, assets, org.json): startup generate → user clicks
 * "Compose Korvai" → remix ops → history round-trip.
 *
 * Any uncaught exception on ANY thread (exactly what closes the app on a
 * phone) is captured and fails the test with the full stack trace.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = KorvaiApp::class)
class SmokeTest {

    private fun drain(ms: Long, until: () -> Boolean = { true }) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < ms) {
            shadowOf(Looper.getMainLooper()).idle()
            if (until()) return
            Thread.sleep(40)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withCrashCapture(block: (MutableList<Throwable>) -> Unit) {
        val recorded = mutableListOf<Throwable>()
        val latch = CountDownLatch(1)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            synchronized(recorded) { recorded.add(throwable) }
            println("UNCAUGHT on ${thread.name}: $throwable")
            throwable.printStackTrace()
        }
        try {
            block(recorded)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        synchronized(recorded) {
            check(recorded.isEmpty()) {
                "uncaught exceptions (would close the app):\n" +
                    recorded.joinToString("\n\n") { t ->
                        t.toString() + "\n" + t.stackTraceToString().take(1500)
                    }
            }
        }
    }

    @Test
    fun exactUserFlow_startup_then_composeClick() {
        withCrashCapture { recorded ->
            val app = ApplicationProvider.getApplicationContext<KorvaiApp>()
            val vm = KorvaiViewModel(app)          // ← app opens (init generate + persist)
            drain(6000) { vm.state.value.history.isNotEmpty() }

            checkNotNull(vm.state.value.resolution) { "startup resolution missing: ${vm.state.value.error}" }
            check(vm.state.value.validation?.ok == true) { "startup validation failed" }

            vm.generate()                           // ← user clicks ✦ Compose Korvai
            drain(6000) { vm.state.value.history.size >= 2 }
            checkNotNull(vm.state.value.resolution) { "click resolution missing: ${vm.state.value.error}" }

            vm.generate()                           // ← clicks again
            drain(6000) { vm.state.value.history.size >= 3 }

            check(vm.state.value.history.size >= 3) {
                "history not persisting (size=${vm.state.value.history.size})"
            }

            // remix ops (the V2 row)
            listOf("reverse", "densify", "simplify", "changeEnding", "changeSolkattu").forEach {
                vm.remix(it)
                drain(2000)
            }
            checkNotNull(vm.state.value.resolution)

            vm.generateVariations(5)
            drain(3000)
            if (vm.state.value.variations.isNotEmpty()) vm.loadVariation(0)
            drain(2000)

            vm.reseed()
            drain(4000)
            checkNotNull(vm.state.value.resolution)
        }
    }

    @Test
    fun daoRoundTrip_direct() {
        val app = ApplicationProvider.getApplicationContext<KorvaiApp>()
        val container = app.container
        val vm = KorvaiViewModel(app)
        drain(4000) { vm.state.value.resolution != null }
        val res = checkNotNull(vm.state.value.resolution)
        runBlocking {
            val json = container.serializeResolution(res)
            val parsed = checkNotNull(container.deserializeResolution(json)) { "round-trip JSON failed" }
            check(parsed.totalMatras == res.totalMatras)
            container.historyDao.insert(
                com.korvai.app.data.KorvaiHistoryEntity(
                    savedAt = 1L, templateId = "t", talaName = "Adi", nadaiName = "Chaturasra",
                    kalai = 1, avartanas = 2, totalMatras = 64, source = "engine", json = json,
                )
            )
            val rows = container.historyDao.recent()
            check(rows.isNotEmpty() && rows[0].json.contains("\"totalMatras\"")) { "DAO read-back failed" }
        }
    }
}
