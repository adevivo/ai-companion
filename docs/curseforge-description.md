# AI Companion — CurseForge project description

> Paste-ready copy for the CurseForge project page. The **Summary** goes in the short "Summary"
> field; everything below the second divider is the long description (CurseForge accepts Markdown).
> Rewritten for 0.4.0 on 2026-09-26 to cover both lines: 0.4.0 for Minecraft 1.21.11 and 0.2.9 for
> 1.20.1. Release notes for the file itself are in `release-notes-0.4.0.md`.

---

## Summary (short field)

CurseForge caps this field at 256 characters and shows it as plain text (no Markdown). The line
below is 212 characters.

Autonomous AI companions driven by your own LLM. They gather, craft, build, fight, guard you and remember what you tell them. Run the model locally (llama.cpp, Ollama, LM Studio) or use any OpenAI-compatible API.

---

# AI Companion

**Companions that play alongside you, not scripted NPCs.** Tell one what you need in plain chat, and she works out how to do it: she gathers and crafts, builds on request, and fights beside you or guards you. Her voice and judgement come from a language model **you** choose. That can be one running privately on your own machine, or a hosted API if you'd rather have a frontier model.

The model decides *what* to do and *what to say*. Walking, mining, crafting and fighting are handled by the game engine, so a slow or offline model never leaves her frozen mid-task.

## Which version?

| Minecraft | Mod version | Notes |
|---|---|---|
| **1.21.11** | **0.4.0** | Current. Memory, multiplayer for everyone, bodyguard, chests. |
| 1.20.1 | 0.2.9 | Older line. Singleplayer and trusted LAN. |

## What she can do

- **Real work.** She mines, collects, crafts, hunts, farms, fishes and builds. Builds are paid for from her own inventory, block by block, and she gathers what's missing.
- **Fights like a player.** She wears the best armour you give her, uses a shield, eats when she's hungry and drops her gear if she dies. She has a player's strength, not a monster's.
- **Bodyguard** *(1.21.11)*. "Protect me" keeps her at your side, taking on anything that comes for you, creepers first.
- **Remembers you** *(1.21.11, optional)*. Facts you tell her carry over to later sessions, other worlds and servers.
- **Chests, with permission** *(1.21.11)*. She uses only the chests you've opened yourself or told her to use.
- **More than one.** Keep a roster, each companion with its own name, personality, skin (any Minecraft username, or your own PNG) and voice.
- **Skills.** Ready-made routines (lumberjack, farming, fishing, harvest, bodyguard, staircase mine, and more) are plain Markdown files you can edit or add to.
- **See how it's going.** On-screen panels show her health and hunger, where she is, and how many tokens you're spending.

## Talking to her

Just chat. `Ava, get me 20 logs` reaches Ava. `all: back to base` reaches every companion in earshot. With only one companion out, you don't need a name at all. In multiplayer she only picks up lines with her name in them, plus your follow-ups within 30 seconds of talking to her.

| Command | What it does |
|---|---|
| `/companion spawn [name]` · `despawn [name]` | Bring a companion out, or put one away |
| `/companion come [name]` · `where [name]` · `goto x y z` | Recall her, find her, or send her somewhere |
| `/companion stats [name]` · `list` | Her health, gear and inventory, or your roster |
| `/companion skill <skill>` · `skills` | Run a skill, or list them |
| `/companion remember <fact>` · `rememberhere <fact>` | Tell her something about you, or about this world |
| `/companion hud` · `radar` · `tokens` | Toggle the on-screen panels |
| `/companion config` · `reload` | Open settings, or apply a hand-edited config file |

## You bring the brain

The mod needs an **OpenAI-compatible chat endpoint** to think with:

- **Local, private and free:** [llama.cpp](https://github.com/ggml-org/llama.cpp), [Ollama](https://ollama.com), LM Studio or similar, on your own machine or LAN. Use a non-thinking instruct model; Qwen2.5 7B–14B works well.
- **Hosted:** any OpenAI-compatible API with your own key (OpenRouter, xAI, OpenAI…). Most of each request is cached, which keeps turns cheap, and an optional hard cap stops runaway spending.

**Memory** (optional, off by default) also needs an embedding model. The easy route is Ollama with `nomic-embed-text`.

**Voice** (optional) uses a small Kokoro container. On first launch the mod writes a ready `docker-compose.yml` into `config/aicompanion/tts/`; start it and your companions speak, each in her own voice.

> **Hardware note:** a local model on the same PC competes with Minecraft for memory. Overdo it (a huge context, every layer on the GPU) and you can freeze the whole machine, not just the game. Start small, or run the model on another computer on your LAN.

## Requirements

**1.21.11:** Fabric Loader 0.17.2+, Fabric API, **Architectury API**. Optional: Cloth Config and Mod Menu (for the settings screen; without them, `/aicompanion config` edits settings from chat).
**1.20.1:** Fabric Loader, Fabric API.

The pathfinding and task engine is bundled inside the jar. **Don't** install a separate PlayerEngine. Fabric only; Forge through Sinytra Connector isn't supported.

## Multiplayer and servers *(1.21.11)*

- **Everyone brings their own.** Each player's companions, model, API key and memories live on their own computer, and the server never pays for anyone's model.
- **Companions answer only their owner** and can't be commanded by other players. They're put away when their owner logs off.
- **Server settings** live in `config/aicompanion-server.json`: per-player companion limits, who may use the commands, and more.
- ⚠️ **Land-claim mods can't see companions.** Companions aren't players, so on a protected server one could break blocks inside a claim. Bear that in mind on public servers.

## Credits and license

Distributed under the **GNU LGPL-3.0**, in keeping with the work it builds on:

- **[PlayerEngine](https://github.com/Goodbird-git/PlayerEngine)** by Goodbird-git (LGPL-3.0): the framework this bundles and builds on.
- **Automatone**, a fork of **[Baritone](https://github.com/cabaletta/baritone)** by leijurv and contributors (LGPL-3.0): pathfinding.
- The task engine descends from **AltoClef**.

**Source:** [github.com/adevivo/ai-companion](https://github.com/adevivo/ai-companion) (branch `mc/1.21.11` for 1.21.11, `main` for 1.20.1) and the memory core at [github.com/adevivo/ai-companion-memory](https://github.com/adevivo/ai-companion-memory).

*Not affiliated with, or endorsed by, Mojang or Microsoft. "Minecraft" is a trademark of Mojang Synergies AB.*
