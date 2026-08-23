#!/usr/bin/env node
/* Generate SeedData.kt from the canonical data/seed.json (single source of truth). */
const fs = require('fs');
const path = require('path');
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'seed.json'), 'utf8'));

const esc = (s) => String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$').replace(/`/g, '\\`');
const str = (s) => `"${esc(s)}"`;
const dstr = (x) => (Number.isInteger(x) ? x.toFixed(1) : String(x));
const list = (arr, fn) => `listOf(${arr.map(fn).join(', ')})`;
const enumRef = (name, v) => `${name}.${v.toUpperCase().replace(/[^A-Z_]/g, '_')}`;

let out = `// AUTO-GENERATED from data/seed.json — do not edit by hand; regenerate with web/gen-kotlin-seed.js
package com.korvai.engine

object SeedData {
    val jatis = ${list(seed.jatis, (j) => `Jati(id = ${str(j.id)}, name = ${str(j.name)}, laghu = ${j.laghu})`)}

    val talas = ${list(seed.talas, (t) => `Tala(
        id = ${str(t.id)}, name = ${str(t.name)}, jati = ${str(t.jati)},
        angas = ${list(t.angas, (a) => `Anga(AngaType.${a.type.toUpperCase()}, ${a.aksharas})`)},
        aksharas = ${t.aksharas}
    )`)}

    val nadais = ${list(seed.nadais, (n) => `Nadai(id = ${str(n.id)}, name = ${str(n.name)}, subdivision = ${n.subdivision})`)}

    val eduppus = ${list(seed.eduppus, (e) => `Eduppu(id = ${str(e.id)}, name = ${str(e.name)}, aksharas = ${dstr(e.aksharas)})`)}

    val cells = ${list(seed.cells, (c) => `RhythmicCell(
        id = ${str(c.id)}, notation = ${str(c.notation)},
        syllables = ${list(c.syllables, str)}, durations = ${list(c.durations, (d) => String(d))},
        matraCount = ${c.matraCount}, weights = ${list(c.weights, (w) => `Weight.${w}`)},
        character = CellCharacter.${c.character.toUpperCase()}, function = CellFunction.${c.function.toUpperCase()},
        usableNadais = ${list(c.usableNadais, str)}, difficulty = ${c.difficulty},
        kaarvai = ${c.kaarvai ? 'true' : 'false'}${c.tags ? `, tags = ${list(c.tags, str)}` : ''}
    )`)}

    val aliases = ${list(seed.aliases || [], (a) => `Alias(notation = ${str(a.notation)}, variants = ${list(a.variants, str)})`)}

    val templates = ${list(seed.templates, (t) => `Template(
        id = ${str(t.id)}, name = ${str(t.name)}, tags = ${list(t.tags, str)},
        description = ${str(t.description)}, structure = ${str(t.structure)},
        repetitions = ${t.repetitions}, landingMode = ${str(t.landingMode)}${t.staircase ? `, staircase = ${t.staircase}` : ''}${t.multipleOf ? `, multipleOf = ${str(t.multipleOf)}` : ''}${t.autoAvartanas ? ', autoAvartanas = true' : ''}${t.kind ? `, kind = ${str(t.kind)}` : ''},
        slots = ${list(t.slots, (s) => `Slot(
            id = ${str(s.id)}, label = ${str(s.label)}${s.minMatra != null ? `, minMatra = ${s.minMatra}` : ''}${s.maxMatra != null ? `, maxMatra = ${s.maxMatra}` : ''}${s.allowedFunctions ? `, allowedFunctions = ${list(s.allowedFunctions, (f) => enumRef('CellFunction', f))}` : ''}${s.allowGaps ? ', allowGaps = true' : ''}${s.fixedCell ? `, fixedCell = ${str(s.fixedCell)}` : ''}
        )`)}
    )`)}

    val adavus = ${list(seed.adavus, (a) => `Adavu(
        id = ${str(a.id)}, name = ${str(a.name)}, family = ${str(a.family)}, sollukattu = ${str(a.sollukattu)},
        counts = ${a.counts}, characters = ${list(a.characters, (c) => enumRef('CellCharacter', c))},
        nadais = ${list(a.nadais, str)}, description = ${str(a.description)}, difficulty = ${a.difficulty}
    )`)}

    val library: Library = Library(talas, jatis, nadais, eduppus, cells, aliases, templates, adavus)
}
`;

fs.mkdirSync(path.join(__dirname, '..', 'android', 'engine', 'src', 'main', 'kotlin', 'com', 'korvai', 'engine'), { recursive: true });
fs.writeFileSync(path.join(__dirname, '..', 'android', 'engine', 'src', 'main', 'kotlin', 'com', 'korvai', 'engine', 'SeedData.kt'), out);
console.log('wrote android/engine/src/main/kotlin/com/korvai/engine/SeedData.kt —', (out.length / 1024).toFixed(1), 'KB');
