package com.korvai.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korvai.app.KorvaiViewModel
import com.korvai.engine.Nadai
import com.korvai.engine.Resolution
import com.korvai.engine.Tala
import com.korvai.engine.TalaEngine

/* ---------- sollukattu ---------- */

@Composable
fun SollukattuPanel(resolution: Resolution) {
    val lines = buildList {
        if (resolution.pad > 0) add("(kaarvai ${resolution.pad})  " + List(resolution.pad) { "—" }.joinToString(" "))
        resolution.segments.forEach { seg ->
            add("[${seg.label} · ${seg.matras}]  " + seg.cells.joinToString("   ") { it.syllables.joinToString(" ") })
        }
        resolution.landingCell?.let { add("[landing · ${resolution.landing}]  " + it.syllables.joinToString(" ")) }
    }
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${resolution.config.talaName} · ${resolution.config.nadaiName} nadai · ${resolution.config.kalai} kalai · " +
                    "${resolution.config.avartanas} avartana(s) · ${resolution.totalMatras} matras · ${resolution.config.templateName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

/* ---------- nattuvangam ---------- */

@Composable
fun NattuvangamPanel(resolution: Resolution, tala: Tala, nadai: Nadai) {
    val tl = TalaEngine.buildTimeline(resolution)
    val matrasPerAkshara = nadai.subdivision * resolution.config.kalai
    val claps = TalaEngine.clapPattern(tala)
    val CLAP_NAME = mapOf("clap" to "👏 clap", "wave" to "👋 wave", "count" to "• count", "rest" to "·")
    val angaName = mapOf(
        "LAGHU" to "Laghu", "DHRUTAM" to "Dhrutam", "ANUDHRUTAM" to "Anudhrutam", "SECTION" to "Chapu",
    )
    val aksharaCount = (tl.totalMatras + matrasPerAkshara - 1) / matrasPerAkshara
    Card {
        LazyColumn(Modifier.padding(12.dp).fillMaxWidth()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Akshara", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Anga · Kriya", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            items((0 until aksharaCount).toList()) { a ->
                val from = a * matrasPerAkshara
                val evs = tl.events.filter { it.matra >= from && it.matra < from + matrasPerAkshara }
                val anga = tala.angas[claps[a % tala.aksharas].angaIndex]
                val isSam = a % tala.aksharas == 0
                Column(Modifier.padding(vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${a + 1}${if (isSam) " ◉sam" else ""}",
                            fontSize = 12.sp,
                            color = if (isSam) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${angaName[anga.type.name]} · ${CLAP_NAME[claps[a % tala.aksharas].mark]}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        evs.joinToString(" ") { it.syllable },
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    HorizontalDivider(Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/* ---------- dance counts + adavus ---------- */

@Composable
fun DancePanel(vm: KorvaiViewModel, resolution: Resolution, tala: Tala, nadai: Nadai) {
    val dc = TalaEngine.danceCounts(resolution, tala, nadai)
    val adavus = TalaEngine.suggestAdavus(resolution, vm.library, resolution.config.nadaiId)
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                "Dance counts (groups of 8; ${resolution.config.kalai}-kalai: each count spans " +
                    "${resolution.config.kalai} akshara(s))",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(dc.blocks) { b ->
            Card {
                Row(Modifier.padding(10.dp)) {
                    Text(
                        "${b.count}${if (b.isSam) " ◉" else ""}",
                        fontSize = 16.sp,
                        color = Color(0xFFFACC15),
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Column {
                        Text("akshara ${b.aksharaFrom + 1}–${b.aksharaTo}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(b.sollukattu, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        item { Text("Adavu suggestions", fontSize = 13.sp, color = Color(0xFFD4A017), modifier = Modifier.padding(top = 8.dp)) }
        items(adavus) { a ->
            Card {
                Column(Modifier.padding(10.dp)) {
                    Text(a.name, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(a.sollukattu, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFACC15))
                    Text(a.description, fontSize = 12.sp)
                    Text(
                        "${a.counts} counts · difficulty ${a.difficulty}/5 · ${a.nadais.joinToString("/")} nadai",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
