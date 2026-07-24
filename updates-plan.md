# Updates Plan — Stats, Radar, Skills

Three features from user feedback, built in phases. Each phase is independently shippable: build the
jar, hand it to the user for manual testing, then commit before moving on. This file is the
implementation brief; it assumes no context beyond what's written here plus the repo itself.

## Testing & versioning convention (READ FIRST)

**We do NOT use `runClient`.** The implementing agent compiles the consumer jar; the **user** installs it
into their local CurseForge instance and runs it manually. So for every phase:

1. **Bump `mod_version` in `aicompanion/gradle.properties` before building.** Patch bumps:
   - Phases 1 **and 2** → `0.1.3` (radar was folded into the stats release by user decision — no
     separate bump)
   - Phase 3 → `0.1.4`
   (Current baseline is `0.1.2`.)
2. Build with `cd aicompanion && ./gradlew build`; the deliverable is `aicompanion/build/libs/aicompanion-<version>.jar`.
3. Hand the user that jar path plus the phase's test checklist. **The user runs the checklist in
   CurseForge**, not the agent — every checklist below reads "(runClient)" for historical reasons but the
   real procedure is "user installs the built jar and verifies."
4. Only commit a phase after the user confirms its checklist passes. The version bump is part of the
   phase's commit.

## Ground rules for the implementing agent

- **This repo is MC 1.20.1 Fabric with Quilt Mappings (`quilt_mappings = 7`).** The consumer mod
  `aicompanion/` uses **Quilt-mapped names** (`ServerPlayerEntity`, `Text`, `Identifier`,
  `MinecraftClient`). The parent-directory `CLAUDE.md` (HandyDandy, MC 26.1.2, Mojmap) **does not apply
  here** — ignore its translation tables.
  - **Quilt ≠ Yarn on several client names** (learned in Phase 2). Yarn's `DrawContext` is
    **`GuiGraphics`** (`net.minecraft.client.gui.GuiGraphics`), with `drawShadowedText` /
    `drawCenteredShadowedText` instead of `drawTextWithShadow` / `drawCenteredTextWithShadow`. Yarn's
    `KeyBinding` is **`KeyBind`** (`net.minecraft.client.option.KeyBind`), `InputUtil` lives at
    **`com.mojang.blaze3d.platform.InputUtil`**, and `InputUtil.Key.getCode()` is **`getKeyCode()`**.
    When a client class won't resolve, javap the Quilt-mapped merged jar under
    `~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-*quilt-mappings*.jar` — don't
    trust Yarn names.
- **Build:** `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` (Java 25 fails with "major version 69").
  Consumer: `cd aicompanion && ./gradlew build`. (We do **not** use `runClient` — see the Testing &
  versioning convention above; the user installs the built jar into CurseForge and tests manually.)
- **Engine changes** (anything under `engine/`) require the Loom flatdir dance: bump `mod_version` in
  `engine/gradle.properties`, `cd engine && ./gradlew build`, `rm -f aicompanion/libs/PlayerEngine-*.jar`,
  copy the new jar into `aicompanion/libs/`, then rebuild the consumer. A ~0.5s consumer build means Loom
  did NOT re-remap (stale engine); ~1min means it did. **Phases 1 and 2 need no engine changes; Phase 3
  probably doesn't either** (see 3.4) — prefer consumer-only solutions.
- **Config papercut (affects Phases 2–3):** `CompanionConfig` writes `DEFAULT_JSON` only when
  `config/aicompanion.json` is absent, so new keys are silently missing for existing users. All new
  config reads MUST use the existing `str/num/bool(obj, key, default)` fallback helpers so an absent key
  degrades to its default. Do not rely on the key being present.
- Commit after each phase passes its test checklist. No Claude/Anthropic attribution in commit messages.
- User owns deployment and live-server testing; hand over a built jar and the checklist results.

## Existing seams you will build on

