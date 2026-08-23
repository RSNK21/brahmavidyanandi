/* =====================================================================
 * Korvai Composer — Tala Constraint Engine (pure, deterministic, no AI)
 *
 * Architecture per the build handoff:
 *   Tala math is exact arithmetic. Nothing in this file calls a network,
 *   uses randomness that is not seeded, or trusts any AI output.
 *   Every resolved structure MUST pass `validateResolution()` before it
 *   reaches the UI. AI proposes; this engine disposes.
 * ===================================================================== */
(function (root) {
  'use strict';

  /* ---------- seeded RNG (mulberry32) ---------- */
  function makeRng(seed) {
    let a = (seed >>> 0) || 1;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function shuffled(arr, rng) {
    const a = arr.slice();
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(rng() * (i + 1));
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }

  /* ---------- jati / anga helpers ---------- */
  // The chosen jati resizes every laghu. Chapu sections are fixed.
  function applyJati(tala, jatiId, jatis) {
    const j = jatis.find((x) => x.id === jatiId) || jatis.find((x) => x.id === tala.jati);
    const laghu = j ? j.laghu : 4;
    const angas = tala.angas.map((a) => (a.type === 'laghu' ? { type: 'laghu', aksharas: laghu } : a));
    return { ...tala, jati: j ? j.id : tala.jati, angas, aksharas: angas.reduce((s, a) => s + a.aksharas, 0) };
  }

  function totalMatras(tala, nadai, kalai, avartanas) {
    return tala.aksharas * nadai.subdivision * kalai * avartanas;
  }

  // Landing phrase length. Samam -> the classic 2-unit landing ("tha," held),
  // matching the canonical 13+14+15 ×3 + 2 = 128 worked example.
  // For eduppu e (in aksharas) the phrase must land e aksharas *before* the
  // next sam, so it occupies e * nadai * kalai matras (min 1).
  function computeLandingUnits(eduppuAksharas, nadai, kalai) {
    if (!eduppuAksharas || eduppuAksharas <= 0) return 2;
    return Math.max(1, Math.round(eduppuAksharas * nadai.subdivision * kalai));
  }

  /* ---------- clap / wave pattern (nattuvangam grid) ---------- */
  function clapPattern(tala) {
    // per akshara: {mark: 'clap'|'wave'|'count'|'rest', angaIndex}
    const out = [];
    tala.angas.forEach((anga, ai) => {
      for (let k = 0; k < anga.aksharas; k++) {
        if (anga.type === 'laghu') out.push({ mark: k === 0 ? 'clap' : 'count', angaIndex: ai });
        else if (anga.type === 'dhrutam') out.push({ mark: k === 0 ? 'clap' : 'wave', angaIndex: ai });
        else if (anga.type === 'anudhrutam') out.push({ mark: 'wave', angaIndex: ai });
        else out.push({ mark: k === 0 ? 'clap' : 'rest', angaIndex: ai }); // chapu section
      }
    });
    return out;
  }

  /* ---------- segment fill: subset-sum over the cell library ---------- */
  // Filters the library by the slot's constraints, then randomised backtracking
  // search for cells whose matra counts sum to exactly `length`.
  function eligibleCells(library, slot, nadaiId, maxDifficulty) {
    return library.cells.filter((c) => {
      if (c.matraCount < 1) return false;
      if (!c.usableNadais.includes(nadaiId)) return false;
      if (c.difficulty > maxDifficulty) return false;
      if (slot.allowedFunctions && slot.allowedFunctions.length) {
        if (!slot.allowedFunctions.includes(c.function)) return false;
      }
      if (c.function === 'GAP' && !slot.allowGaps) return false;
      if (c.function === 'LANDING' || c.function === 'MACRO') return false; // handled separately
      return true;
    });
  }

  function scoreCell(cell, ctx) {
    // preference: difficulty close to target, variety bonus, kaarvai slight penalty
    let s = 1;
    s *= 1 / (1 + Math.abs(cell.difficulty - ctx.targetDifficulty));
    if (cell.kaarvai) s *= ctx.gapAffinity;
    if (ctx.lastCellId === cell.id) s *= 0.25;
    if (ctx.charactersSeen && ctx.charactersSeen.has(cell.character)) s *= 0.8;
    return s;
  }

  function weightedOrder(cells, ctx, rng) {
    const arr = cells.map((c) => ({ c, w: scoreCell(c, ctx) }));
    arr.sort((x, y) => y.w * (0.5 + rng()) - x.w * (0.5 + rng()));
    return arr.map((x) => x.c);
  }

  function fillSegment(length, slot, library, nadaiId, maxDifficulty, targetDifficulty, rng, opts) {
    opts = opts || {};
    const pool = eligibleCells(library, slot, nadaiId, maxDifficulty);
    if (!pool.length) return null;
    const piecesLimit = opts.maxPieces || 12;
    const result = [];

    function bt(remaining, depth) {
      if (remaining === 0) return true;
      if (depth >= piecesLimit) return false;
      const ctx = {
        targetDifficulty,
        gapAffinity: opts.gapAffinity != null ? opts.gapAffinity : 0.55,
        lastCellId: result.length ? result[result.length - 1].id : null,
        charactersSeen: new Set(result.map((c) => c.character)),
      };
      // prefer larger cells first for long stretches, weighted-randomised
      let candidates = pool.filter((c) => c.matraCount <= remaining);
      if (depth === 0 && opts.firstCellMustDifferFrom) {
        candidates = candidates.filter((c) => c.id !== opts.firstCellMustDifferFrom);
        if (!candidates.length) candidates = pool.filter((c) => c.matraCount <= remaining);
      }
      candidates = weightedOrder(candidates, ctx, rng);
      for (const c of candidates) {
        result.push(c);
        if (bt(remaining - c.matraCount, depth + 1)) return true;
        result.pop();
      }
      return false;
    }
    if (bt(length, 0)) return result;
    return null;
  }

  /* ---------- kuraippu lengths (diminishing series) ---------- */
  function kuraippuLengths(body) {
    const segs = [];
    let rem = body;
    let L = Math.pow(2, Math.floor(Math.log2(Math.max(2, body))));
    if (L > body) L = L / 2;
    while (rem > 0) {
      const take = Math.min(L, rem);
      segs.push(take);
      rem -= take;
      L = Math.max(2, Math.floor(take / 2));
      if (L % 2 === 1 && L > 2) L = L - 1;
    }
    return segs;
  }

  /* ---------- landing cell pick ---------- */
  function pickLanding(library, units, nadaiId, rng) {
    const cands = library.cells.filter(
      (c) => c.function === 'LANDING' && c.matraCount === units && c.usableNadais.includes(nadaiId)
    );
    if (!cands.length) return null;
    return cands[Math.floor(rng() * cands.length)];
  }

  /* ---------- main solver ---------- */
  // config: {tala, nadai, kalai, eduppuAksharas, avartanas|'auto', template,
  //          library, maxDifficulty, targetDifficulty, seed}
  // Returns {ok, resolution} — resolution.always validated by validateResolution.
  function solve(config) {
    const { tala, nadai, kalai, template, library } = config;
    const rng = makeRng(config.seed || 1);
    const maxDifficulty = config.maxDifficulty || 5;
    const targetDifficulty = config.targetDifficulty || 3;
    const avartanaMatras = tala.aksharas * nadai.subdivision * kalai;

    // ---- kuraippu ----
    if (template.kind === 'kuraippu') {
      for (let cycles = 1; cycles <= 8; cycles++) {
        const target = avartanaMatras * cycles;
        const landing = computeLandingUnits(config.eduppuAksharas, nadai, kalai);
        const body = target - landing;
        if (body < 4) continue;
        const lengths = kuraippuLengths(body);
        const slot = template.slots[0];
        const segs = [];
        let fail = false;
        for (const len of lengths) {
          const cells = fillSegment(len, slot, library, nadai.id, maxDifficulty, targetDifficulty, rng);
          if (!cells) { fail = true; break; }
          segs.push({ slotId: slot.id, label: slot.label, cells, matras: len });
        }
        if (fail) continue;
        const landingCell = pickLanding(library, landing, nadai.id, rng);
        if (!landingCell) continue;
        const res = buildResolution({
          config, template, avartanas: cycles, segments: segs, repetitions: 1,
          landingCell, pad: 0, nadai, tala, rng, landingMode: 'eduppu',
        });
        const v = validateResolution(res, { tala, nadai });
        if (v.ok) return { ok: true, resolution: res };
      }
      return { ok: false, error: 'No valid kuraippu fits this tala/nadai/kalai at the chosen difficulty.' };
    }

    // ---- faran (cross-rhythm macro cells) ----
    if (template.kind === 'faran') {
      const macros = library.cells.filter(
        (c) => c.function === 'MACRO' && c.usableNadais.includes(nadai.id) && c.difficulty <= maxDifficulty
      );
      for (let cycles = 1; cycles <= 8; cycles++) {
        const target = avartanaMatras * cycles;
        const landing = 0; // farans resolve on sam; the macro cycle supplies its own landing
        const body = target - landing;
        for (const m of shuffled(macros, rng)) {
          if (m.matraCount > body || body % m.matraCount !== 0) continue;
          const reps = body / m.matraCount;
          const segs = [];
          for (let i = 0; i < reps; i++) {
            segs.push({ slotId: 'F', label: 'Faran cell', cells: [m], matras: m.matraCount });
          }
          const res = buildResolution({
            config, template, avartanas: cycles, segments: segs, repetitions: 1,
            landingCell: null, pad: 0, nadai, tala, rng, landingMode: 'none',
          });
          const v = validateResolution(res, { tala, nadai });
          if (v.ok) return { ok: true, resolution: res };
        }
      }
      return { ok: false, error: 'No faran cell divides this cycle length evenly. Try another cycles count or nadai.' };
    }

    // ---- standard / auto-avartanas templates ----
    let avartanas = config.avartanas;
    const auto = template.autoAvartanas || avartanas === 'auto';
    if (auto) avartanas = 'auto';

    const landingMode = template.landingMode || 'eduppu';
    const order = templateOrder(template);
    const occurrences = {};
    let fixedTotal = 0;
    for (const st of order) {
      if (st.fixed) {
        const c = library.cells.find((x) => x.id === st.fixed);
        fixedTotal += c ? c.matraCount : 0;
      } else occurrences[st.slotId] = (occurrences[st.slotId] || 0) + 1;
    }
    const variableSlots = template.slots.filter((s) => !s.fixedCell);

    // Landing-length candidates. With a real eduppu the landing length is
    // strictly determined. At samam the ending may be 1/2/3 units ("tham",
    // "tha tham", "tha ka tham") — the solver picks whichever lets the three
    // (or n) phrases divide the cycle exactly, as musicians do in practice.
    let landingCandidates = [0];
    if (landingMode === 'eduppu') {
      const preferred = computeLandingUnits(config.eduppuAksharas, nadai, kalai);
      if (config.eduppuAksharas > 0) {
        landingCandidates = [preferred];
      } else {
        const sizes = [...new Set(
          library.cells
            .filter((c) => c.function === 'LANDING' && c.usableNadais.includes(nadai.id))
            .map((c) => c.matraCount)
        )];
        sizes.sort((a, b) => Math.abs(a - preferred) - Math.abs(b - preferred));
        landingCandidates = sizes.length ? sizes : [preferred];
      }
    }

    const attempts = auto ? [1, 2, 3, 4, 5, 6, 7, 8] : [avartanas];
    for (const cycles of attempts) {
      const target = avartanaMatras * cycles;
      for (const L of landingCandidates) {
        const bodyRemaining = target - L - fixedTotal;
        if (bodyRemaining < 0) continue;
        const structures = enumerateStructures({
          template, variableSlots, bodyRemaining, occurrences, avartanaMatras, rng,
        });
        const withPad = structures
          .map((lens) => {
            const sum = variableSlots.reduce((s, sl) => s + (occurrences[sl.id] || 1) * lens[sl.id], 0);
            return { lens, pad: bodyRemaining - sum };
          })
          .filter((x) => (auto ? x.pad >= 0 && x.pad < avartanaMatras : x.pad === 0))
          .sort((a, b) => a.pad - b.pad);
        for (const { lens, pad } of withPad) {
          const filled = {};
          let fail = false;
          for (const slot of variableSlots) {
            if (filled[slot.id]) continue;
            const cells = fillSegment(lens[slot.id], slot, library, nadai.id, maxDifficulty, targetDifficulty, rng);
            if (!cells) { fail = true; break; }
            filled[slot.id] = { slotId: slot.id, label: slot.label, cells, matras: lens[slot.id] };
          }
          if (fail) continue;

          const segs = order.map((st) => {
            if (st.fixed) {
              const c = library.cells.find((x) => x.id === st.fixed);
              return { slotId: st.slotId, label: st.label, cells: [c], matras: c.matraCount, fixed: true };
            }
            return { ...filled[st.slotId] };
          });

          const landingCell = L > 0 ? pickLanding(library, L, nadai.id, rng) : null;
          if (L > 0 && !landingCell) continue;

          const res = buildResolution({
            config, template, avartanas: cycles, segments: segs, repetitions: 1,
            landingCell, pad, nadai, tala, rng, landingMode,
          });
          const v = validateResolution(res, { tala, nadai });
          if (v.ok) return { ok: true, resolution: res };
        }
      }
    }
    return {
      ok: false,
      error:
        'No exact fit for this tala/nadai/kalai/template combination. ' +
        'Try a different number of cycles (or Auto), another template, or a lower difficulty.',
    };
  }

  // Expand a template into an ordered list of steps {slotId,label,fixed?}
  function templateOrder(template) {
    const steps = [];
    if (template.id === 'mohra_korvai') {
      for (let r = 0; r < 3; r++) {
        for (const s of ['A', 'A', 'A', 'B']) steps.push({ slotId: s });
      }
      for (let r = 0; r < 3; r++) steps.push({ slotId: 'C', fixed: 'c_thathaiatham' });
      for (let r = 0; r < 3; r++) steps.push({ slotId: 'X' });
    } else if (template.id === 'tirmana') {
      for (let r = 0; r < 3; r++) steps.push({ slotId: 'T3' });
      for (let r = 0; r < 3; r++) steps.push({ slotId: 'T4' });
      for (let r = 0; r < 3; r++) steps.push({ slotId: 'T5' });
    } else if (template.id === 'gap_korvai') {
      steps.push({ slotId: 'X' }, { slotId: 'G' }, { slotId: 'X' }, { slotId: 'G' }, { slotId: 'X' });
    } else {
      const reps = template.repetitions || 1;
      const ids = template.slots.map((s) => s.id);
      for (let r = 0; r < reps; r++) for (const id of ids) steps.push({ slotId: id });
    }
    // attach labels + fixed
    const byId = {};
    template.slots.forEach((s) => (byId[s.id] = s));
    return steps.map((st) => ({
      ...st,
      label: byId[st.slotId] ? byId[st.slotId].label : st.slotId,
      fixed: st.fixed || (byId[st.slotId] && byId[st.slotId].fixedCell) || null,
    }));
  }

  // Enumerate feasible slot-length maps. The expansion order of a template may
  // repeat slots (gap korvai = X G X G X), so the equation is
  //   Σ occurrences(slot) * length(slot) + fixedTotal = bodyRemaining  (exact)
  // or ≤ bodyRemaining (auto/pad mode). Honors staircase & multipleOf.
  function enumerateStructures({ template, variableSlots, bodyRemaining, occurrences, avartanaMatras }) {
    const results = [];
    const n = variableSlots.length;
    if (!n) return bodyRemaining >= 0 ? [{}] : [];
    const stair = template.staircase || 0;
    const multipleOfAv = template.multipleOf === 'avartana';

    function rec(i, remaining, acc, sum) {
      if (results.length >= 60) return;
      if (i === n) { results.push({ lens: { ...acc }, sum }); return; }
      const slot = variableSlots[i];
      const occ = occurrences[slot.id] || 1;
      let lo = Math.max(1, slot.minMatra || 1);
      let hi = Math.min(slot.maxMatra || 64, Math.floor(remaining / occ));
      if (hi < lo) return;
      if (stair && i > 0) lo = Math.max(lo, acc[variableSlots[i - 1].id] + stair);
      if (multipleOfAv) {
        lo = Math.max(lo, avartanaMatras);
        hi = hi - (hi % avartanaMatras);
        if (hi < lo) return;
      }
      const aim = bodyRemaining / Math.max(1, n - i);
      const candidates = [];
      for (let v = lo; v <= hi; v++) {
        if (multipleOfAv && v % avartanaMatras !== 0) continue;
        candidates.push(v);
      }
      candidates.sort((a, b) => Math.abs(a - aim) - Math.abs(b - aim));
      for (const v of candidates) {
        acc[slot.id] = v;
        rec(i + 1, remaining - occ * v, acc, sum + occ * v);
        delete acc[slot.id];
      }
    }
    rec(0, bodyRemaining, {}, 0);
    return results.map((r) => r.lens);
  }

  /* ---------- resolution object ---------- */
  function buildResolution({ config, template, avartanas, segments, repetitions, landingCell, pad, nadai, tala, rng, landingMode }) {
    const landing = landingCell ? landingCell.matraCount : 0;
    return {
      id: 'k' + Date.now().toString(36) + Math.floor(rng() * 1e6).toString(36),
      config: {
        talaId: tala.id, talaName: tala.name, jati: tala.jati, nadaiId: nadai.id, nadaiName: nadai.name,
        kalai: config.kalai, eduppuAksharas: config.eduppuAksharas || 0,
        avartanas, templateId: template.id, templateName: template.name,
        seed: config.seed || 1, maxDifficulty: config.maxDifficulty || 5,
        targetDifficulty: config.targetDifficulty || 3,
        landingMode: landingMode || template.landingMode || 'eduppu',
      },
      template,
      segments,           // [{slotId,label,cells,matras,fixed?}]
      repetitions,
      landingCell,
      landing,
      pad,                // front kaarvai (matras)
      totalMatras: pad + segments.reduce((s, x) => s + x.matras, 0) + landing,
      source: 'engine',
      generatedAt: new Date().toISOString(),
    };
  }

  /* ---------- THE VALIDATOR (single source of truth) ---------- */
  // Re-derives every count from the resolution itself. Any AI output or
  // remix result must pass this. No exceptions.
  function validateResolution(res, opts) {
    opts = opts || {};
    const errors = [];
    const cfg = res.config;

    function err(m) { errors.push(m); }

    // recompute tala from ids stored in the resolution? engine-only res carries config;
    // the caller supplies library lookups via opts.tala/nadai when re-validating.
    const tala = opts.tala || null;
    const nadai = opts.nadai || null;

    if (!Array.isArray(res.segments)) err('segments missing');
    let segTotal = 0;
    (res.segments || []).forEach((seg, i) => {
      let segMatras = 0;
      (seg.cells || []).forEach((c) => {
        // cell internal consistency
        const dur = (c.durations || []).reduce((a, b) => a + b, 0);
        if (dur !== c.matraCount) err(`segment ${i + 1}: cell ${c.id} durations (${dur}) != matraCount (${c.matraCount})`);
        if ((c.syllables || []).length !== (c.durations || []).length)
          err(`segment ${i + 1}: cell ${c.id} syllables/durations length mismatch`);
        if (nadai && c.usableNadais && !c.usableNadais.includes(nadai.id))
          err(`segment ${i + 1}: cell ${c.id} not usable in nadai ${nadai.id}`);
        segMatras += c.matraCount;
      });
      if (segMatras !== seg.matras)
        err(`segment ${i + 1}: cells sum to ${segMatras} but declared ${seg.matras}`);
      segTotal += segMatras;
    });

    const landing = res.landingCell ? res.landingCell.matraCount : 0;
    if (res.landing !== landing) err(`landing mismatch: declared ${res.landing}, cell gives ${landing}`);
    if (res.landingCell && nadai && !res.landingCell.usableNadais.includes(nadai.id))
      err('landing cell not usable in this nadai');
    if (res.landingCell && res.landingCell.function !== 'LANDING')
      err('landing cell must have function LANDING');

    const recomputedTotal = (res.pad || 0) + segTotal + landing;
    if (recomputedTotal !== res.totalMatras)
      err(`totalMatras declared ${res.totalMatras}, recomputed ${recomputedTotal}`);

    if (tala && nadai) {
      const avartanaMatras = tala.aksharas * nadai.subdivision * cfg.kalai;
      if (avartanaMatras * cfg.avartanas !== recomputedTotal)
        err(
          `does not fill ${cfg.avartanas} avartanas exactly: needs ${avartanaMatras * cfg.avartanas}, got ${recomputedTotal}`
        );
      if ((res.pad || 0) < 0 || (res.pad || 0) >= avartanaMatras) err('front pad out of range');
      if (cfg.landingMode === 'none' && landing !== 0) err('landingMode none must have no landing cell');
      if (cfg.eduppuAksharas > 0 && cfg.landingMode !== 'none') {
        const expect = Math.max(1, Math.round(cfg.eduppuAksharas * nadai.subdivision * cfg.kalai));
        if (landing !== expect) err(`eduppu ${cfg.eduppuAksharas} requires landing of ${expect}, got ${landing}`);
      }
    }

    return { ok: errors.length === 0, errors };
  }

  /* ---------- rendering helpers ---------- */
  function segmentSollukattu(seg) {
    return seg.cells.map((c) => c.syllables.join(' ')).join(' ');
  }
  function resolutionSollukattu(res) {
    const parts = [];
    if (res.pad > 0) parts.push('— '.repeat(res.pad).trim());
    res.segments.forEach((seg) => parts.push(segmentSollukattu(seg)));
    if (res.landingCell) parts.push(res.landingCell.syllables.join(' '));
    return parts.filter(Boolean).join('  |  ');
  }

  // Build a flat event timeline (for grid + audio), aligned to matra indices.
  // tala/nadai needed for akshara mapping.
  function buildTimeline(res, tala, nadai) {
    const events = [];
    let m = 0;
    const push = (cell, kind, segIndex) => {
      let local = 0;
      cell.syllables.forEach((syl, i) => {
        const dur = cell.durations[i];
        events.push({
          matra: m, matras: dur, syllable: syl, weight: cell.weights[i],
          kind, segIndex, cellId: cell.id,
        });
        m += dur; local += dur;
      });
      return local;
    };
    if (res.pad > 0) {
      for (let i = 0; i < res.pad; i++) events.push({ matra: m, matras: 1, syllable: '—', weight: 'L', kind: 'pad', segIndex: -1, cellId: 'pad' }), m++;
    }
    res.segments.forEach((seg, si) => seg.cells.forEach((c) => push(c, 'segment', si)));
    if (res.landingCell) push(res.landingCell, 'landing', -2);
    return { events, totalMatras: m };
  }

  /* ---------- dance counts (Bharatanatyam layer) ---------- */
  // Counts are grouped 1..8; each count spans `kalai` aksharas (2-kalai counts
  // are held twice as long). Returns blocks with the syllables under each count.
  function danceCounts(res, tala, nadai) {
    const tl = buildTimeline(res, tala, nadai);
    const matrasPerAkshara = nadai.subdivision * res.config.kalai;
    const aksharasPerCount = Math.max(1, res.config.kalai);
    const matrasPerCount = matrasPerAkshara * aksharasPerCount;
    const blocks = [];
    const totalAksharas = Math.ceil(tl.totalMatras / matrasPerAkshara);
    const counts = Math.ceil(totalAksharas / aksharasPerCount);
    for (let c = 0; c < counts; c++) {
      const from = c * matrasPerCount;
      const to = from + matrasPerCount;
      const evs = tl.events.filter((e) => e.matra >= from && e.matra < to);
      blocks.push({
        count: (c % 8) + 1,
        isSam: c % 8 === 0,
        aksharaSpan: [from / matrasPerAkshara, to / matrasPerAkshara],
        sollukattu: evs.map((e) => e.syllable).join(' '),
      });
    }
    return { countsPerAvartana: tala.aksharas / aksharasPerCount, blocks };
  }

  /* ---------- adavu suggestions (heuristic, Bharatanatyam layer) ---------- */
  function suggestAdavus(res, library, nadaiId) {
    const chars = {};
    res.segments.forEach((s) => s.cells.forEach((c) => { chars[c.character] = (chars[c.character] || 0) + c.matras; }));
    const rankedChars = Object.entries(chars).sort((a, b) => b[1] - a[1]).map((x) => x[0]);
    const adavus = (library.adavus || []).filter(
      (a) => a.nadais.includes(nadaiId) && a.characters.some((ch) => rankedChars.includes(ch))
    );
    return adavus
      .map((a) => {
        const charScore = Math.max(...a.characters.map((c) => {
          const idx = rankedChars.indexOf(c);
          return idx === -1 ? 0 : rankedChars.length - idx;
        }));
        const diffPenalty = Math.abs(a.difficulty - (res.config.targetDifficulty || 3));
        return { adavu: a, score: charScore - diffPenalty * 0.5 };
      })
      .sort((x, y) => y.score - x.score)
      .slice(0, 4)
      .map((x) => x.adavu);
  }

  /* ---------- remix operations (V2 — all deterministic) ---------- */
  function derivedCell(cell, syllables, durations, rng) {
    const weights = syllables.map((s) => (/^(tha|thom|tham|tom|nam|tai)/i.test(s) ? 'H' : 'L'));
    return {
      ...cell, id: cell.id + '#d' + Math.floor(rng() * 1e5),
      syllables, durations,
      matraCount: durations.reduce((a, b) => a + b, 0),
      weights, derived: true, baseCellId: cell.id,
    };
  }

  function mapSegments(res, fn) {
    return res.segments.map((seg, i) => ({ ...seg, cells: fn(seg.cells.slice(), i, seg) }));
  }

  const remixOps = {
    reverse: {
      label: 'Reverse', desc: 'Play cells (and syllables) backwards within each phrase.',
      apply(res, ctx) {
        const rng = makeRng((ctx.seed || 1) + 101);
        const segments = mapSegments(res, (cells) =>
          cells.slice().reverse().map((c) => derivedCell(c, c.syllables.slice().reverse(), c.durations.slice().reverse(), rng))
        );
        return { ...res, segments, id: res.id + '-rev', source: 'remix:reverse' };
      },
    },
    densify: {
      label: 'Densify', desc: 'Substitute sparser material with busier cells (same counts).',
      apply(res, ctx) {
        const rng = makeRng((ctx.seed || 1) + 202);
        const lib = ctx.library, nadaiId = res.config.nadaiId;
        const maxD = Math.min(5, res.config.targetDifficulty + 2);
        const segments = mapSegments(res, (cells) => {
          const out = [];
          for (const c of cells) {
            if (c.kaarvai || c.function === 'GAP') {
              // fill the rest with the densest allowed CORE/FILLER cells
              let remaining = c.matraCount;
              const slot = { allowedFunctions: ['CORE', 'FILLER'], allowGaps: false };
              const fill = fillSegment(remaining, slot, lib, nadaiId, maxD, res.config.targetDifficulty + 1, rng, { maxPieces: 4 });
              if (fill) out.push(...fill); else out.push(c);
            } else {
              const alt = lib.cells.filter(
                (x) => x.matraCount === c.matraCount && x.usableNadais.includes(nadaiId) &&
                  x.function !== 'LANDING' && x.function !== 'GAP' && x.syllables.length > c.syllables.length &&
                  x.difficulty <= maxD
              );
              if (alt.length && rng() < 0.7) out.push(alt[Math.floor(rng() * alt.length)]);
              else out.push(c);
            }
          }
          return out;
        });
        return { ...res, segments, id: res.id + '-den', source: 'remix:densify' };
      },
    },
    simplify: {
      label: 'Simplify', desc: 'Substitute busier material with sparser cells and kaarvai (same counts).',
      apply(res, ctx) {
        const rng = makeRng((ctx.seed || 1) + 303);
        const lib = ctx.library, nadaiId = res.config.nadaiId;
        const segments = mapSegments(res, (cells) =>
          cells.map((c) => {
            const alt = lib.cells.filter(
              (x) => x.matraCount === c.matraCount && x.usableNadais.includes(nadaiId) &&
                x.function !== 'LANDING' && (x.kaarvai || x.syllables.length < c.syllables.length)
            );
            if (alt.length && rng() < 0.75) return alt[Math.floor(rng() * alt.length)];
            return c;
          })
        );
        return { ...res, segments, id: res.id + '-sim', source: 'remix:simplify' };
      },
    },
    changeEnding: {
      label: 'Change ending', desc: 'Swap the landing phrase for another of the same length.',
      apply(res, ctx) {
        const rng = makeRng((ctx.seed || 1) + 404);
        const lib = ctx.library;
        if (!res.landingCell) return { ...res, id: res.id + '-end', source: 'remix:ending' };
        const alts = lib.cells.filter(
          (c) => c.function === 'LANDING' && c.matraCount === res.landingCell.matraCount &&
            c.usableNadais.includes(res.config.nadaiId) && c.id !== res.landingCell.id
        );
        if (!alts.length) {
          // compose a landing from tham/tha units
          const units = res.landingCell.matraCount;
          const one = lib.cells.find((c) => c.id === 'c_tham');
          if (one) {
            const cells = [];
            for (let i = 0; i < units; i++) cells.push(one);
            const merged = derivedCell(one, cells.flatMap((c) => c.syllables), cells.flatMap((c) => c.durations), rng);
            merged.function = 'LANDING';
            return { ...res, landingCell: merged, landing: merged.matraCount, id: res.id + '-end', source: 'remix:ending' };
          }
          return { ...res, id: res.id + '-end', source: 'remix:ending' };
        }
        const pick = alts[Math.floor(rng() * alts.length)];
        return { ...res, landingCell: pick, landing: pick.matraCount, id: res.id + '-end', source: 'remix:ending' };
      },
    },
    changeSolkattu: {
      label: 'Keep structure / change solkattu', desc: 'Same phrase lengths, fresh cell choices.',
      apply(res, ctx) {
        const rng = makeRng((ctx.seed || 1) + 505);
        const lib = ctx.library, nadaiId = res.config.nadaiId;
        const byId = {};
        res.template.slots.forEach((s) => (byId[s.id] = s));
        const segments = mapSegments(res, (cells, i, seg) => {
          const slot = byId[seg.slotId] || { allowedFunctions: ['CORE', 'FILLER', 'TRANSITION'], allowGaps: true };
          let fill = fillSegment(seg.matras, slot, lib, nadaiId, res.config.maxDifficulty, res.config.targetDifficulty, rng, {
            firstCellMustDifferFrom: cells[0] && cells[0].id,
          });
          if (!fill) fill = cells;
          return fill;
        });
        return { ...res, segments, id: res.id + '-sol', source: 'remix:solkattu' };
      },
    },
    reseed: {
      label: 'New variation', desc: 'Re-run the solver with a fresh seed (same constraints).',
      apply(res, ctx) { return res; }, // handled specially in UI via solve()
    },
  };

  // changeNadai / changeJathi: full re-solve at new nadai, keeping template & difficulty feel.
  function resolveWithConfig(res, newConfig, library) {
    const cfg = {
      ...newConfig,
      seed: (newConfig.seed || 1) + 707,
      library,
    };
    return solve(cfg);
  }

  /* ---------- exports ---------- */
  const engine = {
    makeRng, applyJati, totalMatras, computeLandingUnits, clapPattern,
    fillSegment, kuraippuLengths, pickLanding, solve, templateOrder,
    enumerateStructures, buildResolution, validateResolution,
    segmentSollukattu, resolutionSollukattu, buildTimeline,
    danceCounts, suggestAdavus, remixOps, resolveWithConfig,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = engine;
  else root.KorvaiEngine = engine;
})(typeof self !== 'undefined' ? self : this);
