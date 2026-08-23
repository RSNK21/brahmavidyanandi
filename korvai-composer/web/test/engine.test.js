/* Engine unit tests — run: node test/engine.test.js */
const fs = require('fs');
const path = require('path');
const engine = require('../src/engine.js');
const library = JSON.parse(fs.readFileSync(path.join(__dirname, '../../data/seed.json'), 'utf8'));

const byId = (arr, id) => arr.find((x) => x.id === id);
let pass = 0, fail = 0;
function assert(cond, msg) {
  if (cond) { pass++; }
  else { fail++; console.error('  ✗ FAIL:', msg); }
}
function section(name) { console.log('■', name); }

const { applyJati, totalMatras, solve, validateResolution, kuraippuLengths, makeRng } = engine;

/* ---------- 1. tala math ---------- */
section('Tala arithmetic');
{
  const adi = byId(library.talas, 'adi');
  const ch = byId(library.nadais, 'chaturasra');
  assert(totalMatras(adi, ch, 1, 1) === 32, 'Adi chaturasra 1 kalai 1 avartana = 32 matras');
  assert(totalMatras(adi, ch, 2, 2) === 128, 'Adi chaturasra 2 kalai 2 avartanas = 128 matras');
  const tisra = byId(library.nadais, 'tisra');
  assert(totalMatras(adi, tisra, 1, 1) === 24, 'Adi tisra = 24 matras');
  const adiTisraJati = applyJati(adi, 'tisra', library.jatis);
  assert(adiTisraJati.aksharas === 7, 'Adi with Tisra jati (laghu=3) = 7 aksharas');
  const adiKhanda = applyJati(adi, 'khanda', library.jatis);
  assert(adiKhanda.aksharas === 9, 'Adi with Khanda jati = 9 aksharas');
}

/* ---------- 2. the canonical worked example: 13+14+15 ×3 + 2 = 128 ---------- */
section('Canonical korvai 13-14-15 (Adi, Chaturasra, 2 kalai, 2 avartanas)');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_crescendo');
  const out = solve({
    tala: adi, nadai: ch, kalai: 2, eduppuAksharas: 0, avartanas: 2,
    template: tpl, library, seed: 42, maxDifficulty: 5, targetDifficulty: 3,
  });
  assert(out.ok, 'solver succeeds: ' + (out.error || ''));
  if (out.ok) {
    const r = out.resolution;
    const lens = r.segments.map((s) => s.matras);
    // segments are (A B C) ×3; the staircase should be 13/14/15
    assert(lens.length === 9, '9 segments (A B C ×3), got ' + lens.length);
    assert(lens[0] === 13 && lens[1] === 14 && lens[2] === 15,
      `staircase is 13/14/15, got ${lens[0]}/${lens[1]}/${lens[2]}`);
    assert(r.landing === 2, 'landing = 2 units');
    assert(r.totalMatras === 128, 'total = 128, got ' + r.totalMatras);
    const v = validateResolution(r, { tala: adi, nadai: ch });
    assert(v.ok, 'validator passes: ' + v.errors.join('; '));
    console.log('   ', engine.resolutionSollukattu(r).replace(/\s+/g, ' ').slice(0, 110) + '…');
  }
}

/* ---------- 3. validator catches corruption ---------- */
section('Validator rejects broken output');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_x3');
  const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: 1, template: tpl, library, seed: 7 });
  assert(out.ok, 'korvai_x3 solvable for 1 avartana');
  if (out.ok) {
    const r = out.resolution;
    // tamper: drop one cell from a segment
    const bad = { ...r, segments: r.segments.map((s, i) => (i === 0 ? { ...s, cells: s.cells.slice(1) } : s)) };
    const v1 = validateResolution(bad, { tala: adi, nadai: ch });
    assert(!v1.ok, 'detects segment sum mismatch');
    // tamper: wrong total declared
    const bad2 = { ...r, totalMatras: r.totalMatras + 1 };
    const v2 = validateResolution(bad2, { tala: adi, nadai: ch });
    assert(!v2.ok, 'detects totalMatras mismatch');
    // tamper: landing replaced by non-LANDING cell
    const bad3 = { ...r, landingCell: byId(library.cells, 'c_taka') };
    const v3 = validateResolution(bad3, { tala: adi, nadai: ch });
    assert(!v3.ok, 'detects non-LANDING landing cell');
    // nadai incompatibility
    const tisraOnly = byId(library.cells, 'c_takita_x2');
    const bad4 = { ...r, segments: r.segments.map((s, i) => (i === 0 ? { ...s, cells: [tisraOnly, ...s.cells] } : s)) };
    const v4 = validateResolution(bad4, { tala: adi, nadai: ch });
    assert(!v4.ok, 'detects cell/nadai mismatch');
  }
}

