/* =====================================================================
 * Korvai Composer — UI (vanilla JS, no frameworks)
 * ===================================================================== */
(function () {
  'use strict';
  const E = window.KorvaiEngine;
  const A = window.KorvaiAI;
  const AU = window.KorvaiAudio;
  const SEED = window.KORVAI_SEED;

  const $ = (id) => document.getElementById(id);
  const el = (tag, cls, html) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  };
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ---------- safe storage (sandboxed preview may block localStorage) ---------- */
  const mem = {};
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v == null ? (k in mem ? mem[k] : d) : JSON.parse(v); } catch (e) { return k in mem ? mem[k] : d; } },
    set(k, v) { mem[k] = v; try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* in-memory only */ } },
  };

  /* ---------- state ---------- */
  const state = {
    library: SEED,
    talaId: 'adi', jatiId: 'chaturasra', nadaiId: 'chaturasra', kalai: 1,
    eduppuId: 'samam', avartanas: 2, templateId: 'korvai_crescendo',
    maxDifficulty: 5, targetDifficulty: 3, seed: Math.floor(Math.random() * 9999) + 1,
    resolution: null,
    history: store.get('korvai.history', []),
    aiCfg: store.get('korvai.ai', { api: 'llamacpp', baseUrl: 'http://127.0.0.1:8080', model: 'konakolswara-llm', temperature: 0.7, maxRetries: 2 }),
    bpm: 60, metronome: true, loops: 1,
    lastVariations: [],
  };

  const byId = (arr, id) => arr.find((x) => x.id === id);

  function currentTala() {
    return E.applyJati(byId(state.library.talas, state.talaId), state.jatiId, state.library.jatis);
  }
  function currentNadai() { return byId(state.library.nadais, state.nadaiId); }
  function currentTemplate() { return byId(state.library.templates, state.templateId); }
  function currentEduppu() { return byId(state.library.eduppus, state.eduppuId); }

  /* ---------- config panel ---------- */
  function buildSelectors() {
    const talaSel = $('tala');
    state.library.talas.forEach((t) => {
      const o = el('option', null, `${esc(t.name)} · ${t.aksharas} aksharas`);
      o.value = t.id; talaSel.appendChild(o);
    });
    talaSel.value = state.talaId;
    talaSel.onchange = () => { state.talaId = talaSel.value; syncJatiOptions(); updateMathPreview(); };

    const jatiSel = $('jati');
    const syncJatiOptions = () => {
      const tala = byId(state.library.talas, state.talaId);
      const hasLaghu = tala.angas.some((a) => a.type === 'laghu');
      jatiSel.innerHTML = '';
      state.library.jatis.forEach((j) => {
        const o = el('option', null, esc(j.name + (hasLaghu ? ` (laghu ${j.laghu})` : ' (n/a — chapu)')));
        o.value = j.id; jatiSel.appendChild(o);
      });
      jatiSel.value = state.jatiId;
      jatiSel.disabled = !hasLaghu;
      if (!hasLaghu) jatiSel.value = byId(state.library.talas, state.talaId).jati;
    };
    syncJatiOptions();
    jatiSel.onchange = () => { state.jatiId = jatiSel.value; updateMathPreview(); };

    const nadaiSel = $('nadai');
    state.library.nadais.forEach((n) => {
      const o = el('option', null, `${esc(n.name)} (${n.subdivision})`);
      o.value = n.id; nadaiSel.appendChild(o);
    });
    nadaiSel.value = state.nadaiId;
    nadaiSel.onchange = () => { state.nadaiId = nadaiSel.value; updateMathPreview(); };

    const kalaiSel = $('kalai');
    [1, 2, 4].forEach((k) => { const o = el('option', null, k + ' kalai' + (k > 1 ? ' (slow)' : '')); o.value = k; kalaiSel.appendChild(o); });
    kalaiSel.value = state.kalai;
    kalaiSel.onchange = () => { state.kalai = parseInt(kalaiSel.value, 10); syncCycles(); updateMathPreview(); };

    const eduppuSel = $('eduppu');
    state.library.eduppus.forEach((x) => { const o = el('option', null, esc(x.name)); o.value = x.id; eduppuSel.appendChild(o); });
    eduppuSel.value = state.eduppuId;
    eduppuSel.onchange = () => { state.eduppuId = eduppuSel.value; updateMathPreview(); };

    const tplSel = $('template');
    state.library.templates.forEach((t) => { const o = el('option', null, esc(t.name)); o.value = t.id; tplSel.appendChild(o); });
    tplSel.value = state.templateId;
    tplSel.onchange = () => { state.templateId = tplSel.value; syncCycles(); renderTemplateInfo(); updateMathPreview(); };

    const diffSel = $('difficulty');
    for (let d = 1; d <= 5; d++) { const o = el('option', null, '★'.repeat(d) + '☆'.repeat(5 - d)); o.value = d; diffSel.appendChild(o); }
    diffSel.value = state.targetDifficulty;
    diffSel.onchange = () => { state.targetDifficulty = parseInt(diffSel.value, 10); };

    const cyclesSel = $('cycles');
    const syncCycles = () => {
      const tpl = currentTemplate();
      const auto = !!tpl.autoAvartanas;
      cyclesSel.innerHTML = '';
      if (auto) {
        const o = el('option', null, 'auto'); o.value = 'auto'; cyclesSel.appendChild(o);
        cyclesSel.value = 'auto'; state.avartanas = 'auto';
      } else {
        for (let c = 1; c <= 8; c++) { const o = el('option', null, c + (c === 1 ? ' cycle' : ' cycles')); o.value = c; cyclesSel.appendChild(o); }
        cyclesSel.value = String(state.avartanas === 'auto' ? 2 : state.avartanas);
        state.avartanas = parseInt(cyclesSel.value, 10);
      }
      cyclesSel.disabled = auto;
    };
    syncCycles();
    cyclesSel.onchange = () => { state.avartanas = cyclesSel.value === 'auto' ? 'auto' : parseInt(cyclesSel.value, 10); updateMathPreview(); };

    $('seed').value = state.seed;
    $('seed').oninput = () => { state.seed = parseInt($('seed').value, 10) || 1; };
    $('dice').onclick = () => { state.seed = Math.floor(Math.random() * 9999) + 1; $('seed').value = state.seed; };

    $('generate').onclick = () => generate(false);
    $('generateAi').onclick = () => generate(true);
    $('variations10').onclick = () => generateVariations(10);

    $('bpm').oninput = () => { state.bpm = parseInt($('bpm').value, 10); $('bpmVal').textContent = state.bpm; };
    $('bpm').value = state.bpm; $('bpmVal').textContent = state.bpm;
    $('metronome').onchange = () => { state.metronome = $('metronome').checked; };
    $('loops').onchange = () => { state.loops = parseInt($('loops').value, 10); };
    $('play').onclick = playCurrent;
    $('stop').onclick = () => AU.stop();

    renderTemplateInfo();
  }

  function renderTemplateInfo() {
    const t = currentTemplate();
    $('templateInfo').innerHTML = `<strong>${esc(t.name)}</strong> — ${esc(t.structure)}<br><span class="muted">${esc(t.description)}</span>`;
  }

  function updateMathPreview() {
    const tala = currentTala(), nadai = currentNadai();
    const av = state.avartanas === 'auto' ? '(auto)' : state.avartanas;
    const m = E.totalMatras(tala, nadai, state.kalai, 1);
    $('mathPreview').innerHTML =
      `<b>${esc(tala.name)}</b> ${tala.aksharas} aksharas × <b>${esc(nadai.name)}</b> ${nadai.subdivision} × ${state.kalai} kalai = ` +
      `<b>${m} matras</b> / avartana${av === '(auto)' ? '' : ` → total <b>${m * state.avartanas}</b> for ${av} cycle(s)`}`;
  }

  /* ---------- generate ---------- */
  function solveConfig(overrides) {
    return {
      tala: currentTala(), nadai: currentNadai(), kalai: state.kalai,
      eduppuAksharas: currentEduppu().aksharas,
      avartanas: state.avartanas, template: currentTemplate(), library: state.library,
      seed: state.seed, maxDifficulty: state.maxDifficulty, targetDifficulty: state.targetDifficulty,
      ...overrides,
    };
  }

  function generate(useAi, opts) {
    opts = opts || {};
    const cfg = solveConfig(opts.cfg);
    const out = E.solve(cfg);
    if (!out.ok) { showError(out.error); return null; }
    const res = out.resolution;
    const v = E.validateResolution(res, { tala: cfg.tala, nadai: cfg.nadai });
    if (!v.ok) { showError('Internal validation failed (should never happen): ' + v.errors.join('; ')); return null; }
    state.resolution = res;
    pushHistory(res);
    renderAll();
    if (typeof $('summary').scrollIntoView === 'function') {
      $('summary').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    return res;
  }

  function generateVariations(n) {
    const cfg0 = solveConfig();
    const out0 = E.solve(cfg0);
    if (!out0.ok) { showError(out0.error); return; }
    const results = [];
    const seen = new Set([E.resolutionSollukattu(out0.resolution)]);
    for (let i = 0; i < n * 3 && results.length < n; i++) {
      const seed = state.seed + 1 + i;
      const out = E.solve({ ...cfg0, seed });
      if (!out.ok) continue;
      const key = E.resolutionSollukattu(out.resolution);
      if (seen.has(key)) continue;
      seen.add(key);
      const v = E.validateResolution(out.resolution, { tala: cfg0.tala, nadai: cfg0.nadai });
      if (!v.ok) continue;
      results.push(out.resolution);
    }
    state.lastVariations = results;
    state.resolution = out0.resolution;
    pushHistory(out0.resolution);
    renderAll();
    renderVariations(results);
  }

  /* ---------- AI integration ---------- */
  // Real async AI generate path
  async function generateWithAi() {
    const base = generate(false);
    if (!base) return;
    aiLog('info', '— AI pass —');
    const tpl = currentTemplate();
    const bySlot = {};
    base.segments.forEach((s) => {
      if (!bySlot[s.slotId]) bySlot[s.slotId] = { id: s.slotId, label: s.label, matras: s.matras, constraints: tpl.slots.find((x) => x.id === s.slotId) || {} };
    });
    const req = {
      slots: Object.values(bySlot).map((s) => ({ id: s.id, label: s.label, matras: s.matras, constraints: s.constraints })),
      nadaiId: state.nadaiId, nadaiName: currentNadai().name, talaName: currentTala().name,
      kalai: state.kalai, targetDifficulty: state.targetDifficulty, maxDifficulty: state.maxDifficulty,
      library: state.library,
    };
    const det = new A.DeterministicSelector(E, state.seed);
    const llm = new A.LlmSelector(E, { ...state.aiCfg, onLog: (lvl, msg) => aiLog(lvl, msg) }, det);
    const t0 = performance.now();
    const out = await llm.proposeCells(req);
    const dt = Math.round(performance.now() - t0);
    if (!out.ok) { aiLog('warn', 'AI failed entirely (' + out.error + ') — keeping engine cells'); return; }
    if (out.source !== 'ai') { aiLog('warn', `AI unusable after ${dt}ms (${out.aiError || 'validator rejected'}) — keeping engine cells`); return; }
    // re-validate through the ENGINE validator with a reconstructed resolution
    const segs = base.segments.map((s) => {
      const proposed = out.assignments[s.slotId];
      if (proposed) {
        const sum = proposed.reduce((a, c) => a + c.matraCount, 0);
        if (sum === s.matras) return { ...s, cells: proposed };
      }
      return s;
    });
    const candidate = { ...base, segments: segs, source: 'ai+engine' };
    const v = E.validateResolution(candidate, { tala: currentTala(), nadai: currentNadai() });
    if (!v.ok) { aiLog('warn', 'engine validator rejected AI assignment: ' + v.errors.join('; ')); return; }
    aiLog('ok', `AI cells accepted (${dt}ms, attempt ${out.attempts}) — all counts verified`);
    state.resolution = candidate;
    pushHistory(candidate);
    renderAll();
  }
  // rewire the AI button to the async path
  function bindAiButton() {
    $('generateAi').onclick = () => generateWithAi();
  }

  /* ---------- history ---------- */
  function pushHistory(res) {
    state.history.unshift({ res, savedAt: new Date().toISOString() });
    state.history = state.history.slice(0, 40);
    store.set('korvai.history', state.history);
    renderHistory();
  }
  function renderHistory() {
    const box = $('historyList');
    box.innerHTML = '';
    if (!state.history.length) { box.appendChild(el('div', 'muted', 'No saved korvais yet.')); return; }
    state.history.forEach((h, i) => {
      const c = h.res.config;
      const row = el('div', 'hist-row',
        `<div><b>${esc(c.templateName)}</b> · ${esc(c.talaName)} · ${esc(c.nadaiName)} · ${c.kalai}k · ${c.avartanas}c · ${h.res.totalMatras}m
         <span class="badge ${h.res.source && h.res.source.startsWith('ai') ? 'badge-ai' : ''}">${esc(h.res.source || 'engine')}</span></div>
         <div class="muted small">${esc(h.savedAt.replace('T', ' ').slice(0, 19))}</div>`);
      row.onclick = () => {
        state.resolution = h.res;
        state.talaId = h.res.config.talaId; state.nadaiId = h.res.config.nadaiId; state.kalai = h.res.config.kalai;
        state.templateId = h.res.config.templateId;
        renderAll(); syncSelectorsFromState();
      };
      box.appendChild(row);
    });
  }
  function syncSelectorsFromState() {
    $('tala').value = state.talaId; $('nadai').value = state.nadaiId; $('template').value = state.templateId; $('kalai').value = state.kalai;
    updateMathPreview(); renderTemplateInfo();
  }

  /* ---------- rendering ---------- */
  function showError(msg) {
    $('error').textContent = msg;
    $('error').style.display = 'block';
    setTimeout(() => { $('error').style.display = 'none'; }, 6000);
  }

  function renderAll() {
    const res = state.resolution;
    if (!res) return;
    const tala = E.applyJati(byId(state.library.talas, res.config.talaId), res.config.jati, state.library.jatis);
    const nadai = byId(state.library.nadais, res.config.nadaiId);
    const v = E.validateResolution(res, { tala, nadai });
    $('summary').innerHTML = `
      <div class="sum-main">
        <span class="chip">${esc(res.config.talaName)}</span>
        <span class="chip">${esc(res.config.nadaiName)} nadai</span>
        <span class="chip">${res.config.kalai} kalai</span>
        <span class="chip">${res.config.avartanas} avartana${res.config.avartanas > 1 ? 's' : ''}</span>
        <span class="chip">${esc(res.config.templateName)}</span>
        <span class="chip strong">${res.totalMatras} matras</span>
        ${res.pad ? `<span class="chip warn">${res.pad} matra front kaarvai</span>` : ''}
        <span class="badge ${v.ok ? 'badge-ok' : 'badge-bad'}">${v.ok ? '✓ validated exact' : '✗ ' + esc(v.errors.join('; '))}</span>
        ${res.source && res.source !== 'engine' ? `<span class="badge badge-ai">${esc(res.source)}</span>` : ''}
      </div>`;
    renderGrid(res, tala, nadai);
    renderSolkattu(res);
    renderNattuvangam(res, tala, nadai);
    renderDance(res, tala, nadai);
    renderRemixRow(res);
    $('aiRawBox').style.display = 'none';
  }

  /* ----- tala grid ----- */
  const SEG_COLORS = ['#3b82f6', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316', '#10b981'];
  function renderGrid(res, tala, nadai) {
    const wrap = $('grid');
    wrap.innerHTML = '';
    const tl = E.buildTimeline(res, tala, nadai);
    const matrasPerAkshara = nadai.subdivision * res.config.kalai;
    const claps = E.clapPattern(tala);
    const CLAP_ICON = { clap: '👏', wave: '👋', count: '•', rest: '·' };
    const totalMatras = tl.totalMatras;
    const aksharaCount = Math.ceil(totalMatras / matrasPerAkshara);

    // segment color by segIndex
    const evColor = (e) => {
      if (e.kind === 'landing') return '#facc15';
      if (e.kind === 'pad' || e.syllable === '—') return '#475569';
      if (e.kind === 'segment') return SEG_COLORS[(e.segIndex + 6) % SEG_COLORS.length];
      return '#64748b';
    };

    for (let av = 0; av < res.config.avartanas; av++) {
      const row = el('div', 'avartana');
      row.appendChild(el('div', 'av-label', `A${av + 1}`));
      const aksharasThisAv = Math.min(tala.aksharas, aksharaCount - av * tala.aksharas);
      for (let ak = 0; ak < aksharasThisAv; ak++) {
        const aIdx = av * tala.aksharas + ak;
        const isSam = aIdx % tala.aksharas === 0;
        const col = el('div', 'akshara' + (isSam ? ' sam' : ''));
        col.appendChild(el('div', 'ak-top', `<span class="ak-num">${aIdx + 1}</span><span class="ak-clap">${CLAP_ICON[claps[ak].mark]}</span>`));
        const cellsBox = el('div', 'ak-cells');
        const from = aIdx * matrasPerAkshara;
        const to = from + matrasPerAkshara;
        tl.events.filter((e) => e.matra >= from && e.matra < to).forEach((e) => {
          const c = el('div', 'm-cell',
            `<span class="syl">${esc(e.syllable)}</span><span class="w">${e.weight === 'H' ? '●' : '·'}</span>`);
          c.style.borderLeftColor = evColor(e);
          c.dataset.matra = e.matra;
          if (e.weight === 'H') c.classList.add('heavy');
          if (e.syllable === '—') c.classList.add('kaarvai');
          cellsBox.appendChild(c);
        });
        col.appendChild(cellsBox);
        row.appendChild(col);
      }
      wrap.appendChild(row);
    }
  }

  /* ----- sollukattu text ----- */
  function renderSolkattu(res) {
    const pre = $('solkattuText');
    const lines = [];
    const tala = E.applyJati(byId(state.library.talas, res.config.talaId), res.config.jati, state.library.jatis);
    const nadai = byId(state.library.nadais, res.config.nadaiId);
    if (res.pad > 0) lines.push('(kaarvai ' + res.pad + ')  — '.repeat(Math.min(res.pad, 16)).trim());
    let segIdx = 0;
    res.segments.forEach((seg, i) => {
      const isLast = i === res.segments.length - 1;
      lines.push(`[${esc(seg.label)} · ${seg.matras}]  ` + seg.cells.map((c) => c.syllables.join(' ')).join('   '));
      if (!isLast && (i + 1) % 3 === 0 && res.segments.length > 3) lines.push('');
    });
    if (res.landingCell) lines.push(`[landing · ${res.landing}]  ` + res.landingCell.syllables.join(' '));
    pre.innerHTML = esc(lines.join('\n'));
    const cfg = res.config;
    $('solkattuMeta').innerHTML =
      `${esc(cfg.talaName)} · ${esc(cfg.nadaiName)} nadai · ${cfg.kalai} kalai · ${cfg.avartanas} avartana(s) · ${res.totalMatras} matras · ` +
      `${esc(cfg.templateName)} · seed ${cfg.seed}`;
    $('copySolkattu').onclick = () => {
      const txt = $('solkattuText').textContent;
      try { navigator.clipboard.writeText(txt); $('copySolkattu').textContent = 'Copied!'; }
      catch (e) { $('copySolkattu').textContent = 'Select & copy manually'; }
      setTimeout(() => { $('copySolkattu').textContent = 'Copy'; }, 1600);
    };
    $('dlSolkattu').onclick = () => {
      const blob = new Blob([$('solkattuText').textContent + '\n\n' + $('solkattuMeta').textContent], { type: 'text/plain' });
      dl(blob, `korvai-${res.config.templateId}-${res.id}.txt`);
    };
    $('dlJson').onclick = () => {
      const blob = new Blob([JSON.stringify(res, null, 2)], { type: 'application/json' });
      dl(blob, `korvai-${res.config.templateId}-${res.id}.json`);
    };
  }
  function dl(blob, name) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }

  /* ----- nattuvangam view ----- */
  function renderNattuvangam(res, tala, nadai) {
    const tl = E.buildTimeline(res, tala, nadai);
    const matrasPerAkshara = nadai.subdivision * res.config.kalai;
    const claps = E.clapPattern(tala);
    const CLAP_NAME = { clap: '👏 clap', wave: '👋 wave', count: '• count', rest: '·' };
    let html = '<table class="nat-table"><tr><th>Akshara</th><th>Anga</th><th>Kriya</th><th>Sollukattu</th></tr>';
    for (let a = 0; a < Math.ceil(tl.totalMatras / matrasPerAkshara); a++) {
      const from = a * matrasPerAkshara;
      const evs = tl.events.filter((e) => e.matra >= from && e.matra < from + matrasPerAkshara);
      const anga = tala.angas[claps[a % tala.aksharas].angaIndex];
      const angaName = { laghu: 'Laghu', dhrutam: 'Dhrutam', anudhrutam: 'Anudhrutam', section: 'Chapu' }[anga.type];
      const isSam = a % tala.aksharas === 0;
      html += `<tr class="${isSam ? 'sam-row' : ''}"><td>${a + 1}${isSam ? ' ◉sam' : ''}</td><td>${angaName}</td><td>${CLAP_NAME[claps[a % tala.aksharas].mark]}</td><td class="mono">${esc(evs.map((e) => e.syllable).join(' '))}</td></tr>`;
    }
    html += '</table>';
    html += `<div class="muted small" style="margin-top:8px">Anga layout: ${tala.angas.map((x) => `${x.type === 'laghu' ? 'Laghu' : x.type === 'dhrutam' ? 'Dhrutam' : x.type === 'anudhrutam' ? 'Anudhrutam' : 'Chapu'} ${x.aksharas}`).join(' + ')} = ${tala.aksharas} aksharas/avartana.</div>`;
    $('nattuvangam').innerHTML = html;
  }

  /* ----- dance counts + adavus ----- */
  function renderDance(res, tala, nadai) {
    const dc = E.danceCounts(res, tala, nadai);
    let html = '<div class="dance-counts">';
    dc.blocks.forEach((b) => {
      html += `<div class="dcount ${b.isSam ? 'sam' : ''}"><div class="dnum">${b.count}${b.isSam ? ' ◉' : ''}</div><div class="dsyl">${esc(b.sollukattu)}</div></div>`;
    });
    html += '</div>';
    const adavus = E.suggestAdavus(res, state.library, res.config.nadaiId);
    html += '<h3 class="sub">Adavu suggestions</h3><div class="adavus">';
    adavus.forEach((a) => {
      html += `<div class="adavu"><div class="ad-name">${esc(a.name)}</div>
        <div class="ad-sol mono">${esc(a.sollukattu)}</div>
        <div class="ad-desc">${esc(a.description)}</div>
        <div class="muted small">${a.counts} counts · difficulty ${a.difficulty}/5</div></div>`;
    });
    html += '</div>';
    html += `<div class="muted small" style="margin-top:8px">Heuristic mapping: phrase character/density → compatible adavus in ${esc(res.config.nadaiName)} nadai. Counts grouped in 8s; ${res.config.kalai > 1 ? `${res.config.kalai}-kalai: each count spans ${res.config.kalai} aksharas.` : 'each count = 1 akshara.'}</div>`;
    $('dance').innerHTML = html;
  }

  /* ----- remix row ----- */
  function renderRemixRow(res) {
    const row = $('remixRow');
    row.innerHTML = '';
    const tala = E.applyJati(byId(state.library.talas, res.config.talaId), res.config.jati, state.library.jatis);
    const nadai = byId(state.library.nadais, res.config.nadaiId);
    Object.entries(E.remixOps).forEach(([key, op]) => {
      if (key === 'reseed') return;
      const b = el('button', 'btn small', esc(op.label));
      b.title = op.desc;
      b.onclick = () => {
        const remixed = op.apply(res, { seed: state.seed, library: state.library });
        const v = E.validateResolution(remixed, { tala, nadai });
        if (!v.ok) { showError('Remix rejected by validator: ' + v.errors.join('; ')); return; }
        state.resolution = remixed;
        pushHistory(remixed);
        renderAll();
      };
      row.appendChild(b);
    });
    // nadai switch
    state.library.nadais.filter((n) => n.id !== res.config.nadaiId).forEach((n) => {
      const b = el('button', 'btn small ghost', `→ ${esc(n.name)} nadai`);
      b.onclick = () => {
        const out = E.resolveWithConfig(res, {
          tala: E.applyJati(byId(state.library.talas, res.config.talaId), res.config.jati, state.library.jatis),
          nadai: n, kalai: res.config.kalai, eduppuAksharas: res.config.eduppuAksharas,
          avartanas: res.config.avartanas, template: byId(state.library.templates, res.config.templateId),
          library: state.library, seed: state.seed, maxDifficulty: res.config.maxDifficulty,
          targetDifficulty: res.config.targetDifficulty,
        }, state.library);
        if (!out.ok) { showError('Cannot re-fit this structure in ' + n.name + ' nadai here.'); return; }
        state.resolution = out.resolution;
        state.nadaiId = n.id;
        pushHistory(out.resolution);
        renderAll(); syncSelectorsFromState();
      };
      row.appendChild(b);
    });
    const newVar = el('button', 'btn small accent', '↻ New variation');
    newVar.onclick = () => { state.seed = Math.floor(Math.random() * 9999) + 1; $('seed').value = state.seed; generate(false); };
    row.appendChild(newVar);
  }

  /* ----- variations list ----- */
  function renderVariations(list) {
    const box = $('variations');
    box.innerHTML = '';
    box.appendChild(el('div', 'muted small', `${list.length} validated variations of the same structure — click to load:`));
    list.forEach((res) => {
      const line = E.resolutionSollukattu(res);
      const row = el('div', 'var-row mono', esc(line.length > 130 ? line.slice(0, 130) + '…' : line));
      row.onclick = () => { state.resolution = res; pushHistory(res); renderAll(); };
      box.appendChild(row);
    });
    box.style.display = 'block';
  }

  /* ----- AI panel ----- */
  function aiLog(level, msg) {
    const box = $('aiLog');
    const icon = { ok: '✓', warn: '⚠', info: '·' }[level] || '·';
    const line = el('div', 'ai-line ai-' + level, `<span>${icon}</span> ${esc(msg)}`);
    box.appendChild(line);
    box.scrollTop = box.scrollHeight;
  }
  function bindAiPanel() {
    const c = state.aiCfg;
    $('aiApi').value = c.api; $('aiUrl').value = c.baseUrl; $('aiModel').value = c.model;
    $('aiTemp').value = c.temperature; $('aiTempVal').textContent = c.temperature;
    const save = () => {
      state.aiCfg = {
        api: $('aiApi').value, baseUrl: $('aiUrl').value.trim(), model: $('aiModel').value.trim(),
        temperature: parseFloat($('aiTemp').value), maxRetries: 2,
      };
      store.set('korvai.ai', state.aiCfg);
    };
    ['aiApi', 'aiUrl', 'aiModel', 'aiTemp'].forEach((id) => { $(id).onchange = save; });
    $('aiTemp').oninput = () => { $('aiTempVal').textContent = $('aiTemp').value; };
    $('aiTest').onclick = async () => {
      save();
      aiLog('info', `testing ${state.aiCfg.api} endpoint at ${state.aiCfg.baseUrl} …`);
      const det = new A.DeterministicSelector(E, 1);
      const llm = new A.LlmSelector(E, state.aiCfg, det);
      const r = await llm.testConnection();
      if (r.ok) aiLog('ok', 'endpoint reachable — sample: ' + r.sample);
      else aiLog('warn', 'endpoint unreachable (' + r.error + '). The app works fully without it; see MODEL_SETUP.md.');
    };
    $('aiPromptPreview').onclick = () => {
      const res = state.resolution;
      if (!res) { aiLog('warn', 'generate a korvai first'); return; }
      const tpl = byId(state.library.templates, res.config.templateId);
      const bySlot = {};
      res.segments.forEach((s) => { if (!bySlot[s.slotId]) bySlot[s.slotId] = { id: s.slotId, label: s.label, matras: s.matras, constraints: tpl.slots.find((x) => x.id === s.slotId) || {} }; });
      const prompt = A.buildPrompt({
        slots: Object.values(bySlot).map((s) => ({ id: s.id, label: s.label, matras: s.matras })),
        nadaiName: res.config.nadaiName, talaName: res.config.talaName, kalai: res.config.kalai,
        targetDifficulty: res.config.targetDifficulty, library: state.library,
      });
      $('aiRaw').textContent = prompt;
      $('aiRawBox').style.display = 'block';
    };
  }

  /* ---------- audio ---------- */
  function playCurrent() {
    const res = state.resolution;
    if (!res) { showError('Generate a korvai first.'); return; }
    const tala = E.applyJati(byId(state.library.talas, res.config.talaId), res.config.jati, state.library.jatis);
    const nadai = byId(state.library.nadais, res.config.nadaiId);
    const tl = E.buildTimeline(res, tala, nadai);
    AU.play(tl, {
      bpm: state.bpm, kalai: res.config.kalai, subdivision: nadai.subdivision,
      metronome: state.metronome, loops: state.loops, aksharasPerAvartana: tala.aksharas,
    });
    // playback highlight
    const onStop = () => { document.querySelectorAll('.m-cell.playing').forEach((x) => x.classList.remove('playing')); };
    AU.scheduler.onStop = onStop;
    const tickLoop = () => {
      if (!AU.playing) { onStop(); return; }
      const m = AU.currentMatra();
      document.querySelectorAll('.m-cell.playing').forEach((x) => x.classList.remove('playing'));
      const cur = document.querySelector(`.m-cell[data-matra="${Math.floor(m)}"]`);
      if (cur) cur.classList.add('playing');
      requestAnimationFrame(tickLoop);
    };
    requestAnimationFrame(tickLoop);
  }

  /* ---------- tabs ---------- */
  function bindTabs() {
    document.querySelectorAll('.tabbar button').forEach((b) => {
      b.onclick = () => {
        document.querySelectorAll('.tabbar button').forEach((x) => x.classList.remove('active'));
        document.querySelectorAll('.tabpane').forEach((x) => x.classList.remove('active'));
        b.classList.add('active');
        $(b.dataset.pane).classList.add('active');
      };
    });
  }

  /* ---------- boot ---------- */
  function boot() {
    buildSelectors();
    bindAiPanel();
    bindTabs();
    bindAiButton();
    renderHistory();
    updateMathPreview();
    aiLog('info', 'Engine ready — deterministic mode (no AI required).');
    aiLog('info', 'AI layer: configure a local KonakolSwaraLLM endpoint in this tab (llama.cpp / LM Studio / Ollama). See MODEL_SETUP.md.');
    // first render
    generate(false);
  }
  document.addEventListener('DOMContentLoaded', boot);
})();
