package com.korvai.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.korvai.engine.Timeline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic sollukattu renderer.
 *
 * Guardrail (handoff §6/§8): audio is NEVER AI-generated. Syllables are
 * synthesized percussion voices (noise bursts / sine drops) placed exactly on
 * the computed tala grid, then played through AudioTrack in one static buffer —
 * sample-accurate and fully deterministic.
 */
class SollukattuPlayer(private val sampleRate: Int = 44100) {

    private var track: AudioTrack? = null
    @Volatile private var renderJob: Thread? = null

    /** Synthesize the whole performance (loops ×) into PCM and play. */
    fun play(
        timeline: Timeline,
        subdivision: Int,
        kalai: Int,
        aksharasPerAvartana: Int,
        bpm: Int,
        metronome: Boolean,
        loops: Int,
        onFinished: () -> Unit = {},
    ) {
        stop()
        val matraDur = 60.0 / (bpm * subdivision * kalai)
        val totalMatras = timeline.totalMatras * loops
        val totalSamples = (totalMatras * matraDur * sampleRate).toInt() + sampleRate / 2
        val pcm = ShortArray(totalSamples)

        for (l in 0 until loops) {
            val baseMatra = l * timeline.totalMatras
            for (e in timeline.events) {
                val at = ((baseMatra + e.matra) * matraDur * sampleRate).toInt()
                renderSyllable(pcm, at, e.syllable, e.weight.name == "H")
            }
            if (metronome) {
                val matrasPerAkshara = subdivision * kalai
                val aksharas = (timeline.totalMatras + matrasPerAkshara - 1) / matrasPerAkshara
                for (a in 0 until aksharas) {
                    val at = ((baseMatra + a * matrasPerAkshara) * matraDur * sampleRate).toInt()
                    renderTick(pcm, at, a % aksharasPerAvartana == 0)
                }
            }
        }

        // fade tail to avoid clicks
        val n = pcm.size
        for (i in 0 until (sampleRate / 200)) {
            if (n - 1 - i >= 0) pcm[n - 1 - i] = (pcm[n - 1 - i] * i / (sampleRate / 200)).toInt().toShort()
        }

        val bufSize = maxOf(pcm.size * 2, AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
        val t = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufSize,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        t.write(pcm, 0, pcm.size)
        t.setNotificationMarkerPosition(pcm.size - 8)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) { onFinished() }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        })
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
    }

    /* ---------- voices (mirror of the web synth) ---------- */

    private var noiseState = 22222L
    private fun noise(): Double {
        // xorshift32 — deterministic "white" noise
        var x = noiseState
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        noiseState = x
        return ((x and 0xffffffffL).toDouble() / 4294967295.0) * 2.0 - 1.0
    }

    private fun add(pcm: ShortArray, i: Int, v: Double) {
        if (i in pcm.indices) {
            val s = pcm[i] + (v * Short.MAX_VALUE * 0.9).toInt()
            pcm[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun noiseBurst(pcm: ShortArray, at: Int, freqHz: Double, durS: Double, gain: Double, q: Double = 1.2) {
        val len = (durS * sampleRate).toInt()
        // crude band-pass: subtract a one-pole lowpass of the noise from another
        var lp1 = 0.0; var lp2 = 0.0
        val a1 = exp(-2.0 * PI * freqHz * 1.4 / sampleRate)
        val a2 = exp(-2.0 * PI * freqHz / (q * sampleRate) * 8.0)
        for (i in 0 until len) {
            val n = noise()
            lp1 = a1 * lp1 + (1 - a1) * n
            lp2 = a2 * lp2 + (1 - a2) * n
            val band = lp2 - lp1
            val env = exp(-4.5 * i / len)
            add(pcm, at + i, band * env * gain * 2.4)
        }
    }

    private fun ping(pcm: ShortArray, at: Int, freq: Double, durS: Double, gain: Double) {
        val len = (durS * sampleRate).toInt()
        var phase = 0.0
        for (i in 0 until len) {
            phase += 2.0 * PI * freq / sampleRate
            val env = exp(-6.0 * i / len)
            add(pcm, at + i, (2.0 / PI) * sin(phase) * env * gain) // triangle-ish
        }
    }

    private fun drop(pcm: ShortArray, at: Int, f0: Double, f1: Double, durS: Double, gain: Double) {
        val len = (durS * sampleRate).toInt()
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / len
            val f = f0 * (f1 / f0).pow(t * 0.8.coerceAtMost(1.0 - t / 1.25))
            phase += 2.0 * PI * f / sampleRate
            val env = exp(-4.0 * i / len)
            add(pcm, at + i, sin(phase) * env * gain)
        }
    }

    private fun Double.pow(x: Double) = Math.pow(this, x)

    private fun renderSyllable(pcm: ShortArray, at: Int, syllable: String, heavy: Boolean) {
        val g = if (heavy) 1.0 else 0.66
        when (syllable.lowercase()) {
            "ta", "tat", "that", "thit", "dit", "hat" -> { noiseBurst(pcm, at, 3200.0, 0.045, 0.5 * g); ping(pcm, at, 2400.0, 0.03, 0.10 * g) }
            "ka", "ki", "ri", "yai", "hi" -> { noiseBurst(pcm, at, 2100.0, 0.05, 0.45 * g); ping(pcm, at, 1500.0, 0.04, 0.12 * g) }
            "ju", "nu", "jo", "mi" -> { noiseBurst(pcm, at, 1300.0, 0.06, 0.40 * g); ping(pcm, at, 900.0, 0.05, 0.14 * g) }
            "di", "dhi" -> { noiseBurst(pcm, at, 1800.0, 0.05, 0.42 * g); ping(pcm, at, 1200.0, 0.04, 0.12 * g) }
            "tha", "tai", "thai" -> { drop(pcm, at, 230.0, 150.0, 0.14, 0.50 * g); noiseBurst(pcm, at, 2600.0, 0.03, 0.12 * g) }
            "thom", "tom", "dhin" -> drop(pcm, at, 120.0, 55.0, 0.28, 0.62 * g)
            "tham", "tam", "nam" -> { drop(pcm, at, 95.0, 70.0, 0.30, 0.65 * g); ping(pcm, at, 190.0, 0.22, 0.12 * g) }
            "—", "-" -> { /* kaarvai: silence */ }
            else -> noiseBurst(pcm, at, 2000.0, 0.05, 0.40 * g)
        }
    }

    private fun renderTick(pcm: ShortArray, at: Int, accent: Boolean) {
        ping(pcm, at, if (accent) 1660.0 else 1100.0, if (accent) 0.09 else 0.05, if (accent) 0.30 else 0.14)
    }
}