/* ---------- 4. every template solves for at least one config ---------- */
section('All templates solvable (Chaturasra)');
{
  const combos = [
    { kalai: 1, av: [1, 2, 3, 4] }, { kalai: 2, av: [1, 2, 3] },
  ];
  let allOk = true;
  for (const tpl of library.templates) {
    let ok = false, tried = 0;
    for (const { kalai, av } of combos) {
      for (const cycles of av) {
        if (ok) break;
        tried++;
        const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
        const ch = byId(library.nadais, 'chaturasra');
        const out = solve({
          tala: adi, nadai: ch, kalai, eduppuAksharas: 0,
          avartanas: tpl.autoAvartanas ? 'auto' : cycles,
          template: tpl, library, seed: 11, maxDifficulty: 5, targetDifficulty: 3,
        });
        if (out.ok) {
          const v = validateResolution(out.resolution, { tala: adi, nadai: ch });
          if (v.ok) { ok = true; }
        }
      }
      if (ok) break;
    }
    assert(ok, `template ${tpl.id} solved+validated (tried ${tried} configs)`);
    if (!ok) allOk = false;
  }
}

/* ---------- 5. other talas ---------- */
section('Other talas (Rupaka, Misra Chapu, Ata, Tisra Triputa)');
{
  for (const tid of ['rupaka', 'misra_chapu', 'ata', 'tisra_triputa', 'khanda_chapu']) {
    const tala = applyJati(byId(library.talas, tid), byId(library.talas, tid).jati, library.jatis);
    const ch = byId(library.nadais, 'chaturasra');
    const tpl = byId(library.templates, 'korvai_x3');
    let ok = false;
    for (const cycles of [1, 2, 3, 4, 5, 6]) {
      const out = solve({ tala, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: cycles, template: tpl, library, seed: 5 });
      if (out.ok) { ok = true; break; }
    }
    assert(ok, `korvai_x3 on ${tid}`);
  }
}

/* ---------- 6. nadais ---------- */
section('Nadais (Tisra, Khanda, Misra)');
{
  for (const nid of ['tisra', 'khanda']) {
    const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
    const nadai = byId(library.nadais, nid);
    const tpl = byId(library.templates, 'korvai_x3');
    let ok = false;
    for (const cycles of [1, 2, 3, 4]) {
      const out = solve({ tala: adi, nadai, kalai: 1, eduppuAksharas: 0, avartanas: cycles, template: tpl, library, seed: 9 });
      if (out.ok) {
        const v = validateResolution(out.resolution, { tala: adi, nadai });
        if (v.ok) { ok = true; break; }
      }
    }
    assert(ok, `korvai_x3 in ${nid} nadai`);
  }
}

/* ---------- 7. kuraippu ---------- */
section('Kuraippu diminishing series');
{
  assert(JSON.stringify(kuraippuLengths(126)) === JSON.stringify([64, 32, 16, 8, 4, 2]), '126 → 64+32+16+8+4+2');
  assert(kuraippuLengths(50).reduce((a, b) => a + b, 0) === 50, 'arbitrary body sums exactly');
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'kuraippu');
  const out = solve({ tala: adi, nadai: ch, kalai: 2, eduppuAksharas: 0, avartanas: 2, template: tpl, library, seed: 3 });
  assert(out.ok, 'kuraippu solves on Adi 2-kalai 2-avartanas (body 126)');
  if (out.ok) {
    const lens = out.resolution.segments.map((s) => s.matras);
    assert(lens[0] > lens[lens.length - 1], 'kuraippu strictly diminishes');
    console.log('    lengths:', lens.join(', '), 'landing', out.resolution.landing);
  }
}

/* ---------- 8. mohra + tirmana (auto avartanas) ---------- */
section('Mohra→Korvai and Tirmana (auto cycles)');
{
  for (const tid of ['mohra_korvai', 'tirmana']) {
    const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
    const ch = byId(library.nadais, 'chaturasra');
    const tpl = byId(library.templates, tid);
    const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: 'auto', template: tpl, library, seed: 13 });
    assert(out.ok, `${tid} solves (auto)`);
    if (out.ok) {
      const r = out.resolution;
      const v = validateResolution(r, { tala: adi, nadai: ch });
      assert(v.ok, `${tid} validates: ` + v.errors.join('; '));
      console.log(`    ${tid}: cycles=${r.config.avartanas} pad=${r.pad} total=${r.totalMatras} segs=${r.segments.length}`);
    }
  }
}

