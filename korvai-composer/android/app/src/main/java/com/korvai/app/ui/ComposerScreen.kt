@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.korvai.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korvai.app.KorvaiViewModel
import com.korvai.app.UiState
import com.korvai.engine.TalaEngine

private val TABS = listOf("Grid", "Sollukattu", "Nattuvangam", "Dance", "AI", "History")
private val Gold = Color(0xFFD4A017)
private val BPM_PRESETS = listOf("30", "40", "45", "50", "60", "66", "72", "80", "90", "100", "120")

/**
 * Compact layout (v1.2): everything above the tabs fits in ~1/3 of the screen.
 * - One horizontally scrollable strip holds all tala/structure dropdowns.
 * - Second strip: template + seed + all actions.
 * - Six content tabs (scrollable) fill the rest; History moved into a tab.
 */
@Composable
fun ComposerScreen(vm: KorvaiViewModel) {
    val state by vm.state.collectAsState()
    val cfg = state.config
    val lib = vm.library

    Column(Modifier.fillMaxSize().padding(8.dp)) {

        /* ---------- header ---------- */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◈ ", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Nandi", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("  korvai composer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.fillMaxWidth(0.12f))
            val v = state.validation
            Text(
                if (v?.ok == true) "✓ exact" else "—",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (v?.ok == true) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        /* ---------- strip 1: tala & structure (horizontal) ---------- */
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactDropdown("Tala", lib.talas.map { "${it.name} · ${it.aksharas}" to it.id }, cfg.talaId, 150.dp) { id ->
                val tala = lib.talas.first { it.id == id }
                vm.updateConfig { it.copy(talaId = id, jatiId = tala.jati) }
            }
            CompactDropdown("Jati", lib.jatis.map { "${it.name} (${it.laghu})" to it.id }, cfg.jatiId, 118.dp) { id ->
                vm.updateConfig { it.copy(jatiId = id) }
            }
            CompactDropdown("Nadai", lib.nadais.map { "${it.name} (${it.subdivision})" to it.id }, cfg.nadaiId, 128.dp) { id ->
                vm.updateConfig { it.copy(nadaiId = id) }
            }
            CompactDropdown("Kalai", listOf("1 kalai" to "1", "2 kalai" to "2", "4 kalai" to "4"), cfg.kalai.toString(), 96.dp) { v ->
                vm.updateConfig { it.copy(kalai = v.toInt()) }
            }
            CompactDropdown("Eduppu", lib.eduppus.map { it.name to it.id }, cfg.eduppuId, 124.dp) { id ->
                vm.updateConfig { it.copy(eduppuId = id) }
            }
            val autoTpl = lib.templates.first { it.id == cfg.templateId }.autoAvartanas
            if (autoTpl) {
                CompactDropdown("Cycles", listOf("auto" to "auto"), "auto", 86.dp) { }
            } else {
                CompactDropdown("Cycles", (1..8).map { "$it ×" to it.toString() }, cfg.avartanas.toString(), 86.dp) { v ->
                    vm.updateConfig { it.copy(avartanas = v.toInt()) }
                }
            }
            CompactDropdown("Level", (1..5).map { "★".repeat(it) to it.toString() }, cfg.targetDifficulty.toString(), 104.dp) { v ->
                vm.updateConfig { it.copy(targetDifficulty = v.toInt()) }
            }
            CompactDropdown("BPM", BPM_PRESETS.map { it to it }, cfg.bpm.toString(), 86.dp) { v ->
                vm.updateConfig { it.copy(bpm = v.toInt()) }
            }
        }

        /* ---------- strip 2: template + seed + actions ---------- */
        val tpl = lib.templates.first { it.id == cfg.templateId }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactDropdown(
                "Template", lib.templates.map { it.name to it.id }, cfg.templateId,
                modifier = Modifier.fillMaxWidth(0.52f),
            ) { id ->
                vm.updateConfig { it.copy(templateId = id) }
            }
            OutlinedTextField(
                value = cfg.seed.toString(),
                onValueChange = { t -> t.toIntOrNull()?.let { s -> vm.updateConfig { it.copy(seed = s) } } },
                label = { Text("Seed", fontSize = 10.sp) },
                modifier = Modifier.width(84.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            )
            OutlinedButton(onClick = { vm.reseed() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("🎲") }
        }
        Text(
            tpl.description,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { vm.generate() }, modifier = Modifier.fillMaxWidth(0.5f)) {
                Text("✦ Compose", maxLines = 1)
            }
            OutlinedButton(onClick = { if (state.playing) vm.stopAudio() else vm.play() }) {
                Text(if (state.playing) "■" else "▶")
            }
            OutlinedButton(onClick = { vm.generateVariations(10) }) { Text("×10") }
            FilterChip(
                selected = cfg.metronome,
                onClick = { vm.updateConfig { it.copy(metronome = !it.metronome) } },
                label = { Text("👏", fontSize = 13.sp) },
            )
        }

        state.error?.let {
            Text(
                "⚠ $it",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        /* ---------- summary chips (horizontal) ---------- */
        val res = state.resolution
        if (res != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(res.config.talaName, fontSize = 11.sp) })
                AssistChip(onClick = {}, label = { Text("${res.config.nadaiName} · ${res.config.kalai}k · ${res.config.avartanas}c", fontSize = 11.sp) })
                AssistChip(onClick = {}, label = { Text("${res.totalMatras} matras", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                if (res.pad > 0) AssistChip(onClick = {}, label = { Text("${res.pad}m kaarvai", fontSize = 11.sp, color = Color(0xFFFBBF24)) })
                AssistChip(onClick = {}, label = { Text(if (res.source.startsWith("ai")) "AI ✓" else "engine", fontSize = 11.sp) })
            }
        }

        /* ---------- content tabs ---------- */
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab,
            edgePadding = 0.dp,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            TABS.forEachIndexed { i, title ->
                Tab(
                    selected = state.selectedTab == i,
                    onClick = { vm.selectTab(i) },
                    text = { Text(title, fontSize = 12.sp, maxLines = 1) },
                )
            }
        }

        Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
            when (state.selectedTab) {
                0 -> GridTab(vm, state)
                1 -> state.resolution?.let { ScrollWrap { SollukattuPanel(it) } }
                2 -> state.resolution?.let { r ->
                    val t = lib.talas.first { it.id == r.config.talaId }.let { TalaEngine.applyJati(it, r.config.jati, lib.jatis) }
                    val n = lib.nadais.first { it.id == r.config.nadaiId }
                    NattuvangamPanel(r, t, n)
                }
                3 -> state.resolution?.let { r ->
                    val t = lib.talas.first { it.id == r.config.talaId }.let { TalaEngine.applyJati(it, r.config.jati, lib.jatis) }
                    val n = lib.nadais.first { it.id == r.config.nadaiId }
                    DancePanel(vm, r, t, n)
                }
                4 -> ScrollWrap { AiPanel(vm, state) }
                else -> HistoryTab(vm, state)
            }
        }
    }
}

@Composable
private fun ScrollWrap(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

/* ---------- compact dropdown (fixed width, menu on tap) ---------- */
@Composable
private fun CompactDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    width: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val mod = if (width != null) modifier.width(width) else modifier
    Column(mod) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = options.firstOrNull { it.second == selected }?.first ?: selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(
                    androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp),
                singleLine = true,
            )
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (name, value) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name, fontSize = 13.sp) },
                        onClick = { onSelect(value); expanded = false },
                    )
                }
            }
        }
    }
}

