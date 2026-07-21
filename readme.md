# AI Companion — an autonomous, LLM-driven Minecraft agent

A single dedicated **AI friend** that is an **active participant** in the world: it has objectives,
recognizes friend vs. threat, **autonomously navigates**, interacts, and **builds things on request** —
driven by a **local llama.cpp** model, on your own server, with no external AI service.

We are **not building this from scratch.** We fork an existing, proven autonomous-agent framework and
replace its cloud brain with our local one.

> Voice output setup: **[tts/README.md](tts/README.md)**.

---

## Project description

A single, dedicated **AI companion** for Minecraft that navigates, gathers, crafts, builds, and fights
autonomously, driven by a **local LLM** (llama.cpp) on your own server — with no external AI dependency.
Optionally pointable at a hosted OpenAI-compatible API (e.g. xAI/Grok) for quality comparison.

### Origin

**Forked from:** [PlayerEngine](https://github.com/Goodbird-git/PlayerEngine) (Goodbird-git, **LGPL-3.0**) —
a Minecraft **1.20.1 · Fabric** framework that grants any `LivingEntity` player-like abilities by combining:

- **Automatone** — a fork of the **Baritone** pathfinding engine (navigation)
- **AltoClef** (`adris.altoclef`) — a task/agent engine (mine, collect, craft, combat, build structures)

Upstream, the "brain" that decided what the agent should do was the **Player2 cloud API**. The reference
consumer glue was **Player2NPC** (no license — studied for structure, **not** copied). The exact upstream
commit we forked from is recorded in `engine/UPSTREAM_FORK.txt`.

### What this project is

We **replace the Player2 cloud brain with a local, OpenAI-compatible LLM** and provide our own companion
mod. The LLM is the *voice + high-level policy*; navigation, task execution, and world interaction stay in
engine code and survive the LLM being offline.

```
player chat ─► ConversationManager ─► LLM (local llama.cpp / any OpenAI-compatible endpoint)
                                          │  emits {reason, command, message} JSON
                                          ▼
                              AltoClefController ─► AltoClef Task ─► Automatone/Baritone
```

### Changes we've made vs. upstream

| Area | Change |
|---|---|
| **Brain swap** | Routed the LLM seam to a configurable OpenAI-compatible endpoint (local llama.cpp by default); disabled Player2 device-auth, heartbeat, and cloud TTS in local mode. |
| **Companion entity** | Our own `CompanionEntity` (LivingEntity + PlayerEngine capability interfaces), spawn command, Baritone navigation, and a held-item render layer so tools/weapons show in-hand. |
| **Config / identity** | `config/aicompanion.json` drives companion name, description, and persona, plus all LLM settings (endpoint, model, temperature, maxTokens, timeout). Persona is injected into the engine's hardened prompt scaffold, not a replacement for it. |
| **LLM request** | Now sends `model` / `temperature` / `max_tokens`; optional JSON mode (`response_format: json_object`) to force valid command output. |
| **Command reliability** | Robust JSON parsing (strips code fences, extracts the outermost object, lenient reader), raw-response logging on parse failure, and a graceful fallback that speaks a non-JSON reply instead of dropping the turn. |
| **Equip** | Extended `equip` to wield **tools/weapons in the main hand** (new `HoldItemTask`), not just armor; the agent's held item is now part of its perceived status so the LLM can confirm equips. |
| **Frontier A/B testing** | Config lever to point at a hosted API (e.g. xAI/Grok) via a bearer API key (env-var preferred), while keeping Player2 cloud coupling off. |
| **Cost guardrail** | `llm.maxRequests` — a hard per-session request cap that fails fast before any HTTP call, so a runaway loop can't rack up spend on a paid endpoint. |
| **Recall** | `/companion come` (recall to owner, interrupts the current task) and `/companion where` (report coordinates + distance) for a companion that wandered off. |
| **Voice output** | Repointed the engine's TTS path from Player2 cloud to a **local Kokoro** OpenAI-compatible endpoint. Audio is still fetched and played **client-side**; only the `message` field is ever voiced. See **[tts/](tts/)**. |

### Licensing

PlayerEngine is LGPL-3.0: we may fork and modify it (our modified engine stays LGPL). Our consumer mod
links against it, which LGPL permits. Player2NPC has no license, so none of its source is reused — our
entity/spawn/render glue is written independently, informed only by public/vanilla Minecraft APIs.

---

## Foundation (decided via spike)

| Layer | Choice |
|---|---|
| Framework | **[PlayerEngine](https://github.com/Goodbird-git/PlayerEngine)** (LGPL-3.0) — grants any `LivingEntity` player-like abilities |
| Navigation | **Automatone** — a fork of the **Baritone** pathfinding engine |
| Task/agent engine | **AltoClef** (`adris.altoclef`) — mine, collect, craft, combat, **build structures** |
| Reference glue | **[Player2NPC](https://github.com/Goodbird-git/Player2NPC)** (⚠️ **no license** — study, don't copy) |
| Minecraft / loader | **1.20.1 · Fabric** (what the framework targets → zero porting) |
| Brain | **llama.cpp** (local), replacing the framework's external Player2 API |

**Legal:** PlayerEngine is LGPL-3.0 — we may fork and modify it (our modified PlayerEngine stays LGPL;
our own glue mod links against it, which LGPL permits). Player2NPC has no license file, so we do **not**
reuse its source — we write our own equivalent glue, informed by its structure.

---

## Architecture (verified from source)

```
player chat  ──►  ConversationManager        (chat hook, distance-based awareness, greetings, memory)
                     │
                     ▼
              LLMCompleter / Player2APIService   ◄──  *** THE ONE PIECE WE REPLACE ***
                     │                                (was: https://api.player2.game + local auth
                     │                                 now: http://localhost:3030  llama.cpp)
                     ▼
              high-level command  (e.g. "get oak_log 10", "@build ...")
                     │
                     ▼
              AltoClefController  ──►  AltoClef Task  ──►  Automatone/Baritone
              (mine / collect / craft / combat / BuildStructureTask / navigate / interact)
```

The companion entity is `AutomatoneEntity extends LivingEntity implements IAutomatone,
IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider` — PlayerEngine's
"player abilities on a mob" pattern. A per-player `CompanionManager` (Cardinal Component)
summons/tracks it and persists to NBT.

**Design principle still holds** (from the notes): the LLM is the **voice + high-level policy**;
navigation, task execution, and world interaction stay in engine code and must survive the LLM being offline.

---

## The brain swap (our actual work)

1. **New `LLMCompleter`** → `POST http://localhost:3030/v1/chat/completions` (llama.cpp, OpenAI-compatible).
2. **Strip** Player2-service pieces: `auth` (127.0.0.1:4315), `TTSManager`, `HeartbeatManager`, `AudioUtils`.
3. **Keep** `ConversationManager` orchestration + the AltoClef task engine + Automatone navigation.
4. **Add config** (see below) and our own thin entity/spawn glue.

### ⚠️ Primary risk — command discipline
AltoClef expects the LLM to emit **parseable high-level commands**. In testing,
**Qwen2.5-14B-Instruct-Q4_K_M** proved **not very capable** at strict command/JSON output — it is
*not* the local model to use; reliable command discipline needs a **much more capable** local model.
**Mitigate with GBNF grammar / constrained output on llama.cpp + a strict command schema + a repair
step.** This must be validated *first* (Phase 2).

**Model is swappable, not fixed.** The `LLMCompleter` is **provider-agnostic** (local llama.cpp / any
OpenAI-compatible endpoint / optionally a frontier API), selected from config. So model choice is deferred
to **empirical Phase-2 testing**, not committed now. Guidance: prefer **local** (free, private, offline-resilient);
for command discipline, **instruction-following + a better quant beats raw size**, and GBNF *forces* valid
output regardless of model. Cost levers if a frontier model is used: throttled triggers, small situation
packets, and prompt caching of the static system/persona.

---

## The LLM backend

```bash
# macOS / Linux
llama-server \
  -m /path/to/models/Qwen2.5-14B-Instruct-Q4_K_M.gguf \
  --host 0.0.0.0 --port 3030 -c 262144 -ngl 40 --mlock
```
```powershell
# Windows 11 (PowerShell) — backtick continues lines; binary is llama-server.exe
llama-server.exe `
  -m C:\path\to\models\Qwen2.5-14B-Instruct-Q4_K_M.gguf `
  --host 0.0.0.0 --port 3030 -c 262144 -ngl 40 --mlock
```
Endpoint `http://localhost:3030` — OpenAI-compatible `/v1/chat/completions`, or native `/completion`
(supports **GBNF grammar**). Note: **Qwen2.5-14B-Instruct-Q4_K_M was not capable enough** for reliable
command discipline — treat it as a floor, not a recommendation; a **much more capable local model** is needed.

---

## Configuration (sysadmin-editable)

A config file (default practical, override freely) — see **[docs/config.example.json](docs/config.example.json)**:
`companion.name`, `companion.skin` (username → Mojang skin, or file), `companion.description`,
`companion.systemPrompt` (defaults sensibly), and `llm.endpoint/model/temperature/timeout`,
`behavior.triggerPrefix/thinkThrottle`.

---

## Voice output (optional)

The companion can **speak its chat lines** aloud. This is **optional and not bundled** in the mod — it's a
small local [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) text-to-speech service you run yourself with
Docker. Voice is **off by default**; everything else works without it.

> **Where is `tts/`?** It ships in the **source repo**, *not* in the mod jar you install. If you got the
> mod from CurseForge, grab the [`tts/` folder from GitHub](https://github.com/adevivo/ai-companion/tree/main/tts)
> (you only need its `docker-compose.yml`) to run voice.

1. **Install Docker Desktop** (Windows 11 and macOS): <https://www.docker.com/products/docker-desktop/>
   — new to Docker? See the [getting-started guide](https://docs.docker.com/get-started/).
2. **Start the TTS server** from the `tts/` directory — same command on every OS:
   ```bash
   cd tts
   docker compose up -d      # first run pulls ~2GB + model weights
   ```
3. **Enable it** in `config/aicompanion.json` (`"tts": { "enabled": true, … }`) and restart the game.

Full setup, the 68-voice list, remote-server notes, and troubleshooting live in **[tts/README.md](tts/README.md)**.

---

## Repo layout

```
ai-companion/
├── readme.md                 ← this file
├── LICENSE                   ← LGPL-3.0 (+ LICENSE.GPL-3.0.txt)
├── engine/                   ← LIBRARY: our fork of PlayerEngine (LGPL-3.0); the local-LLM brain lives here
│   └── UPSTREAM_FORK.txt      ← upstream commit we forked from
├── aicompanion/              ← OUR MOD: the companion (entity/spawn/config/skin); bundles the engine jar
├── tts/                      ← optional local voice output: Kokoro docker-compose stack + guide
└── docs/
    ├── config.example.json   ← config schema
    ├── curseforge-description.md  ← CurseForge page copy
    └── branding/             ← project icon
```

Structure mirrors the proven upstream pattern: **`engine/`** is the framework library (Automatone +
AltoClef; we modify its brain), and **`aicompanion/`** is a consumer mod that depends on the engine jar
and defines the entity/spawn/config — exactly how Player2NPC consumes PlayerEngine.

## Building

**JDK 17 is required** — Java 25 fails with `Unsupported class file major version 69`. The jar version
comes from `engine/gradle.properties` (currently **1.0.11**); adjust the filename if you bump it.

```bash
# macOS / Linux
export JAVA_HOME=/opt/homebrew/opt/openjdk@17          # or your JDK 17 path

# 1. Build the engine (our PlayerEngine fork) and stage its jar for the consumer
cd engine && ./gradlew build && cp build/libs/PlayerEngine-1.0.11.jar ../aicompanion/libs/ && cd ..

# 2. Build the companion mod (depends on the staged engine jar)
cd aicompanion && ./gradlew build      # → build/libs/*.jar
```
```powershell
# Windows 11 (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"   # adjust to your JDK 17 install

# 1. Build the engine fork and stage its jar for the consumer
cd engine; .\gradlew.bat build; Copy-Item build\libs\PlayerEngine-1.0.11.jar ..\aicompanion\libs\; cd ..

# 2. Build the companion mod (depends on the staged engine jar)
cd aicompanion; .\gradlew.bat build      # -> build\libs\*.jar
```

> On every engine change, bump `mod_version` in `engine/gradle.properties` and restage the jar — this
> defeats Loom's flatDir remap cache, which will otherwise silently reuse the old engine.

## Status
Phases 0–3 are done: the companion is spawned, rendered (with skins and held items), and driven by a
local llama.cpp brain through a hardened prompt with robust JSON command parsing. A frontier A/B lever
and a per-session request cap are in place for paid-endpoint testing. Voice output (local Kokoro TTS)
is wired — see **[tts/](tts/)**.