/* ---------- 9. faran divisibility ---------- */
section('Faran cross-rhythm');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'faran');
  const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: 'auto', template: tpl, library, seed: 21 });
  assert(out.ok, 'faran solves');
  if (out.ok) {
    const m = out.resolution.segments[0].cells[0].matraCount;
    const n = out.resolution.segments.length;
    assert(m * n + out.resolution.landing === out.resolution.totalMatras, 'macro × reps + landing = total');
    console.log(`    faran: ${m}×${n} + ${out.resolution.landing} = ${out.resolution.totalMatras}`);
  }
}

/* ---------- 10. eduppu ---------- */
section('Eduppu (1/2 akshara landing = 2 units in Chaturasra 1-kalai)');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_x3');
  const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0.5, avartanas: 1, template: tpl, library, seed: 17 });
  assert(out.ok, 'korvai with arai eduppu solves');
  if (out.ok) assert(out.resolution.landing === 2, 'arai eduppu landing = 2 units');
}

/* ---------- 11. determinism & variations ---------- */
section('Determinism / variation batches');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_crescendo');
  const base = { tala: adi, nadai: ch, kalai: 2, eduppuAksharas: 0, avartanas: 2, template: tpl, library, maxDifficulty: 5, targetDifficulty: 3 };
  const a = solve({ ...base, seed: 100 });
  const b = solve({ ...base, seed: 100 });
  const c = solve({ ...base, seed: 101 });
  assert(a.ok && b.ok && c.ok, 'all solve');
  if (a.ok && b.ok) {
    assert(engine.resolutionSollukattu(a.resolution) === engine.resolutionSollukattu(b.resolution), 'same seed ⇒ identical output');
  }
  if (a.ok && c.ok) {
    const sa = engine.resolutionSollukattu(a.resolution);
    const sc = engine.resolutionSollukattu(c.resolution);
    assert(sa !== sc, 'different seed ⇒ different solkattu');
  }
}

/* ---------- 12. remix ops validate ---------- */
section('Remix ops preserve validity');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_crescendo');
  const out = solve({ tala: adi, nadai: ch, kalai: 2, eduppuAksharas: 0, avartanas: 2, template: tpl, library, seed: 42 });
  assert(out.ok, 'base solve');
  if (out.ok) {
    for (const [key, op] of Object.entries(engine.remixOps)) {
      if (key === 'reseed') continue;
      const remixed = op.apply(out.resolution, { seed: 77, library });
      const v = validateResolution(remixed, { tala: adi, nadai: ch });
      assert(v.ok, `remix ${key} validates` + (v.ok ? '' : ': ' + v.errors.join('; ')));
      assert(remixed.totalMatras === out.resolution.totalMatras, `remix ${key} keeps total matras`);
    }
  }
}

/* ---------- 13. dance counts + adavus ---------- */
section('Dance counts & adavu suggestions');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_x3');
  const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: 1, template: tpl, library, seed: 42 });
  if (out.ok) {
    const dc = engine.danceCounts(out.resolution, adi, ch);
    assert(dc.blocks.length === 8, `Adi 1-kalai ⇒ 8 counts, got ${dc.blocks.length}`);
    const ad = engine.suggestAdavus(out.resolution, library, 'chaturasra');
    assert(ad.length >= 1, 'at least one adavu suggestion');
    console.log('    adavus:', ad.map((a) => a.name).join(' · '));
  } else assert(false, 'base solve failed');
}

/* ---------- 14. timeline integrity ---------- */
section('Timeline integrity');
{
  const adi = applyJati(byId(library.talas, 'adi'), 'chaturasra', library.jatis);
  const ch = byId(library.nadais, 'chaturasra');
  const tpl = byId(library.templates, 'korvai_x3');
  const out = solve({ tala: adi, nadai: ch, kalai: 1, eduppuAksharas: 0, avartanas: 1, template: tpl, library, seed: 42 });
  if (out.ok) {
    const tl = engine.buildTimeline(out.resolution, adi, ch);
    assert(tl.totalMatras === out.resolution.totalMatras, 'timeline covers every matra');
    assert(tl.events[0].matra === 0, 'starts at matra 0');
    const last = tl.events[tl.events.length - 1];
    assert(last.matra + last.matras === tl.totalMatras, 'ends exactly at total');
  }
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
