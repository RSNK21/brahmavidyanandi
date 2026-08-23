/* =====================================================================
 * AI Rhythmic Selector layer (V3) — optional, swappable, never load-bearing.
 *
 * The AI's ONLY job: given a template's slots and their matra/character
 * constraints, propose which rhythmic cells (from the curated library)
 * fill each slot. It is never asked to do arithmetic and never trusted:
 * every proposal is re-counted by the engine validator. Failed proposals
 * are regenerated (max N retries) and then silently replaced by the
 * deterministic weighted selector.
 *
 * Primary backend: KonakolSwaraLLM (sgattup/KonakolSwaraLLM) served
 * locally via llama.cpp / LM Studio / Ollama (see MODEL_SETUP.md).
 * ===================================================================== */
(function (root) {
  'use strict';

  /* ---------------- prompt in KonakolSwaraLLM's trained format ---------------- */
  const KONAKOL_SYSTEM =
    'You are an expert in Carnatic classical music, specializing in Konakol (Solkattu) — ' +
    'the vocal recitation of rhythmic syllables — and Swara sequence composition. ' +
    'You can explain Tala theory, compose creative Konakol patterns, generate melodic ' +
    'Swara sequences, and teach the grammar of South Indian rhythm and melody.';

  function buildPrompt(req) {
    const { slots, nadaiName, talaName, kalai, targetDifficulty } = req;
    const lib = req.library.cells
      .filter((c) => c.function !== 'LANDING')
      .map((c) => `  ${c.notation} = ${c.matraCount} matra${c.matraCount > 1 ? 's' : ''} (${c.function.toLowerCase()}, ${c.character.toLowerCase()}, difficulty ${c.difficulty})`)
      .join('\n');

    const slotText = slots
      .map((s) => `  Slot ${s.id} (${s.label}): exactly ${s.matras} matras.`)
      .join('\n');

    const question =
      `Context: ${talaName} tala, ${nadaiName} nadai, ${kalai} kalai.\n\n` +
      `Available rhythm cells (matra counts are exact):\n${lib}\n\n` +
      `Task: build a korvai by choosing cells for each slot. Constraints:\n${slotText}\n` +
      `Aim for difficulty around ${targetDifficulty} of 5 and a musically pleasing mix.\n\n` +
      `RULES (obey exactly):\n` +
      `1. For each slot, output ONE LINE: "SLOT <id>: cell1 + cell2 + ..." using the cell notations exactly as listed.\n` +
      `2. The matra counts of the cells on each line MUST sum to exactly the slot's matras.\n` +
      `3. Use only the listed cells. No new syllables. No arithmetic in the answer.\n` +
      `4. Output the slot lines and nothing else.`;

    return `${KONAKOL_SYSTEM}\n\n### Question:\n${question}\n\n### Answer:\n`;
  }

  /* ---------------- lenient response parsing ---------------- */
  // Accepts "SLOT A: ta ki ta + ta ka di mi" or "A: takita, takadhimi" forms.
  function parseProposal(text, library) {
    const byNotation = {};
    library.cells.forEach((c) => { byNotation[c.notation.toLowerCase().replace(/[\s,]+/g, '')] = c; });
    const assignments = {};
    const lines = String(text || '').split(/\r?\n/);
    for (const line of lines) {
      const m = line.match(/^\s*(?:slot\s*)?([A-Za-z0-9_]+)\s*[:\-]\s*(.+)$/i);
      if (!m) continue;
      const slotId = m[1].toUpperCase();
      const rest = m[2];
      if (/^[a-z]\)/i.test(rest)) continue;
      const cells = [];
      const tokens = rest.split(/\+|,|·|\||;/).map((t) => t.trim()).filter(Boolean);
      for (const tok of tokens) {
        // strip trailing annotations like "(2 matras)"
        const clean = tok.replace(/\(.*?\)/g, '').replace(/["'“”]/g, '').trim();
        const key = clean.toLowerCase().replace(/[\s,×]+/g, '');
        // exact notation match, or match ignoring separators (takita → ta ki ta)
        let cell = byNotation[key] || byNotation[clean.toLowerCase().replace(/[\s]+/g, '')];
        if (!cell) {
          // try prefix match: "ta ki ta × 4" style repeats
          const repM = clean.match(/^(.*?)\s*[×x]\s*(\d+)$/i);
          if (repM) {
            const base = byNotation[repM[1].toLowerCase().replace(/[\s,]+/g, '')];
            if (base) { for (let i = 0; i < parseInt(repM[2], 10); i++) cells.push(base); continue; }
          }
        }
        if (cell) cells.push(cell);
        // unknown tokens are skipped; the validator will reject a wrong sum
      }
      if (cells.length) assignments[slotId] = cells;
    }
    return assignments;
  }

  /* ---------------- endpoint clients ---------------- */
  async function fetchWithTimeout(url, opts, ms) {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), ms || 30000);
    try {
      return await fetch(url, { ...opts, signal: ctrl.signal });
    } finally {
      clearTimeout(t);
    }
  }

  // llama.cpp server (llama-server): POST /completion {prompt, ...} → {content}
  async function callLlamaCpp(cfg, prompt, opts) {
    const res = await fetchWithTimeout(
      cfg.baseUrl.replace(/\/$/, '') + '/completion',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt,
          n_predict: cfg.maxTokens || 400,
          temperature: cfg.temperature != null ? cfg.temperature : 0.7,
          stop: ['### Question:', '</s>'],
        }),
      },
      cfg.timeoutMs || 45000
    );
    if (!res.ok) throw new Error('llama.cpp HTTP ' + res.status);
    const j = await res.json();
    return (j.content || j.response || '').trim();
  }

  // OpenAI-compatible: LM Studio, Ollama (/v1), vLLM, Groq etc.
  async function callOpenAi(cfg, prompt, opts) {
    const headers = { 'Content-Type': 'application/json' };
    if (cfg.apiKey) headers.Authorization = 'Bearer ' + cfg.apiKey;
    const body = {
      model: cfg.model || 'konakolswara-llm',
      messages: [
        { role: 'system', content: KONAKOL_SYSTEM },
        { role: 'user', content: prompt.replace(KONAKOL_SYSTEM, '').replace('### Question:\n', '').replace(/\n\n### Answer:\n?$/, '') },
      ],
      temperature: cfg.temperature != null ? cfg.temperature : 0.7,
      max_tokens: cfg.maxTokens || 400,
    };
    const res = await fetchWithTimeout(
      cfg.baseUrl.replace(/\/$/, '') + '/v1/chat/completions',
      { method: 'POST', headers, body: JSON.stringify(body) },
      cfg.timeoutMs || 45000
    );
    if (!res.ok) throw new Error('OpenAI-compatible HTTP ' + res.status);
    const j = await res.json();
    return ((j.choices && j.choices[0] && j.choices[0].message && j.choices[0].message.content) || '').trim();
  }

  /* ---------------- interface ---------------- */
  class RhythmicSelectorAI {
    // eslint-disable-next-line class-methods-use-this
    async proposeCells(/* request */) { throw new Error('not implemented'); }
  }

  /* Deterministic weighted selector — ALWAYS available, no network. */
  class DeterministicSelector extends RhythmicSelectorAI {
    constructor(engine, seed) { super(); this.engine = engine; this.seed = seed || 1; }
    async proposeCells(req) {
      const rng = this.engine.makeRng(this.seed);
      const out = {};
      for (const slot of req.slots) {
        const cells = this.engine.fillSegment(
          slot.matras, slot.constraints, req.library, req.nadaiId,
          req.maxDifficulty, req.targetDifficulty, rng
        );
        if (!cells) return { ok: false, error: 'deterministic fill failed for slot ' + slot.id, source: 'fallback' };
        out[slot.id] = cells;
      }
      return { ok: true, assignments: out, source: 'deterministic' };
    }
  }

  /* LLM-backed selector with validate → retry → fallback loop. */
  class LlmSelector extends RhythmicSelectorAI {
    constructor(engine, cfg, deterministic) {
      super();
      this.engine = engine;
      this.cfg = cfg || {};
      this.fallback = deterministic;
      this.log = [];
    }
    _log(level, msg) {
      this.log.push({ t: new Date().toISOString(), level, msg });
      if (this.cfg.onLog) this.cfg.onLog(level, msg);
    }
    async testConnection() {
      try {
        const r = await this._rawCall('Reply with exactly: OK');
        return { ok: true, sample: String(r).slice(0, 120) };
      } catch (e) {
        return { ok: false, error: String(e && e.message || e) };
      }
    }
    async _rawCall(userText) {
      const cfg = this.cfg;
      if (cfg.api === 'openai') return callOpenAi(cfg, userText);
      return callLlamaCpp(cfg, userText);
    }
    async proposeCells(req) {
      const prompt = buildPrompt(req);
      const maxRetries = this.cfg.maxRetries != null ? this.cfg.maxRetries : 2;
      let lastErr = '';
      for (let attempt = 1; attempt <= 1 + maxRetries; attempt++) {
        try {
          this._log('info', `asking ${this.cfg.api === 'openai' ? 'OpenAI-compatible' : 'llama.cpp'} endpoint (attempt ${attempt})`);
          const text = await this._rawCall(prompt);
          const assignments = parseProposal(text, req.library);
          const check = this.validateAssignments(assignments, req);
          if (check.ok) {
            this._log('ok', `model proposal passed the mathematical validator on attempt ${attempt}`);
            return { ok: true, assignments, source: 'ai', attempts: attempt, raw: text };
          }
          lastErr = check.errors.join('; ');
          this._log('warn', `proposal rejected by validator: ${lastErr} — regenerating`);
        } catch (e) {
          lastErr = String((e && e.message) || e);
          this._log('warn', `endpoint error: ${lastErr}`);
        }
      }
      this._log('warn', 'falling back to the deterministic selector (output stays mathematically exact)');
      const fb = await this.fallback.proposeCells(req);
      return { ...fb, aiError: lastErr, source: 'fallback' };
    }
    // Re-derive every count — the single source of truth is the engine.
    validateAssignments(assignments, req) {
      const errors = [];
      for (const slot of req.slots) {
        const cells = assignments[slot.id];
        if (!cells || !cells.length) { errors.push(`slot ${slot.id}: no cells proposed`); continue; }
        const sum = cells.reduce((s, c) => s + c.matraCount, 0);
        if (sum !== slot.matras) errors.push(`slot ${slot.id}: sums to ${sum}, needs ${slot.matras}`);
        for (const c of cells) {
          if (!c.usableNadais.includes(req.nadaiId)) errors.push(`slot ${slot.id}: cell ${c.notation} unusable in ${req.nadaiId}`);
          if (c.function === 'LANDING' || c.function === 'MACRO') errors.push(`slot ${slot.id}: cell ${c.notation} has forbidden function`);
        }
      }
      return { ok: errors.length === 0, errors };
    }
  }

  const ai = { RhythmicSelectorAI, DeterministicSelector, LlmSelector, buildPrompt, parseProposal };
  if (typeof module !== 'undefined' && module.exports) module.exports = ai;
  else root.KorvaiAI = ai;
})(typeof self !== 'undefined' ? self : this);