/* ---------- grid tab: grid + remix chips + variations ---------- */
@Composable
private fun GridTab(vm: KorvaiViewModel, state: UiState) {
    val res = state.resolution ?: return
    val lib = vm.library
    val tala = lib.talas.first { it.id == res.config.talaId }.let { TalaEngine.applyJati(it, res.config.jati, lib.jatis) }
    val nadai = lib.nadais.first { it.id == res.config.nadaiId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // remix row (horizontal, no page growth)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = false, onClick = { vm.remix("reverse") }, label = { Text("Reverse", fontSize = 11.sp) })
            FilterChip(selected = false, onClick = { vm.remix("densify") }, label = { Text("Densify", fontSize = 11.sp) })
            FilterChip(selected = false, onClick = { vm.remix("simplify") }, label = { Text("Simplify", fontSize = 11.sp) })
            FilterChip(selected = false, onClick = { vm.remix("changeEnding") }, label = { Text("Ending", fontSize = 11.sp) })
            FilterChip(selected = false, onClick = { vm.remix("changeSolkattu") }, label = { Text("New solkattu", fontSize = 11.sp) })
            FilterChip(selected = false, onClick = { vm.reseed() }, label = { Text("↻ Variation", fontSize = 11.sp) })
            lib.nadais.filter { it.id != res.config.nadaiId }.forEach { n ->
                FilterChip(selected = false, onClick = { vm.changeNadai(n.id) }, label = { Text("→ ${n.name}", fontSize = 11.sp) })
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Column(Modifier.padding(6.dp)) { TalaGrid(res, tala, nadai) }
        }
        if (state.variations.isNotEmpty()) {
            Text(
                "${state.variations.size} validated variations — tap to load",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            state.variations.forEachIndexed { i, v ->
                TextButton(onClick = { vm.loadVariation(i) }) {
                    Text(TalaEngine.resolutionSollukattu(v).take(70) + "…", fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/* ---------- history tab ---------- */
@Composable
private fun HistoryTab(vm: KorvaiViewModel, state: UiState) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saved korvais (on-device)", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear", fontSize = 12.sp) }
            }
            HorizontalDivider()
        }
        if (state.history.isEmpty()) {
            item {
                Text(
                    "Nothing saved yet — every generated korvai is stored automatically.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.history.size) { idx ->
            val h = state.history[idx]
            Card {
                TextButton(onClick = { vm.loadHistory(h) }) {
                    Column {
                        Text(
                            "${h.templateId} · ${h.talaName} · ${h.nadaiName} · ${h.kalai}k · ${h.avartanas}c",
                            fontSize = 11.5.sp,
                        )
                        Text(
                            "${h.totalMatras} matras · ${h.source}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/* ---------- AI panel ---------- */
@Composable
private fun AiPanel(vm: KorvaiViewModel, state: UiState) {
    val cfg = state.aiConfig
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Rhythmic Selector — optional, off by default", fontWeight = FontWeight.Bold, color = Gold, fontSize = 14.sp)
            Text(
                "The AI never does tala arithmetic. It proposes cells from the curated library; the engine " +
                    "validator re-counts every matra and rejects anything inexact (retry → deterministic fallback). " +
                    "Default backend: KonakolSwaraLLM served locally (llama.cpp / LM Studio / Ollama) — see MODEL_SETUP.md.",
                fontSize = 11.5.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = cfg.enabled, onCheckedChange = { on -> vm.updateAiConfig { it.copy(enabled = on) } })
                Text("Enable AI suggestions", fontSize = 12.sp)
            }
            CompactDropdown(
                "API", listOf("llama.cpp server (/completion)" to "llamacpp", "OpenAI-compatible (/v1/chat/completions)" to "openai"),
                cfg.api, modifier = Modifier.fillMaxWidth(),
            ) { v -> vm.updateAiConfig { it.copy(api = v) } }
            OutlinedTextField(
                value = cfg.baseUrl,
                onValueChange = { v -> vm.updateAiConfig { it.copy(baseUrl = v) } },
                label = { Text("Base URL (e.g. http://127.0.0.1:8080)", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            )
            OutlinedTextField(
                value = cfg.model,
                onValueChange = { v -> vm.updateAiConfig { it.copy(model = v) } },
                label = { Text("Model name", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testAiEndpoint() }) { Text("Test endpoint", fontSize = 12.sp) }
                Button(enabled = !state.aiBusy, onClick = { vm.generateWithAi() }) {
                    Text(if (state.aiBusy) "…" else "✦ Compose with AI", fontSize = 12.sp)
                }
            }
            HorizontalDivider()
            Text("Activity log", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.aiLog.takeLast(20).forEach { (level, msg) ->
                Text(
                    msg,
                    fontSize = 10.5.sp,
                    color = when (level) {
                        "ok" -> Color(0xFF4ADE80)
                        "warn" -> Color(0xFFFBBF24)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