| Seam | Where | What it gives you |
|---|---|---|
| Command tree | `aicompanion/.../CompanionCommands.java` | `/companion …` registration, `findCompanion(source)` helper (owner-preferred lookup) |
| Companion body | `aicompanion/.../entity/CompanionEntity.java` | `inventory` (`LivingEntityInventory`: `main`, `armor`, `offHand` lists), `getController()` |
| Hunger | engine `LivingEntityHungerManager` via `controller.getBaritone().getEntityContext().hungerManager()` | food + saturation levels |
| Engine status format | engine `player2api/status/AgentStatus.java`, `StatusUtils` | proof of which stats exist and how the LLM already sees them |
| S2C packet pattern | `AiCompanion.OPEN_CONFIG_SCREEN` + `AiCompanionClient` receiver | exact idiom for new packets (1.20.1 buf-based `ServerPlayNetworking`/`ClientPlayNetworking`, hop to client thread via `client.execute`) |
| Brain event queue | engine `ConversationManager.getOrCreateEventQueueData(controller)` → `AgentConversationData.onEvent(Event)` | inject a synthetic message into a specific companion's LLM queue (used by chat today) |
| Config screen | `aicompanion/.../client/CompanionConfigScreen.java` | Cloth Config categories (`Identity`, `LLM`, `Voice (TTS)`, `Behavior`) — add new categories the same way |

---

## Phase 1 — `/companion stats` (smallest; consumer-only)

**Goal:** inspect the companion's HP, food, equipped items, and inventory from chat.

