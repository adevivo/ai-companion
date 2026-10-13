# AI Companion — Modrinth project listing

> Paste-ready copy for the Modrinth project, field by field, in the order Modrinth asks for it.
> Written 2026-10-05 from `curseforge-description.md`. The long description at the bottom is that
> file's body with the version table moved to 0.4.3; keep the two in step when either changes.
> Modrinth's form changes now and then, so a field here may sit under a different heading.

---

## Create-project dialog

| Field | Value |
|---|---|
| **Project type** | Mod |
| **Name** | `AI Companion` |
| **URL (slug)** | `ai-companion`. If it's taken, `ai-companion-llm` |
| **Visibility** | Public (it stays hidden until moderation approves it either way) |
| **Summary** | below |

**Summary** (plain text, 256 characters max; this one is 240):

```
Autonomous AI companions driven by your own LLM: a player bot that gathers, crafts, builds, fights, guards you and remembers what you tell it. Run the model locally (Ollama, llama.cpp, LM Studio) or use ChatGPT or any OpenAI-compatible API.
```

Modrinth's search indexes the **summary** and categories as well as the name. That's why the
keywords (LLM, player bot, Ollama, ChatGPT) go here and not in the name: Modrinth's content rules
discourage stuffing extra text into the title.

---

## Settings → General

- **Icon:** `docs/branding/ai-companion-icon-256x256.png`
- **Environment:** required on **both client and server**. On 1.21.11 the owning player's client
  does the companion's thinking and holds the API key, and the server spawns and moves the companion.
  If the form has separate fields: Client side = **Required**, Server side = **Required**.

## Settings → Tags

- **Primary categories (pick 3):** Mobs · Utility · Game Mechanics
- **Additional:** Adventure, Social

## Settings → License

- **License:** `LGPL-3.0-or-later` (GNU Lesser General Public License v3.0 or later)

## Settings → Links

| Field | Value |
|---|---|
| Issue tracker | `https://github.com/adevivo/ai-companion/issues` |
| Source code | `https://github.com/adevivo/ai-companion` |
| Wiki | leave blank |
| Discord | leave blank (or add one if you have it) |

## Gallery

