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

## Community / Support
Discord: [Join the AI Companion Discord](https://discord.gg/PAm4ZFsXX)


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
| **LLM request** | Now sends `model` / `temperature` / `max_tokens`; JSON mode (`response_format: json_object`, `llm.useGrammar`, **on by default**) to force valid command output. Without it, chatty models drift into bare prose mid-session — a measured run had 9 of 21 turns come back unparseable, which reads in-game as the companion narrating work it never starts. Scoped to the agent turn only — the build-DSL codegen and the memory summarizer want plain text, and forcing an object on them wraps their reply in one their parsers choke on. |
| **Command reliability** | Robust JSON parsing (strips code fences, extracts the outermost object, lenient reader), raw-response logging on parse failure, and a graceful fallback that speaks a non-JSON reply instead of dropping the turn. |
| **Equip** | Extended `equip` to wield **tools/weapons in the main hand** (new `HoldItemTask`), not just armor; the agent's held item is now part of its perceived status so the LLM can confirm equips. |
| **Frontier A/B testing** | Config lever to point at a hosted API (e.g. xAI/Grok) via a bearer API key (env-var preferred), while keeping Player2 cloud coupling off. |
| **Spend visibility** | Token usage is accumulated from the response `usage` object and reported to the owner (chat + log) every `llm.usageReportEveryTokens` tokens — informational, never blocking. `llm.maxRequests` remains as an opt-in *hard* per-session cap (default off). |
| **Chat gating** | `behavior.triggerPrefix` (blank = answer all nearby chat) and `behavior.thinkThrottleSeconds` (minimum gap between LLM turns; queued, not dropped) — both previously config-only, now actually wired. |
| **Recall & cleanup** | `/companion come` (recall to owner, interrupts the current task), `/companion where` (coordinates + distance), and `/companion despawn` (remove a stuck companion, and drop its conversation state so the manager doesn't leak). |
| **Stats readout** | `/companion stats` prints the companion's HP, food/saturation, worn armor, held items (with durability), and an aggregated inventory list to chat. |
| **Multiple companions** | A `companions` roster in config — each entry its own name, description, persona and skin — spawned by name with `/companion spawn <name>`, or edited in game via `/companion config` → Companions (a section per companion, plus an Add field). Identity used to be four statics, so a second spawn was an indistinguishable clone: same name in chat, and `findCompanion` returned the first owner-matching entity in arbitrary order, making every targeting command a coin flip. Now `come`/`goto`/`where`/`stats`/`despawn`/`skill` all take an optional name and say which companion they acted on, `/companion list` shows everyone with distance, health and current task, a bare `spawn` takes the first companion not already out, and spawning a duplicate by name is refused (checked across all worlds). The pre-roster `companion` block is retired — migrated into the list on first launch, with the original kept as `aicompanion.json.bak`. |
| **Borrowed skins** | `skin.username` on a roster entry takes any Minecraft player's skin, alongside the existing `skin.file` PNG (which wins if both are set). The name is resolved **once, on the server**, through the same vanilla path player heads use, and the resulting textures blob is pushed to clients as tracked data — so no client ever contacts Mojang, and nothing has to be installed per machine. That last part is the real gain: file skins are a client-side asset, so on a LAN every player needed their own copy of the PNG or saw default Steve. Arm width comes from the account rather than the config when a username is set, because the two would otherwise disagree. An unresolvable name (offline-mode server, no internet, a typo) caches as "no skin", logs once, and falls through to the file and then to Steve; `/companion reload` clears that cache so a corrected name takes effect without a restart. |
| **Addressing by name** | Chat used to fan out to every companion within 64 blocks, so two of them each spent a turn on every line. A message opening with a companion's name now goes to that one alone, with the name stripped before the model sees it; anything unaddressed goes to the nearest one only. Matching is prefix-only and requires a word boundary, so "Avalanche incoming" addresses nobody and "tell Ava I said hello" is not a message to Ava. |
| **Companion cross-talk** | `behavior.aiCrossTalk`, **off by default**. Companions used to overhear and answer each other, and each answer prompted another — a full LLM turn every time, with nobody talking to them. One session logged 382, including four near-identical sentences in ninety seconds. |
| **Inventory window** | Right-click a companion with an empty hand to open its inventory: 36 storage slots, 4 armour, 1 offhand, over your own. The only hands-on way to give it anything — items previously reached it only by being dropped for it to notice or fetched on request, and armour had no route at all despite being worn, damaged, and worn out. Shift-clicking armour or a shield lands it in the slot it belongs in. Owner-only, and the screen closes if the companion walks out of reach. |
| **Healing & eating** | The engine's hunger manager was never ticked, so an injured companion healed **never** — it sat at whatever health it was left with, while hunger read a permanent 20/20 because nothing consumed it either. Natural regeneration now runs every tick; hunger depletion stays off deliberately, so there is no starvation risk and no foraging pressure. Drop below 30% health and the owner gets one chat warning (re-arms after recovery past 60%). New `eat` command consumes food from the companion's own inventory on the spot — `food` gathers and could send it on an expedition mid-emergency, which is what used to happen when it was asked to eat. |
| **Structure templates** | Ordinary rectangular shapes (`house`/`hut`/`shelter`, crop `field`, `wall`/`path`/`bridge`) are now generated in Java instead of asked for. A matched build skips the ~4.2k-token codegen prompt, its output, and a round-trip; the matcher is server-side, so nothing had to be advertised to the model and there is no standing prompt cost. Anything the templates decline — an L-shape, multiple rooms, stairs — falls through to the DSL path unchanged, and logs the word that triggered the decline so the deny-list can be tuned from real sessions. Templates place full blocks only: doorways are air gaps and roofs are flat, because a plan cannot yet carry block states. |
| **Mortality** | A `LivingEntity` has no drop-on-death behaviour — that lives in `PlayerEntity`, and only for a player's own inventory — so a dead companion used to take everything it carried with it. It now drops its whole inventory (main, armor, offhand) where it fell and tells the owner the coordinates and stack count. `keepInventory` is deliberately ignored: for a player the rule moves items to the respawned body, but a companion has no respawn, so honouring it would keep the stacks on an entity that no longer exists. Death also releases the conversation state, which otherwise leaked one history per death. |
| **Radar HUD** | A client-side locator bar that points toward the companion so you can walk to it past entity-tracking range — the server pushes its position/health, so it works even when the entity isn't loaded. `/companion radar` (or an unbound keybind) cycles ON / AUTO / OFF; default ON, with cross-dimension and staleness handling. |
| **Token HUD** | A client-side panel (top-left) showing the session's running token spend — total, the in/out split, request count, and a 30-minute **tokens-per-minute** bar graph. Visible only while a companion is spawned and reporting in; hidden by F1 and while the F3 overlay is up. The server pushes cumulative totals once a second and the client derives the graph by diffing them, so nothing extra is tracked server-side. `/companion tokens` toggles the panel; default ON. |
| **Skills** | User-authored markdown procedures in `config/aicompanion/skills/*.md`, invoked with `/companion skill <name>` — the file's body is injected into that companion's LLM queue and run with its normal command loop. `/companion skills` lists them; names/descriptions are advertised in the persona so they can also be asked for in chat. Ships with four examples (lumberjack, home-guard, farming, harvest) and hot-reloads via `/companion reload`. |
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

### Command discipline
AltoClef expects the LLM to emit **parseable high-level commands**, so whatever model you pick has to
hold a strict output format turn after turn. That is the first thing to validate with a new brain, and
it is not a given — instruction-following matters far more here than raw parameter count.

**Qwen2.5-14B-Instruct-Q4_K_M handles it well** and is the recommended local default: capable enough for
reliable command discipline across extended testing, and small enough to run on consumer hardware. See
[The LLM backend](#the-llm-backend) for how to launch it. GBNF grammar / constrained output on
llama.cpp, plus a strict command schema and a repair step, keep output valid regardless of model.

**Model is swappable, not fixed.** The `LLMCompleter` is **provider-agnostic** (local llama.cpp / any
OpenAI-compatible endpoint / a frontier API), selected from config. Guidance: prefer **local** (free,
private, offline-resilient); **a better quant beats raw size** for command discipline. Cost levers if a
frontier model is used: throttled triggers, small situation packets, and prompt caching of the static
system/persona.

---

## The LLM backend

```bash
# macOS / Linux
llama-server \
  -m /path/to/models/Qwen2.5-14B-Instruct-Q4_K_M.gguf \
  --host 0.0.0.0 --port 3030 -c 8192 -ngl 40
```
```powershell
# Windows 11 (PowerShell) — backtick continues lines; binary is llama-server.exe
llama-server.exe `
  -m C:\path\to\models\Qwen2.5-14B-Instruct-Q4_K_M.gguf `
  --host 0.0.0.0 --port 3030 -c 8192 -ngl 40
```
Endpoint `http://localhost:3030` — OpenAI-compatible `/v1/chat/completions`, or native `/completion`
(supports **GBNF grammar**). **Qwen2.5-14B-Instruct-Q4_K_M is the recommended local model** — it holds
the command format reliably and runs on consumer hardware.

### ⚠️ Don't starve the game of VRAM and RAM

If you run the model on the **same machine you play on**, llama.cpp and Minecraft compete for the same
VRAM and system RAM — and when that competition is lost, what goes down is usually the whole computer,
not just the game. A hard freeze, a black screen, a driver reset, or a spontaneous reboot mid-session is
almost always this. A Minecraft mod can crash the *game*; it cannot crash your *PC*.

**Shader packs make it considerably more likely.** They want a large slice of VRAM themselves and they
allocate in bursts, so a single ordinary action — placing water, stepping into the Nether, loading new
chunks — can spike hard enough to tip a machine that was already at the edge. If the crash reliably
follows one specific action, that is the shape of this problem, not a bug in that feature.

Three knobs, in the order worth reaching for:

| Flag | What it does | Advice |
|---|---|---|
| `-c` | Context window — sets KV-cache size | **Start at `8192`.** Cost grows linearly with the number, so a very large context reserves *gigabytes* before a single token is generated. The companion sends small situation packets and does not need a big window. |
| `-ngl` | Number of layers offloaded to the GPU | **Lower it** to shift work onto CPU and system RAM, freeing VRAM for the game. `0` runs entirely on CPU — slower, but it will not contend with your GPU at all. |
| `--mlock` | Pins the model in physical RAM, preventing paging | **Leave it off** on a machine you also game on. It is a throughput optimisation for a dedicated inference box; on a shared machine it removes the OS's ability to relieve memory pressure, which is exactly what you need it to be able to do. |

**The reliable fix is not to share at all.** Point `llm.endpoint` at a **second machine on your LAN** —
`--host 0.0.0.0` already makes the server reachable from other hosts — or use a hosted endpoint (see
[Choosing a brain](#choosing-a-brain)). Either way your gaming rig keeps all of its VRAM, and you can run
a much larger model than the rig could have hosted itself.

If you have already crashed this way, expect `latest.log` to be empty or truncated — a hard hang never
gets the chance to flush it. On Windows, check **Event Viewer → Windows Logs → System** for a
*Kernel-Power event 41* (unclean shutdown) or a display-driver-reset entry timestamped at the crash.

---

## Configuration

The mod writes `config/aicompanion.json` inside your Minecraft instance folder on first launch, with
comments (`_help` keys) explaining every setting. Full schema:
**[docs/config.example.json](docs/config.example.json)**. Edit it and restart the game.

Sections: `companions[].{name,description,systemPrompt,skin{file,username,slim},voice}` ·
`llm.{endpoint,model,temperature,maxTokens,timeoutMs,useGrammar,apiKey,maxRequests,maxConcurrentRequests,maxPromptChars,usageReportEveryTokens}` ·
`tts.{enabled,endpoint,model,voice,speed}` ·
`behavior.{triggerPrefix,thinkThrottleSeconds,aiCrossTalk,buildCostsMaterials,buildGroundCheck,buildPhysicalPlacement,buildBlocksPerTick,maxAutonomousTurns,mobsTargetCompanion,defenseFightBack,defenseUseShield,defenseFleeFromHostiles,defenseBravery,autoEquipArmor,scavengeFood,scavengeRadius}` ·
`combat.{attackDamageBase,armorBase,maxHealth,followRange}` ·
`skills.{advertiseInPrompt}`.

### Choosing a brain

Only the `llm` block differs between a local model and a hosted one.

**Local — llama.cpp (free, private, default):**

```json
"llm": {
  "endpoint": "http://localhost:3030",
  "model": "local",
  "temperature": 0.7,
  "maxTokens": 200,
  "apiKey": ""
}
```

Start the server yourself (see [The LLM backend](#the-llm-backend) above). `model` is ignored by
llama.cpp — it serves whatever GGUF you loaded — and `apiKey` stays blank because there's no auth.

**Hosted — xAI / Grok (paid, much more capable):**

```json
"llm": {
  "endpoint": "https://api.x.ai",
  "model": "grok-4-1-fast-non-reasoning",
  "temperature": 0.7,
  "maxTokens": 200,
  "apiKey": "xai-your-key-here",
  "usageReportEveryTokens": 100000
}
```

Get a key from <https://console.x.ai>. Three things that will bite you if you skip them:

- **`endpoint` is the base URL only** — no trailing slash, no `/v1`. The mod appends
  `/v1/chat/completions` itself, so `https://api.x.ai/v1/` becomes a 404.
- **Use a *non-reasoning* model.** Reasoning models are slower and bill you for thinking tokens the
  companion never uses. `grok-4-1-fast-non-reasoning` is the sane default.
- **Prefer the environment variable to the config file** for the key —
  set `AICOMPANION_LLM_APIKEY` and leave `apiKey` blank, and the secret never touches disk. The env
  var wins when both are set.

Any other OpenAI-compatible provider works the same way: base URL, model id, key.

### Keeping an eye on cost

Whenever a companion is spawned, a small panel in the **top-left** shows the session's token spend
live: the running total, the input/output split, the request count, and a bar graph of
**tokens per minute** over the last 30 minutes. The graph is the useful part — a companion answering
the occasional question looks nothing like one stuck in a think loop, and you'll see the difference
within a minute instead of at the next milestone report. It disappears when the companion despawns,
and F1 hides it with the rest of the HUD. `/companion tokens` turns the panel off and on if you'd
rather not have it there; it's on by default and the setting lasts for the session.

Behind it, the companion also reports its running token usage to chat and the log every
`llm.usageReportEveryTokens` tokens (default `100000`; `0` silences it). Two optional brakes, both
off by default:

| Setting | Effect |
|---|---|
| `behavior.triggerPrefix` | Set to e.g. `"@"` and only messages starting with it reach the model — ambient chat becomes free. Blank = it answers everything nearby. |
| `behavior.thinkThrottleSeconds` | Minimum gap between LLM turns. Messages arriving inside the window are queued and folded into the next turn, not dropped. |
| `behavior.buildCostsMaterials` | `true` (default): `build_structure` spends real items from the companion's inventory, one per block. If it is short, it **collects the shortfall itself** and then builds — one command does the whole job, rather than bouncing back to the model to fetch one item per turn. Blocks already correct are skipped and cost nothing. Only if gathering fails does it refuse and report what is still missing. `false` restores creative-style building where blocks come from nothing. |
| `behavior.buildGroundCheck` | `true` (default): a build plan is compared against the real terrain before any block is placed. One-sided by design — a plan that came out **below** ground is lifted onto the surface (up to 3 blocks), since buried is never intended and is invisible once it happens; a plan **above** ground is built exactly as generated, since "on top of the ground" is a one-block gap and towers are legitimately higher. Only a plan more than 16 blocks up is refused, with **no materials spent** and the correct ground Y reported back. Set `false` to disable the check entirely. |
| `behavior.maxAutonomousTurns` | `2` (default): how many actions the companion may take on its own initiative after finishing what you asked, before it waits to be spoken to again. Every finished command prompts it for a next step, so without a cap one instruction chains indefinitely — and it starts inventing chores. The counter resets whenever anybody talks to it, and whenever a command **fails**, so gather-then-retry loops still run to completion. `0` = unlimited (the old behaviour). |
| `behavior.buildPhysicalPlacement` | `true` (default): the companion **walks to the build site and builds with its hands** — a couple of blocks per tick, only ones it can actually reach, choosing somewhere to stand that the plan does not need to fill and that it can get back out of, moving along as each spot is exhausted, with an arm swing and a placement sound. Air cells in a plan (doorways, windows) are carved first, which is what keeps it from sealing itself inside a house cut into a hillside. Blocks are still written directly rather than right-clicked, so orientation-sensitive blocks behave as before. A build it cannot finish reaching reports honestly that it is **partly** built, and repeating the same description resumes it rather than starting over. `false` restores the old instant path: up to 256 blocks in a single tick, no reach check, no walking, from anywhere on the map. |
| `behavior.buildBlocksPerTick` | `2` (default, clamped 1–64): pacing when the above is on. Raise it to finish large builds sooner, at the cost of blocks appearing in visible clumps. Prefer raising this over turning physical placement off. |
| `behavior.mobsTargetCompanion` | `true` (default): **hostile mobs hunt the companion the way they hunt you.** It is a `LivingEntity` rather than a real player and vanilla mobs look for targets with a hard-coded player filter, so with this off they walk straight past it and it is only ever attacked in retaliation for swinging first. Covers the goal-based hostiles (zombies, skeletons, spiders, creepers, illagers, blazes, ghasts, slimes, guardians…) plus piglins and hoglins. Endermen keep player-only stare aggro either way. |
| `behavior.defenseFightBack` | `true` (default): whether the companion deliberately engages hostiles that are targeting it. It always swings at whatever is already in arm's reach regardless of this setting. |
| `behavior.defenseUseShield` | `true` (default): whether it raises a shield when threatened. Note: shield **durability** is not yet wired for a non-player entity, so a raised shield does not wear out. |
| `behavior.defenseFleeFromHostiles` | **`false`** (default): whether it may run away from fights it judges it cannot win, dodge arrows, and throw up cover blocks. All of that logic hangs off "is a mob targeting the companion", which was impossible before `mobsTargetCompanion`, so it has never actually run in a real world. Off, the companion stands its ground and keeps working; on, expect it to abandon a farm or a half-built house to sprint over the horizon. Turn it on deliberately, after watching it get mobbed. |
| `llm.maxRequests` | Hard per-session request cap. Once hit the companion stops responding until restart — a stop, not a throttle. `0` = unlimited. |

---

## Voice output (optional)

The companion can **speak its chat lines** aloud, through a small local
[Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) text-to-speech service you run yourself with Docker.
Everything else works without it.

> **Where is `tts/`?** In the mod's own config directory. The jar carries the setup files and unpacks them
> to `config/aicompanion/tts/` (`docker-compose.yml` + `README.md`) on first launch, so a CurseForge install
> needs nothing from this repo. The copy here is the source of truth that gets bundled.

1. **Install Docker Desktop** (Windows 11 and macOS): <https://www.docker.com/products/docker-desktop/>
   — new to Docker? See the [getting-started guide](https://docs.docker.com/get-started/).
2. **Start the TTS server** — same command on every OS:
   ```bash
   cd config/aicompanion/tts
   docker compose up -d      # first run pulls ~2GB + model weights
   ```

That's it — `tts.enabled` is **on by default** and companions start speaking as soon as the container
answers. No config edit and no restart, which matters on a dedicated server where the settings screen
can't reach the server's config file.

Until a container is running, voice costs nothing: the client reports back that it has nowhere to play
audio, and the server stops sending it speech for five minutes (`/companion reload` retries immediately).
Set `tts.enabled` to `false` to switch it off for good.

> **The container belongs on the player's machine, not the server's.** The server only sends the text and
> the endpoint; the client fetches and plays the audio, so `http://localhost:8880` has to resolve *there*.
> On a dedicated server, either each player runs their own container, or you point `tts.endpoint` at one
> box everybody can reach. The settings themselves are read from the **server's** config either way.

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

**JDK 17 is required** — Java 25 fails with `Unsupported class file major version 69`.

The engine jar version comes from `engine/gradle.properties` (currently **1.0.46**). The commands below
glob it rather than naming it, and **clear both output directories first** — the consumer depends on
`PlayerEngine:+`, so a stale jar left alongside a new one is picked up silently, and a rebuilt jar
carrying the *same* version is served from Loom's remap cache. If a change to engine code does not seem
to take effect, bump `mod_version` in `engine/gradle.properties`.

Clearing `engine/build/libs/` matters as much as clearing `aicompanion/libs/`: `gradlew build` does not
remove jars from previous versions, so the copy glob will happily carry an old one forward alongside the
new one and leave the consumer resolving between two.

```bash
# macOS / Linux
export JAVA_HOME=/opt/homebrew/opt/openjdk@17          # or your JDK 17 path

# 1. Build the engine (our PlayerEngine fork) and stage its jar for the consumer
cd engine && rm -f build/libs/*.jar && ./gradlew build \
  && rm -f ../aicompanion/libs/PlayerEngine-*.jar \
  && cp build/libs/PlayerEngine-*.jar ../aicompanion/libs/ && cd ..

# 2. Build the companion mod (depends on the staged engine jar)
cd aicompanion && ./gradlew build      # → build/libs/*.jar
```
```powershell
# Windows 11 (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"   # adjust to your JDK 17 install

# 1. Build the engine fork and stage its jar for the consumer
cd engine; Remove-Item build\libs\*.jar -ErrorAction SilentlyContinue; .\gradlew.bat build; Remove-Item ..\aicompanion\libs\PlayerEngine-*.jar -ErrorAction SilentlyContinue; Copy-Item build\libs\PlayerEngine-*.jar ..\aicompanion\libs\; cd ..

# 2. Build the companion mod (depends on the staged engine jar)
cd aicompanion; .\gradlew.bat build      # -> build\libs\*.jar
```

> On every engine change, bump `mod_version` in `engine/gradle.properties` and restage the jar — this
> defeats Loom's flatDir remap cache, which will otherwise silently reuse the old engine.

## Status

**Alpha — singleplayer and trusted LAN only.** The companion spawns, renders (with skins and held
items), and is driven by a local llama.cpp brain through a hardened prompt with robust JSON command
parsing. A frontier A/B lever, running token-usage reporting, an opt-in request cap, and chat gating
(`behavior.triggerPrefix` / `thinkThrottleSeconds`) are in place for paid endpoints. Voice output
(local Kokoro TTS) is wired — see **[tts/](tts/)**. Quality-of-life additions: a `/companion stats`
readout, a client radar HUD that locates the companion past tracking range, a live token-usage HUD
with a per-minute burn graph, and a **skills** system — markdown procedures in
`config/aicompanion/skills/` invoked with `/companion skill <name>`.

**Not ready for a public multiplayer server**, and deliberately so:

- The companion is a `LivingEntity`, not a player, so land-claim mods that hook player block-break
  events can't see it — it could plausibly dig through claimed land.
- Companion identity is a single server-global config block, so every player would share one persona.
- The companion's chat lines are broadcast to all players regardless of distance.
- The request cap is a process-wide counter, not per-player.

These are the 1.0 milestone. On a dedicated server `/companion` is op-gated, so nothing surprises you
in the meantime.
