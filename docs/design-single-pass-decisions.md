# Design — single-pass (Jev-style) decisions

Status: **idea / experiment. Nothing here is built.** Written 2026-09-26.

This records a suggestion from a viewer of the local-LLM (Ollama) demo video: *"This could go crazy
with an LLM outputting single-pass Jev-style decisions."* It captures what the term means, where it
would and would not help the companion, and the smallest mod change that would let a player opt in
without making it a dependency.

Everything marked **verified** was read out of the code on `mc/1.21.11`. Everything about Jev /
AnyJev comes from a web search on 2026-09-26 — the technique was only weeks old then, so re-check
the sources before building anything.

---

## 1. What a Jev-style decision is

A normal LLM call *generates* text (here, a JSON object) that the mod then parses. A Jev-style
decision model instead **picks one option from a fixed, typed set and returns a calibrated
probability for it**, in roughly a single forward pass — no free-text generation.

**AnyJev** (Nokia Applied Research, open source, September 2026) adds this to any open LLM
**without fine-tuning**. It has three levels, including an "L2" head trainable from only 100–300
labelled examples and served with a truncated forward pass. Reported result: Qwen3-32B with L1 hit an
expected calibration error of **0.036**, against **0.144** published for Jev itself.

Sources:
- [MarkTechPost — Nokia open-sources AnyJev](https://www.marktechpost.com/2026/09/23/nokia-open-sources-anyjev-a-training-free-layer-that-turns-any-open-llm-into-a-calibrated-decision-model/)
- [Medium — How to turn any LLM into Jev?](https://medium.com/data-science-in-your-pocket/how-to-turn-any-llm-into-jev-4e5793d12ddd)

### What it improves — and what it does not

| Claim | Verdict |
|---|---|
| Faster | **Yes, a lot.** One forward pass instead of generating `reason` + `command` + `message`. On a local model that is the difference between a visible pause and a near-instant reaction. |
| More *accurate* | **Not in the "reasons better" sense.** It gives up step-by-step reasoning, so a hard multi-step choice can come out *worse* than letting the model think first. |
| Calibrated | **Yes — the main quality win.** A 0.9 means about 90%, so the mod can act on thresholds ("act above 0.8, otherwise ask or do nothing"). |
| Consistent | **Yes.** Fixes the known effect where reordering the answer choices changes the pick. |
| Always parseable | **Yes.** The output is always one of the offered options — no malformed JSON, no invented commands. |

---

## 2. Where it fits the companion

It does not *hinder* chat or problem solving — it simply **cannot do them**. It is a separate mode
used alongside normal generation, not a replacement for it.

Nor is it for truly scripted behaviour: a fixed routine is plain code and needs no model at all. It
is for the **choice between** behaviours when the right one depends on fuzzy context:

**Good fit** — frequent, latency-sensitive, closed-set:
- A creeper is approaching and the owner is building: flee, attack, or warn?
- Was that chat line addressed to me?
- Low health mid-fight: keep fighting or retreat?
- Which command *verb* does this request map to?

**Bad fit** — keep full generation:
- Conversation (`message`)
- Planning and building
- Anything whose answer is not a small fixed set

The likely end state is a **hybrid**: fast single-pass decisions for moment-to-moment behaviour, full
generation for talking and planning, and a low-confidence decision escalating to the full model (or
to the player).

---

## 3. Why the mod cannot be driven by it unchanged — verified

The brain makes **one** call per turn and gets **one** reply carrying the decision and the speech
together:

- [`Player2APIService.completeConversation`](../engine/common/src/main/java/com/player2/playerengine/player2api/Player2APIService.java#L504)
  POSTs OpenAI-style `/v1/chat/completions`, with JSON mode on by default
  (`llm.useGrammar`, see `CompanionConfig`).
- [`Prompts.java`](../engine/common/src/main/java/com/player2/playerengine/player2api/Prompts.java#L63-L65)
  asks for `{ "reason", "command", "message" }` in that single reply. `reason` is the model's
  step-by-step thinking; `message` is what the companion says.
- The seam is documented in [brain-contract.md](brain-contract.md).

So a proxy sitting between the mod and the LLM would see a single "generate this JSON" request and
could not do much with it:

1. **There is no separate decision call to answer in one pass.** Decision and speech arrive together.
2. **The options exist only as prompt prose** (the "Valid Commands" list). A proxy would have to
   scrape them out of the prompt, which breaks whenever the prompt is edited.
3. **The confidence would be discarded** — nothing in the reply schema carries it.
4. **`command` is not a pure closed set.** The verb is (`get`, `follow`, `attack`, …) but the
   arguments are open (`get log 10`, `goto 120 64 -30`). A typed decision can pick the verb; the
   arguments still need generation or a second step.

---

## 4. Proposed architecture — optional middleware, never a dependency

Keep the Jev layer **out of the mod**. Requiring it would raise the bar to entry far too high: most
players run one model behind one endpoint and should keep doing so.

```
                        ┌───────────────────────────── normal path (unchanged) ─┐
 Companion brain ──────►│ llm.endpoint   → any OpenAI-compatible LLM            │
   (client or server)   └───────────────────────────────────────────────────────┘
        │
        │  only when llm.decisions.enabled = true
        ▼
 llm.decisions.endpoint → Jev middleware (separate process the player installs)
                              │  runs the model itself (llama.cpp / vLLM / …)
                              │  returns { choice, confidence }
                              └─ low confidence → forwards to full generation
```

### The middleware (not in this repo)

- A separate process the player chooses to install and run, pointed at their own model.
- Speaks the **same OpenAI-compatible request shape** the mod already sends, so the mod needs no new
  client code — only a second URL.
- Recognises a decision request by its **enum JSON schema** (§5) and answers it in a single pass,
  returning the chosen option plus a confidence.
- **Does its own escalation:** below a threshold it forwards the request to full generation and
  returns that answer instead. This works even before the mod learns to read the confidence.
- Anything that is not a decision request is passed through untouched.

### Hard limit: local models only

The middleware needs the model's **token probabilities**, and AnyJev's better mode needs **control of
the forward pass**. In practice it has to run the model itself:

- **llama.cpp, vLLM** — expected to work.
- **Ollama** — **unconfirmed.** Its API may not expose enough. Check before promising anything.
- **Hosted providers** — as understood on 2026-09-26 (not re-verified): OpenAI returns only the top
  ~20 token logprobs, Anthropic returns none. A middleware cannot sit in front of them the way it can
  in front of a local model.

So this is an **enthusiast option for local-model players**, not something that works with "any
provider."

---

## 5. The mod changes it would need

Small, optional, and invisible to anyone who does not turn it on.

### 5.1 A separate decision request

Split the closed-set part out of the combined turn into its own call whose options are sent as a
**JSON-schema enum**, not prose. The mod already builds `json_schema` response formats for the
LM Studio fallback (see `LocalServerQuirksTest`), so the plumbing exists.

Sketch of the request's `response_format`:

```json
{
  "type": "json_schema",
  "json_schema": {
    "name": "decision",
    "schema": {
      "type": "object",
      "properties": {
        "choice":     { "type": "string", "enum": ["flee", "attack", "warn", "ignore"] },
        "confidence": { "type": "number" }
      },
      "required": ["choice"]
    }
  }
}
```

**Important:** with the setting off, or the decision endpoint unset, this request goes to the
player's normal LLM. A plain model handles a schema-constrained enum fine — just slower, and its
`confidence` (if it fills one in) is not calibrated. That is what keeps the change safe to ship
by default.

### 5.2 Config — default off

New keys under the existing `llm` block:

| Key | Default | Meaning |
|---|---|---|
| `llm.decisions.enabled` | `false` | Master switch. Off = behaviour identical to today. |
| `llm.decisions.endpoint` | `""` | Middleware URL. Empty = use `llm.endpoint`. |
| `llm.decisions.minConfidence` | `0.8` | Below this, escalate to the full turn (only meaningful with a calibrated middleware). |

Remember: [config.example.json](config.example.json) is **generated** from
`CompanionConfig.DEFAULT_JSON` — add the keys (and their `_` help strings) there and regenerate; do
not hand-edit the example.

Wherever the brain runs (client via `LocalBrainTransport`, or server), the decision endpoint must
follow the same routing as `llm.endpoint`, so a player's middleware on their own machine is the one
used.

### 5.3 UI — LLM tab of the config screen

In `CompanionConfigScreen`'s LLM tab, alongside "JSON Mode":

- **Toggle** "Fast decisions (experimental)" — default **off**.
- **Text field** "Decision endpoint" — shown or enabled only when the toggle is on; placeholder
  text explains that empty means "use the main endpoint."
- A one-line hint: *"Needs a separate single-pass decision server running a local model. Leave off
  if you don't have one."*

---

## 6. Open questions

1. **Does Ollama expose enough** (full logprobs, or a way to run a truncated pass) for AnyJev? If not,
   the Ollama demo audience would need llama.cpp or vLLM instead.
2. **Which decisions to split out first?** The command verb is the obvious one, but its arguments
   are open-ended (§3.4). A narrower first target — "is this chat for me?" or a combat
   flee/fight/warn choice — may be cleaner.
3. **What do we lose by dropping `reason`?** Today the model reasons before choosing. Measure whether
   a single-pass verb pick matches the full turn's verb often enough.
4. **Does the split cost more than it saves** for players *without* the middleware? Two calls per
   turn to a plain LLM could be slower than one. The split may need to happen only when
   `llm.decisions.enabled` is on.
5. **Can the confidence reach the game?** e.g. a hesitant companion asking "did you mean me?" below
   threshold instead of acting.

---

## 7. A first experiment

Before any mod change, answer question 1 and get a number:

1. Stand up AnyJev (L1, no training) against a local model on llama.cpp.
2. Collect ~100 real turns from play logs: the prompt, plus the verb the full model chose.
   (Strip companion and player names before saving them anywhere shared.)
3. Replay each turn as a single-pass verb decision over the Valid Commands verbs.
4. Record: agreement with the full model's verb, latency per decision vs per full turn, and how
   agreement varies with confidence.

If agreement is high above a usable threshold and latency drops sharply, §5 is worth building. If
not, record the result here and park the idea.