Upload both diagrams (they're also used in the description):

| File | Title | Description |
|---|---|---|
| `docs/images/how-the-pieces-fit.png` | How the pieces fit | The download's three parts, and what you bring: your own model. |
| `docs/images/where-a-turn-runs.png` | Where a turn runs | The server moves the companion; the owner's computer holds their memories, builds the prompt and calls their model. |

In-game screenshots would sell it far better than diagrams. Add some when you have them, and
mark the best one **Featured**: it becomes the card image in search.

---

## Version 1: 0.4.3 for 1.21.11

| Field | Value |
|---|---|
| File | `aicompanion/build/libs/aicompanion-0.4.3+mc1.21.11.jar` |
| Version number | `0.4.3+mc1.21.11` |
| Version title | `0.4.3 for Minecraft 1.21.11` |
| Release channel | Release |
| Loaders | Fabric |
| Game versions | 1.21.11 |

**Dependencies:**

| Project | Type |
|---|---|
| Fabric API | Required |
| Architectury API | Required |
| Cloth Config API | Optional |
| Mod Menu | Optional |

Do **not** add PlayerEngine as a dependency. The modified fork is bundled inside the jar.

**Changelog:**

```markdown
The first Modrinth release, and the current release for **Minecraft 1.21.11**. Playing 1.20.1? Use 0.2.9.

## 0.4.3
- **She sees animals and villagers**, plus slimes, ghasts, phantoms, shulkers and hoglins, which she used to miss.
- **She leads animals with food.** Cows follow her wheat, pigs her carrots and chickens her seeds, just like they follow you.
- **`clear` breaks blocks on request**, up to 512 at a time. Chests and other containers are never touched.
- Bodyguard no longer gives up after a quiet minute.
- Fixed a second fishing crash that could disconnect you from a server.

## 0.4.1
- **No Player2 code, no telemetry.** The bundled engine (our PlayerEngine fork) no longer contains upstream's Player2 cloud login and once-a-minute heartbeat. They were off by default and never used, and are now deleted. The mod only contacts the AI, embeddings and voice endpoints you configure.
- Fixed a client crash when a companion goes fishing, and chat flooding with "Fishing process started" when she can't reach water.
- Memory no longer switches off for a turn that starts with filler like "okay", "hm" or "yeah". "okay, let's add the doors" now recalls what you told her about doors. "okay stop" is still a command.

## 0.4.0 highlights
- **She remembers you.** Tell your companion something and she can bring it up days later, in another world, or on a server. Optional and off by default; needs a small embedding model such as Ollama's `nomic-embed-text`.
- **Multiplayer for everyone.** Each player brings their own companions, model and API key. The thinking and the memories stay on each player's own computer. A companion only listens to its owner.
- **Bodyguard.** "Protect me" keeps her at your side, fighting whatever comes for you, creepers first.
- **Chests, with permission.** Only the ones you've opened yourself or told her to use.
- **Builds from what she carries**, and opens doors instead of breaking through them.
- Cloth Config is now optional: without it, `/aicompanion config` edits settings from chat.
- Fixes: chest item duplication, companions frozen after reconnecting to a dedicated server, conversation lost on logout, getting stuck in powder snow, and more.

## Known issues
- A companion unloaded mid-task forgets the task. Just ask again.
- Free hosted models struggle with large builds.
- Land-claim mods can't see companions (they aren't players), so on a protected server one could break blocks inside a claim.
```

## Version 2: 0.2.9 for 1.20.1

1.20.1 is still where most players are, so it's worth having this line on Modrinth too. Use the
same jar you uploaded to CurseForge (it isn't in this repo's build output).

| Field | Value |
|---|---|
| File | `aicompanion-0.2.9.jar` |
| Version number | `0.2.9` |
| Version title | `0.2.9 for Minecraft 1.20.1` |
| Release channel | Release |
| Loaders | Fabric |
| Game versions | 1.20.1 |

**Dependencies:** Fabric API = Required. On `main`, `fabric.mod.json` *depends* on `cloth-config`.
Before you set the type, check whether the 0.2.9 jar has it nested (`unzip -l aicompanion-0.2.9.jar | grep jars/`).
If it's nested, set it to **Embedded**. If not, set it to **Required**.

**Changelog:**

```markdown
The release for **Minecraft 1.20.1**: singleplayer and trusted LAN. For memory, multiplayer for everyone, bodyguard and chests, see 0.4.3 for 1.21.11.
```

---

## Description (paste everything below this line)

**Companions that play alongside you, not scripted NPCs.** Tell one what you need in plain chat, and she works out how to do it: she gathers and crafts, builds on request, and fights beside you or guards you. Her voice and judgement come from a language model **you** choose. That can be one running privately on your own machine, or a hosted API if you'd rather have a frontier model.

The model decides *what* to do and *what to say*. Walking, mining, crafting and fighting are handled by the game engine, so a slow or offline model never leaves her frozen mid-task.

## Which version?

| Minecraft | Mod version | Notes |
|---|---|---|
| **1.21.11** | **0.4.3** | Current. Memory, multiplayer for everyone, bodyguard, chests, animals. |
| 1.20.1 | 0.2.9 | Older line. Singleplayer and trusted LAN. |

## What she can do

- **Real work.** She mines, collects, crafts, hunts, farms, fishes and builds. Builds are paid for from her own inventory, block by block, and she gathers what's missing.
- **Fights like a player.** She wears the best armour you give her, uses a shield, eats when she's hungry and drops her gear if she dies. She has a player's strength, not a monster's.
- **Bodyguard** *(1.21.11)*. "Protect me" keeps her at your side, taking on anything that comes for you, creepers first.
- **Remembers you** *(1.21.11, optional)*. Facts you tell her carry over to later sessions, other worlds and servers.
- **Chests, with permission** *(1.21.11)*. She uses only the chests you've opened yourself or told her to use.
- **More than one.** Keep a roster, each companion with its own name, personality, skin (your own PNG, or any Minecraft username *(1.21.11)*) and voice.
- **Skills.** Ready-made routines (lumberjack, farming, fishing, harvest, home guard, staircase mine, and bodyguard *(1.21.11)*) are plain Markdown files you can edit or add to.
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
- **Hosted:** any OpenAI-compatible API with your own key (OpenRouter, xAI, OpenAI…). An optional hard cap stops runaway spending, and on 1.21.11 most of each request is cached, which keeps turns cheap.

**Memory** (optional, off by default) also needs an embedding model. The easy route is Ollama with `nomic-embed-text`.

**Voice** (optional) uses a small Kokoro container. On first launch the mod writes a ready `docker-compose.yml` into `config/aicompanion/tts/`; start it and your companions speak, each in her own voice.

> **Hardware note:** a local model on the same PC competes with Minecraft for memory. Overdo it (a huge context, every layer on the GPU) and you can freeze the whole machine, not just the game. Start small, or run the model on another computer on your LAN.

## Requirements

**1.21.11:** Fabric Loader 0.17.2+, Fabric API, **Architectury API**. Optional: Cloth Config and Mod Menu (for the settings screen; without them, `/aicompanion config` edits settings from chat).
**1.20.1:** Fabric Loader, Fabric API.

The pathfinding and task engine is bundled inside the jar. **Don't** install a separate PlayerEngine. Fabric only; Forge through Sinytra Connector isn't supported.

## Multiplayer and servers *(1.21.11)*

![Where a turn runs: the server moves the companion; the owner's computer holds their memories, builds the prompt and calls their model](https://raw.githubusercontent.com/adevivo/ai-companion/mc/1.21.11/docs/images/where-a-turn-runs.png)


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
