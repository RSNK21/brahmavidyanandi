# MODEL_SETUP.md — Running KonakolSwaraLLM locally (free, offline)

The Korvai Composer apps use **[sgattup/KonakolSwaraLLM](https://huggingface.co/sgattup/KonakolSwaraLLM)** (Apache 2.0, a Llama 3.2 3B QLoRA fine-tune for Carnatic Konakol/Solkattu) as their *optional* AI selector.

Two facts drive this setup:

1. The HF repo ships **only the LoRA adapter** (~97 MB) — it must be merged with the Llama 3.2 3B base and converted to GGUF before it can be served.
2. The model card itself disclaims **beat-count precision**. That is exactly why the app architecture is *"AI proposes, engine disposes"* — every AI proposal is re-counted by the deterministic validator, and the app works 100% without the model.

---

## Step 1 — Build the GGUF once (free Colab T4, ~20 min)

Open [`colab/build_konakolswara_gguf.ipynb`](colab/build_konakolswara_gguf.ipynb) on [colab.research.google.com](https://colab.research.google.com) (Runtime → GPU T4, free tier) and Run all.

You get `konakolswara-llm-Q4_K_M.gguf` (~2.0 GB). No account beyond Google is needed; nothing is uploaded anywhere.

## Step 2 — Serve it locally

### llama.cpp (recommended — what the web app expects by default)

```bash
# build once (or download a release binary)
git clone https://github.com/ggerganov/llama.cpp && cd llama.cpp
cmake -B build && cmake --build build -j

# serve (CORS is enabled by default, which the browser app needs)
./build/bin/llama-server -m /path/to/konakolswara-llm-Q4_K_M.gguf --host 127.0.0.1 --port 8080
```

App settings: **API = llama.cpp server**, **Base URL = `http://127.0.0.1:8080`**.

### LM Studio

Load the GGUF → *Local Server* → Start (default port 1234, enable CORS in server settings).

App settings: **API = OpenAI-compatible**, **Base URL = `http://127.0.0.1:1234`**, model = whatever LM Studio names it.

### Ollama

```bash
cat > Modelfile <<EOF
FROM /path/to/konakolswara-llm-Q4_K_M.gguf
EOF
ollama create konakolswara-llm -f Modelfile
# browser calls need CORS:
OLLAMA_ORIGINS='*' ollama serve     # OpenAI-compatible API at :11434
```

App settings: **API = OpenAI-compatible**, **Base URL = `http://127.0.0.1:11434`**, model `konakolswara-llm`.

### Android (on-device, fully offline)

Two options:

- **Termux + llama.cpp**: `pkg install git cmake clang && git clone https://github.com/ggerganov/llama.cpp && cd llama.cpp && cmake -B build && cmake --build build -j` then run `llama-server` exactly as above. The Android app reaches it at `http://127.0.0.1:8080` — same device, same loopback.
- **llama.cpp JNI binding** (the handoff doc's §6 option 1): bundle the GGUF in app assets / external storage and load it via `llama.cpp`'s Android bindings or MediaPipe LLM Inference API. The `RhythmicSelectorAI` interface in `android/app/src/main/java/com/korvai/app/ai/RhythmicSelectorAI.kt` is the single seam — add a `LocalLlamaSelector` beside `LlmSelector` and call the JNI API instead of HTTP. The prompt string is already in KonakolSwaraLLM's trained format (`LlmSelector.buildPrompt`).

> A 3B Q4_K_M needs ~2.3 GB RAM — fine on any recent phone, too heavy for very old devices. That's why AI stays optional and off by default.

## Step 3 — Verify in the app

1. Web: open `web/index.html` → **AI selector** tab → *Test endpoint* (expect `✓ endpoint reachable`).
2. Compose a korvai, press **✦ Compose with AI selector**. The activity log shows:
   - `asking llama.cpp endpoint (attempt 1)`
   - either `✓ model proposal passed the mathematical validator` — AI cells are used,
   - or `⚠ proposal rejected by validator: slot A: sums to 12, needs 13 — regenerating` → retry → `⚠ falling back to the deterministic selector (output stays mathematically exact)`.

The korvai you see is **always mathematically exact**, AI or not.

## Notes

- **Privacy**: prompts contain only tala/slot metadata and cell-library text — no user data (handoff §6).
- **Free-tier cloud fallback** (optional, never required): the OpenAI-compatible client also works with Groq/Gemini-style endpoints and a key you paste in. The apps never send anything unless you enable it.
- The model card lists a companion `sgattup/RagaLakshanaLLM` for raga theory — out of scope for this rhythm-focused app, but the same serving recipe applies if you extend it.
