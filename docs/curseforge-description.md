# AI Companion — CurseForge project description

> Paste-ready copy for the CurseForge project page. The **Summary** goes in the
> short "Summary" field; everything below the divider is the long description
> (CurseForge accepts Markdown). Trim sections to taste.

---

## Summary (short field)

A self-contained autonomous AI companion for Minecraft, driven by **your own** LLM — it lives in the world, has goals, navigates, gathers, crafts, builds on request, and fights. Runs fully local with llama.cpp, or point it at a hosted frontier model. **Singleplayer & LAN** (see notes); no data leaves your network unless you choose a cloud endpoint.

---

# AI Companion

A single dedicated **AI friend** for your Minecraft world — not a static NPC, but an **active participant**. It has objectives, tells friend from threat, **autonomously navigates**, gathers and crafts, **builds structures on request**, and fights alongside you. Its "voice and judgment" come from a large language model that **you** run — a **local llama.cpp** server on your own machine, or any hosted **OpenAI-compatible** API (e.g. xAI/Grok, OpenAI) if you'd rather trade privacy for frontier-model quality.

The LLM decides *what to do and what to say*. The heavy lifting — pathfinding, task execution, world interaction — lives in engine code and **keeps working even if the LLM is offline**.

## ⚠️ Scope — singleplayer and LAN, not public servers

This is an **alpha**, and it is built and tested for **singleplayer** and **Open to LAN** worlds played with people you trust. It is **not ready for a public multiplayer server**, and I'd rather tell you why than let you find out:

- **The companion is not a player, so land-claim and protection mods can't see it.** Claim mods hook player-specific block-break events; a companion is a `LivingEntity` and never fires them. On a protected server it could plausibly dig through claimed land.
- **One companion identity, server-wide.** Name and persona come from a single server config file, so every player would get the same character.
- **Replies go to everyone.** The companion's chat lines are sent to all online players regardless of distance.
- **Cost caps are per-server, not per-player.** One person's conversation spends everyone's budget.

Fixing these properly is the 1.0 milestone. Until then: singleplayer and trusted LAN. On a dedicated server the `/companion` command is op-gated, so it won't surprise you.

## ⚠️ Requirements — read this first

This mod **does nothing on its own.** It needs an **OpenAI-compatible chat-completions endpoint** to think. You provide one of:

