/* =====================================================================
 * Audio renderer — deterministic sample/event synthesis only.
 * (Guardrail: audio is NEVER AI-generated. Every syllable is a small
 * synthesized percussion voice scheduled exactly on the tala grid.)
 * ===================================================================== */
(function (root) {
  'use strict';

  let ctx = null;
  function getCtx() {
    if (!ctx) {
      const AC = root.AudioContext || root.webkitAudioContext;
      ctx = new AC();
    }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }

  /* ---------- tiny synth voices ---------- */
  function noiseBuffer(c) {
    const len = Math.floor(c.sampleRate * 0.12);
    const buf = c.createBuffer(1, len, c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    return buf;
  }
  let _noise = null;

  function snap(t, freq, dur, gain) {
    const c = ctx;
    const src = c.createBufferSource();
    if (!_noise) _noise = noiseBuffer(c);
    src.buffer = _noise;
    const bp = c.createBiquadFilter();
    bp.type = 'bandpass'; bp.frequency.value = freq; bp.Q.value = 1.2;
    const g = c.createGain();
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    src.connect(bp).connect(g).connect(c.destination);
    src.start(t); src.stop(t + dur + 0.02);
  }
  function ping(t, freq, dur, gain) {
    const c = ctx;
    const o = c.createOscillator();
    o.type = 'triangle';
    o.frequency.setValueAtTime(freq, t);
    const g = c.createGain();
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(c.destination);
    o.start(t); o.stop(t + dur + 0.02);
  }
  function drop(t, f0, f1, dur, gain) {
    const c = ctx;
    const o = c.createOscillator();
    o.type = 'sine';
    o.frequency.setValueAtTime(f0, t);
    o.frequency.exponentialRampToValueAtTime(f1, t + dur * 0.8);
    const g = c.createGain();
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(c.destination);
    o.start(t); o.stop(t + dur + 0.02);
  }

  /* ---------- syllable → voice recipe ---------- */
  const RECIPES = {
    snapHi:  (t, w) => { snap(t, 3200, 0.045, w === 'H' ? 0.5 : 0.32); ping(t, 2400, 0.03, 0.1); },
    snap:    (t, w) => { snap(t, 2100, 0.05, w === 'H' ? 0.45 : 0.3); ping(t, 1500, 0.04, 0.12); },
    snapLo:  (t, w) => { snap(t, 1300, 0.06, w === 'H' ? 0.4 : 0.26); ping(t, 900, 0.05, 0.14); },
    tomMid:  (t, w) => { drop(t, 230, 150, 0.14, w === 'H' ? 0.5 : 0.34); snap(t, 2600, 0.03, 0.12); },
    tomLow:  (t, w) => { drop(t, 120, 55, 0.28, w === 'H' ? 0.62 : 0.42); },
    resonant:(t, w) => { drop(t, 95, 70, 0.3, w === 'H' ? 0.65 : 0.45); ping(t, 190, 0.22, 0.12); },
  };
  const SYLLABLE_MAP = {
    ta: 'snapHi', ka: 'snap', tat: 'snapHi', that: 'snapHi', thit: 'snapHi',
    dit: 'snapHi', hat: 'snapHi', ki: 'snap', ri: 'snap', ju: 'snapLo', nu: 'snapLo',
    jo: 'snapLo', yai: 'snap', hi: 'snap', mi: 'snapLo',
    di: 'snap', dhi: 'snap',
    tha: 'tomMid', tai: 'tomMid', thai: 'tomMid',
    thom: 'tomLow', tom: 'tomLow', dhin: 'tomLow',
    tham: 'resonant', tam: 'resonant', nam: 'resonant',
  };
  function playSyllable(syl, t, weight) {
    if (!syl || syl === '—' || syl === '-') return;
    const recipe = SYLLABLE_MAP[syl.toLowerCase()];
    if (recipe) RECIPES[recipe](t, weight);
    else RECIPES.snap(t, weight);
  }
  function tick(t, accent) {
    ping(t, accent ? 1660 : 1100, accent ? 0.09 : 0.05, accent ? 0.3 : 0.14);
  }

  /* ---------- scheduler ---------- */
  const S = {
    playing: false,
    timer: null,
    events: [],      // {time, syl, weight, kind, matra}
    meta: null,      // {matraDur, totalMatras, loops, loopDur, bpm, metronome, matrasPerAkshara, claps: []}
    startTime: 0,
    onEvent: null,   // (matra, event|null, time) visual callback
    onStop: null,
  };

  function schedule() {
    const c = getCtx();
    const horizon = c.currentTime + 0.18;
    while (S.idx < S.events.length && S.events[S.idx].time < horizon) {
      const e = S.events[S.idx];
      if (e.syl) playSyllable(e.syl, e.time, e.weight);
      else if (e.metronome) tick(e.time, e.accent);
      S.idx++;
    }
    if (S.idx >= S.events.length) {
      // all scheduled; wait out the tail then stop callback
      const tail = S.events.length ? S.events[S.events.length - 1].time : 0;
      if (c.currentTime > tail + 0.4) { S.stop(); }
    }
  }

  S.stop = function () {
    S.playing = false;
    if (S.timer) { clearInterval(S.timer); S.timer = null; }
    if (S.onStop) S.onStop();
  };

  // timeline: from engine.buildTimeline; tala/nadai for akshara mapping.
  S.play = function (timeline, opts) {
    opts = opts || {};
    const c = getCtx();
    S.stop();
    const bpm = opts.bpm || 60;
    const kalai = opts.kalai || 1;
    const subdivision = opts.subdivision || 4;
    const matraDur = 60 / (bpm * subdivision * kalai);
    const matrasPerAkshara = subdivision * kalai;
    const loopDur = timeline.totalMatras * matraDur;
    const loops = opts.loops || 1;

    const events = [];
    for (let l = 0; l < loops; l++) {
      const base = c.currentTime + 0.25 + l * loopDur;
      timeline.events.forEach((e) => {
        events.push({
          time: base + e.matra * matraDur,
          syl: e.syllable, weight: e.weight, kind: e.kind, matra: e.matra,
        });
      });
      if (opts.metronome) {
        const aksharas = Math.ceil(timeline.totalMatras / matrasPerAkshara);
        for (let a = 0; a < aksharas; a++) {
          const isSam = a % opts.aksharasPerAvartana === 0;
          events.push({ time: base + a * matrasPerAkshara * matraDur, metronome: true, accent: isSam });
        }
      }
    }
    events.sort((x, y) => x.time - y.time);
    S.events = events;
    S.idx = 0;
    S.meta = { matraDur, totalMatras: timeline.totalMatras, loopDur, bpm, startTime: c.currentTime + 0.25 };
    S.playing = true;
    S.timer = setInterval(schedule, 30);
    schedule();
    return S;
  };

  // current position in matras (for UI highlight)
  S.currentMatra = function () {
    if (!S.playing || !S.meta) return -1;
    const c = getCtx();
    const elapsed = c.currentTime - S.meta.startTime;
    if (elapsed < 0) return 0;
    return elapsed / S.meta.matraDur;
  };

  const audio = { play: S.play.bind(S), stop: S.stop.bind(S), get playing() { return S.playing; }, currentMatra: S.currentMatra.bind(S), scheduler: S, playSyllable, getCtx };
  if (typeof module !== 'undefined' && module.exports) module.exports = audio;
  else root.KorvaiAudio = audio;
})(typeof self !== 'undefined' ? self : this);
