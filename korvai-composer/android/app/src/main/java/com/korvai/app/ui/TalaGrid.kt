package com.korvai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korvai.engine.Nadai
import com.korvai.engine.Resolution
import com.korvai.engine.Tala
import com.korvai.engine.TalaEngine
import com.korvai.engine.Timeline
import com.korvai.engine.Weight

private val SEG_COLORS = listOf(
    Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFFF97316), Color(0xFF10B981),
)

private val CLAP_ICON = mapOf("clap" to "👏", "wave" to "👋", "count" to "•", "rest" to "·")

/**
 * Tala grid: one row per avartana, one column per akshara, one cell per matra.
 * Custom Compose layout — Carnatic tala structure doesn't map onto Western
 * notation libraries (handoff §5/§13).
 */
@Composable
fun TalaGrid(resolution: Resolution, tala: Tala, nadai: Nadai) {
    val timeline: Timeline = TalaEngine.buildTimeline(resolution)
    val matrasPerAkshara = nadai.subdivision * resolution.config.kalai
    val claps = TalaEngine.clapPattern(tala)
    val aksharaCount = (timeline.totalMatras + matrasPerAkshara - 1) / matrasPerAkshara

    fun eventColor(kind: String, segIndex: Int, syllable: String): Color = when {
        kind == "landing" -> Color(0xFFFACC15)
        syllable == "—" -> Color(0xFF475569)
        kind == "segment" -> SEG_COLORS[(segIndex + 6) % SEG_COLORS.size]
        else -> Color(0xFF64748B)
    }

    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        for (av in 0 until resolution.config.avartanas) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "A${av + 1}",
                    Modifier.width(26.dp).padding(top = 4.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val aksharasThisAv = minOf(tala.aksharas, aksharaCount - av * tala.aksharas)
                for (ak in 0 until aksharasThisAv) {
                    val aIdx = av * tala.aksharas + ak
                    val isSam = aIdx % tala.aksharas == 0
                    val from = aIdx * matrasPerAkshara
                    val to = from + matrasPerAkshara
                    val events = timeline.events.filter { it.matra >= from && it.matra < to }
                    Column(
                        Modifier
                            .width(((aksharasThisAv.coerceAtMost(8)) * 14).coerceAtLeast(48).dp / 1)
                            .background(
                                if (isSam) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(7.dp),
                            )
                            .border(
                                1.dp,
                                if (isSam) Color(0xFFD4A017) else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(7.dp),
                            )
                            .padding(4.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${aIdx + 1}",
                                fontSize = 10.sp,
                                color = if (isSam) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSam) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                CLAP_ICON[claps[ak].mark] ?: "·",
                                fontSize = 10.sp,
                            )
                        }
                        events.forEach { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .background(Color(0xFF191512), RoundedCornerShape(3.dp))
                                    .padding(start = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .background(eventColor(e.kind, e.segIndex, e.syllable))
                                        .padding(vertical = 0.dp)
                                ) {}
                                Text(
                                    " ${e.syllable}" + if (e.weight == Weight.H) " ●" else "",
                                    fontSize = 11.sp,
                                    color = if (e.syllable == "—") Color(0xFF5B6B7C) else Color.Unspecified,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