- **Local (private, free):** a running [`llama.cpp`](https://github.com/ggml-org/llama.cpp) server (or any local OpenAI-compatible server) on your machine/LAN. Nothing leaves your network.
- **Cloud (frontier quality, paid):** a hosted OpenAI-compatible API and an API key (e.g. xAI/Grok). A per-session request cap is built in so a runaway loop can't rack up spend.

Plus: **Minecraft 1.20.1**, **Fabric Loader**, and **Fabric API**. The pathfinding/task engine is **bundled inside this jar** — you do **not** need a separate download for it.

> Voice output (the companion speaking aloud) is **optional** and needs a separate local TTS service (a small Kokoro Docker container). It is **not part of this download** — grab the `tts/` folder from the [GitHub repo](https://github.com/adevivo/ai-companion/tree/main/tts) and run `docker compose up -d`. Full steps in the repo's `tts/README.md`. See Configuration to enable it.

## Features

- **Autonomous agent, not a script** — mines, collects, crafts, engages in combat, and builds structures via a proven task engine (Automatone/Baritone pathfinding + an AltoClef-derived task layer).
- **Your brain, your rules** — one config file points the companion at a local llama.cpp server or a hosted frontier API. Swap between them anytime.
- **Survives the LLM going away** — navigation and task execution are engine-side; if the model is offline or slow, the companion doesn't freeze mid-world.
- **Persona & identity** — set the companion's name, description, and personality in config; the persona is injected into a hardened prompt scaffold (it shapes voice, it doesn't get to override safety structure).
- **Held tools & weapons** — the companion visibly wields tools/weapons in its main hand, and its held item is part of what the LLM "sees," so equips are confirmable.
- **Reliable command output** — tolerant JSON parsing with graceful fallback: a malformed model reply is spoken as chat instead of dropping the turn.
- **Know what you're spending** — the companion reports its running token usage (in / out / total) to chat and the log every 100k tokens, so a paid endpoint never surprises you. Optional hard caps and a chat trigger prefix are there if you want them; both are off by default.
- **Recall commands** — call a wandered-off companion back, or ask where it is.
- **Optional local voice** — route spoken lines to a local Kokoro TTS endpoint; only the companion's spoken `message` is ever voiced, never its reasoning or commands.

## Commands

| Command | What it does |
|---|---|
| `/companion spawn` | Spawn your companion into the world. |
| `/companion goto <x> <y> <z>` | Send it to specific coordinates. |
| `/companion come` | Recall it to you, interrupting its current task. |
| `/companion where` | Report its coordinates and distance from you. |
| `/companion despawn` | Remove it from the world (e.g. if it gets stuck). |

Beyond commands, just **talk to it in chat** — that's the primary way you direct it.

## Configuration

On first launch the mod writes `config/aicompanion.json` with documented defaults. Key settings:

- **Identity:** companion `name`, `description`, `persona`.
- **LLM:** `endpoint` (default `http://localhost:3030`), `model`, `temperature`, `maxTokens`, request `timeout`.
- **Cloud/frontier:** set `endpoint` to a hosted API and supply the key via the **`AICOMPANION_LLM_APIKEY` environment variable** (preferred) or the `apiKey` field. Worked example for xAI/Grok:

```json
"llm": {
  "endpoint": "https://api.x.ai",
  "model": "grok-4-1-fast-non-reasoning",
  "temperature": 0.7,
  "maxTokens": 200,
  "apiKey": "xai-your-key-here"
}
```

> `endpoint` is the **base URL only** — no trailing slash and no `/v1`; the mod appends
> `/v1/chat/completions` itself. And pick a **non-reasoning** model: reasoning models are slower and
> bill you for thinking tokens the companion never uses. Any other OpenAI-compatible provider works
> the same way.

- **Spend awareness:** `llm.usageReportEveryTokens` (default `100000`) prints a running token total to chat; `0` silences it. `llm.maxRequests` is a separate, opt-in *hard* cap that makes the companion stop responding once hit — leave it at `0` unless you want a hard stop.
- **Chat gating:** `behavior.triggerPrefix` (blank by default) makes the companion answer only messages starting with that prefix, so ambient chat costs nothing. `behavior.thinkThrottleSeconds` sets a minimum gap between LLM turns — messages inside the window are queued, not dropped.
- **Voice (optional):** enable TTS and point it at a local Kokoro endpoint (client-side playback).

## What's bundled

This download is **self-contained**. It nests the mod's forked **PlayerEngine** (pathfinding + task engine) inside its own jar — no separate engine download.

> **Do not** also install a standalone PlayerEngine jar in your `mods/` folder — two copies of the engine will collide on load.

## Credits & license

This mod is a fork/consumer of the excellent work of others, and is distributed under the **GNU LGPL-3.0** in keeping with its upstream. Full credit:

- **[PlayerEngine](https://github.com/Goodbird-git/PlayerEngine)** by Goodbird-git — **LGPL-3.0** — the framework this bundles and builds on.
- **Automatone**, a fork of **[Baritone](https://github.com/cabaletta/baritone)** (leijurv & contributors) — **LGPL-3.0** — pathfinding/navigation.
- The task engine descends from the **AltoClef** (`adris.altoclef`) lineage — mine/collect/craft/combat/build.
- Brain integration adapted the LLM seam from PlayerEngine's Player2 path to a local/OpenAI-compatible endpoint. The reference consumer **Player2NPC** was studied for structure only; **no** Player2NPC source is reused (it carries no license).

**Source code:** the complete source for this mod and its modified engine is available at
`https://github.com/adevivo/ai-companion` — required by, and provided under, the LGPL-3.0.

*Not affiliated with, or endorsed by, Mojang or Microsoft. "Minecraft" is a trademark of Mojang Synergies AB.*