**Reality check on the request:** the user asked for XP too, but **the companion has no XP** — it's a
`LivingEntity`; neither `CompanionEntity` nor the engine tracks experience anywhere (verified by grep).
Do not fake one. The stats output simply omits XP. (If the companion should someday collect XP orbs,
that's a new feature — out of scope.)

### 1.1 Implementation

Add a `stats` subcommand to `CompanionCommands` (register next to `where`). Use `findCompanion(source)`;
same "no companion found" error path as the others. Output via `source.sendFeedback`, multi-line:

```
— Vetta —                          ← custom name, gold/bold
Health: 14.0/20   Food: 18/20 (sat 4.5)
Armor: iron_helmet (112/165), iron_chestplate (—) …or "none"
Hands: main = iron_sword (243/250), off = empty
Inventory (14/36 slots):
  64× cobblestone, 32× oak_log, 5× bread, …
```

Details:
- HP: `companion.getHealth()` / `companion.getMaxHealth()`.
- Food: only available when a controller is attached —
  `controller.getBaritone().getEntityContext().hungerManager()` → `getFoodLevel()`,
  `getSaturationLevel()`. If `getController()` is null, print `Food: n/a`.
- Armor + hands: walk `companion.inventory.armor` and the main/off hand getters already used by the
  entity's equipment plumbing (`CompanionEntity` lines ~282–312). Show durability as
  `(damage remaining/max)` for damageable items via `stack.getMaxDamage() - stack.getDamage()`.
- Inventory: iterate `companion.inventory.main`, aggregate counts per item
  (`Registries.ITEM.getId(stack.getItem()).getPath()`), sort descending by count, join with commas.
  Skip empty stacks. Count used slots for the `(n/36)` header.
- Formatting: mirror the existing feedback style (plain `Text.literal`); use `Formatting.GOLD` header,
  `Formatting.GRAY` for the inventory list. Keep each line a separate `sendFeedback` call.

### 1.2 Test checklist (user runs built jar in CurseForge — see Testing & versioning convention)

- [ ] `/companion stats` with no companion → clean error, not a crash.
- [ ] Spawn, damage it slightly, hand it items (`/companion` chat: "get 10 logs" or creative-give),
      then `/companion stats` → HP reflects damage, inventory shows aggregated items, armor/hands correct.
- [ ] Works when the companion is far away (uses `findCompanion`'s big box, same as `where`).

**Commit:** `Add /companion stats: HP, food, equipment, inventory readout`

---

## Phase 2 — Companion radar HUD (client HUD + one S2C packet)

**Goal:** a locator bar near the bottom of the screen that points toward the companion, so the owner can
walk toward it without recalling it. Must work beyond entity-tracking range (64 blocks) — that's the
whole point — so the client cannot rely on having the entity; the server must push coordinates.

### 2.1 Server side (consumer, `AiCompanion` + `CompanionEntity`)

- New packet id: `Identifier RADAR_UPDATE = new Identifier("aicompanion", "radar_update")` beside
  `OPEN_CONFIG_SCREEN`.
- In `CompanionEntity.tick()` server-side, every 10 ticks: if `getController() != null` and its owner
  resolves to an online `ServerPlayerEntity`, send them a buf with: `double x, y, z`,
  `Identifier` world id (`getWorld().getRegistryKey().getValue()`), `float health`, `float maxHealth`.
  Use `PacketByteBufs.create()` + `ServerPlayNetworking.send(owner, RADAR_UPDATE, buf)`.
- No packet when there's no owner. If the companion is in an unloaded chunk it stops ticking, so packets
  stop — the client handles staleness (2.2). That is acceptable v1 behavior; do not build a last-known
  store on the server.

### 2.2 Client side (new `client/CompanionRadarHud.java`)

- Receiver (register in `AiCompanionClient`): parse the buf, store into a static snapshot
  `{x, y, z, worldId, health, maxHealth, long receivedAtMs}`. No `client.execute` hop needed for a pure
  data write (render thread only reads).
- Render with `HudRenderCallback.EVENT` (`net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback`,
  gives `(DrawContext, tickDelta)`). Draw a horizontal bar ~180px wide, centered, sitting just above the
  hotbar (`y = screenHeight - 50` region; don't overlap the XP bar — nudge up if needed after visual check).
- Bearing math: `angleTo = Math.toDegrees(Math.atan2(dz, dx)) - 90` (MC yaw convention);
  `rel = MathHelper.wrapDegrees(angleTo - player.getYaw())`. Map `rel` in [-90°, +90°] linearly onto the
  bar; beyond ±90° clamp the marker to the bar edge and draw it as a chevron (`‹`/`›`) meaning "behind
  you, turn". Marker: a small colored square or the `»` char; white normally, red-tinted when companion
  health < 1/3 max.
- Below/beside the marker: distance in blocks (`%.0fm`). Y-delta hint: `▲`/`▼` next to the distance when
  |dy| > 4.
- Cross-dimension: if snapshot `worldId` ≠ client `world.getRegistryKey().getValue()`, render the bar
  dimmed with the text `other dimension` instead of a marker.
- Staleness: if `now - receivedAtMs > 5000`, dim the whole bar and append `(last seen)`. If > 60s or no
  snapshot ever, render nothing.

### 2.3 Visibility modes

Three modes, client-side static enum: **ON** (default, per user decision — bar always visible whenever a
companion is reporting in), **AUTO** (shows only when distance > 16 blocks or stale/cross-dimension,
hides when the companion is right next to you), **OFF**.
- `/companion radar` server subcommand cycles the mode by sending a tiny S2C packet
  (`RADAR_TOGGLE`) the client acts on, echoing the new mode in chat — same idiom as `config`.
- Also register a client keybind (`KeyBindingHelper`, category `AI Companion`, default **unbound** to
  avoid conflicts) that cycles the same enum.
- Mode is session-scoped (static). Do not persist it; not worth a client config file for v1.

### 2.4 Test checklist (user runs built jar in CurseForge — see Testing & versioning convention)

- [ ] Spawn companion, send it away (`/companion goto` somewhere ~200 blocks off) → bar appears (AUTO),
      marker tracks it as you turn; chevron at edges when it's behind you.
- [ ] Walk to it → within 16 blocks the bar hides (AUTO); `/companion radar` → ON keeps it visible.
- [ ] Distance readout roughly matches `/companion where`.
- [ ] Despawn the companion → bar goes stale then disappears (≤ 60s).
- [ ] Nether test: bar shows `other dimension`, no bogus marker.
- [ ] HUD does not collide with hotbar/XP bar at gui scale 2 and 3.

**Commit:** `Companion radar: locator bar HUD with auto/on/off modes`

---

## Phase 3 — Skills (markdown-defined procedures)

**Goal:** `/companion skill <name>` teaches the companion a user-authored procedure at invocation time.
A skill is a markdown file — instructions the LLM receives verbatim and executes with its normal
command loop. This is prompt injection by design, not a macro system: the model already emits
`{reason, command, message}` and chains AltoClef tasks; the skill text steers it.

### 3.1 Skill file format

Directory: `config/aicompanion/skills/*.md` (create on first load, alongside the existing
`config/aicompanion/skins/` and `tts/` conventions). Format — plain markdown, no YAML parser needed:

```markdown
# Lumberjack
Collect a full stack of logs and bring them back.

Go to the nearest forest. Collect 64 logs of any type. When you have them,
return to your owner and tell them what you gathered. If you get stuck for
more than a minute, stop and say so.
```

- **Name** = first `# ` heading (fallback: filename minus `.md`). Invocation key = name lowercased,
  spaces → `-` (so `/companion skill lumberjack`).
- **Description** = first non-heading paragraph (used in listings and the prompt advertisement).
- **Body** = the whole file below the heading, sent verbatim.
- Cap body at 4000 chars at load; truncate with a log warning (protects the context window).
- Ship 2 example skills on first run (written like the TTS setup unpack): `lumberjack.md` above and a
  `home-guard.md` (follow owner, warn about hostiles) — so the feature is discoverable.

### 3.2 Loader (new `CompanionSkills.java`, consumer)

Static `Map<String, Skill>` (`record Skill(String key, String name, String description, String body,
Path file)`). Load at mod init and re-scan inside `CompanionConfig.reloadAndApply` so `/companion reload`
picks up edits — mention that in the reload feedback line. Malformed/empty files: skip with a log
warning, never crash the load.

### 3.3 Invocation

- `/companion skill <name>` — greedy string arg with a `SuggestionProvider` over loaded skill keys.
- `/companion skills` — list loaded skills (name — description, gray) plus the directory path and the
  hint that files are hot-reloaded by `/companion reload`.
- On invoke: `findCompanion(source)`; require a non-null controller (error otherwise). Then enqueue a
  synthetic user message into that specific companion's brain queue:

```java
AgentConversationData data = ConversationManager.getOrCreateEventQueueData(ctrl);
data.onEvent(new Event.UserMessage(
    "Execute this skill now, step by step, using your available commands:\n\n" + skill.body,
    source.getName()));
```

  This targets exactly the caller's companion (unlike `onUserChatMessage`, which fans out by 64-block
  distance) and reuses the same queue/lock machinery as chat — no threading concerns.
- Feedback: `Skill 'Lumberjack' sent to <name>.` The companion's spoken `message` reply is the real ack.

### 3.4 Engine visibility check (do this first)

`AgentConversationData.onEvent` and `Event.UserMessage` are engine classes. `onEvent` is called from
`ConversationManager` (different package), so it should already be public — **verify the consumer can
compile against both before writing the rest.** If either is package-private, the fix is a one-word
visibility change in the engine → that triggers the full engine version-bump/restage dance from the
ground rules. Budget for it; don't discover it at the end.

### 3.5 Skill awareness in the system prompt (small, optional but recommended)

So the user can also say "use your lumberjack skill" in chat, append a short block to the persona at
config-load time (where `Prompts.persona` is set in `CompanionConfig`):
`"You have these skills your owner can invoke: lumberjack — Collect a full stack…"` — one line per
skill, names + descriptions only (bodies stay out of the standing prompt; they're injected on demand).
Gate behind config key `skills.advertiseInPrompt` (default `true`, read with the fallback helpers per
the papercut rule).

### 3.6 Config screen tab

Add a `Skills` category to `CompanionConfigScreen`: read-only rows (Cloth Config text/label entries) —
one per loaded skill showing name + description, prefixed by a label entry stating the folder path and
"edit the .md files, then /companion reload". No in-GUI editor — Cloth Config is the wrong tool for
editing multi-line markdown, and the files-on-disk workflow matches how skins/TTS are configured.

### 3.7 Test checklist (user runs built jar in CurseForge — see Testing & versioning convention)

- [ ] First launch creates `config/aicompanion/skills/` with the 2 examples.
- [ ] `/companion skills` lists them; tab-completion works on `/companion skill `.
- [ ] Invoke `lumberjack` with llama.cpp up → companion announces intent and starts an AltoClef task
      (watch `run/logs/latest.log` for the round-trip; the injected message should appear as a user turn).
- [ ] Edit a skill file, `/companion reload`, `/companion skills` shows the change.
- [ ] Broken skill file (empty) → skipped with a warning, everything else still loads.
- [ ] Config screen shows the Skills tab.

**Commit:** `Skills: markdown-defined procedures via /companion skill <name>`

---

## Follow-ups deliberately out of scope

- XP tracking for the companion (no such stat exists today — see Phase 1).
- Persisting radar mode across sessions (client config file — revisit if requested).
- A GUI stats screen with rendered item slots (chat readout first; upgrade later if the text version
  falls short).
- Skill scheduling/chaining/parameters. Keep v1 to "inject and let the model drive".
- Multiplayer scoping of all three features (owner-only radar is already right; the rest lands with
  PLAN.md Phase 8's per-player identity work).
