# Backport ledger: `mc/1.21.11` → 1.20.1

Fixes made on the 1.21.11 line that also belong on the 1.20.1 line (`main` at 0.3.1, and
`fix/perception-radius` at 0.3.2, the unshipped next release). Each entry says whether the bug is
confirmed present on 1.20.1, which commits carry it, and what changes when porting it.

**Cherry-picking will not apply cleanly.** The trees diverged at the re-base, so treat the commits as
the reference and re-apply by hand. Three differences cause most of the friction:

| | 1.21.11 (`mc/1.21.11`) | 1.20.1 (`main`, `fix/perception-radius`) |
|---|---|---|
| Engine package | `com.player2.playerengine…` under `engine/common/src/` | `adris.altoclef…` under `engine/src/autoclef/`, and `baritone.api…` under `engine/src/main/` |
| Mappings | Mojmap everywhere | **Engine is Mojmap, the mod half is Yarn** (`MinecraftClient`, `net.minecraft.entity.Entity`, `ServerWorld`, `Text`) |
| `LlmConfig` | `com.neovetta.aicompanion.core.LlmConfig` (ai-companion-memory core 0.5.0) | `adris.altoclef.player2api.LlmConfig`, inside the engine |

Tick an entry off by adding the 1.20.1 commit hash to its **Status** line.

---

## 1. A restored companion never moves on a dedicated server 🔴

**Status:** not ported. **Confirmed present** on both 1.20.1 branches: identical
`Map<UUID, C>` + `computeIfAbsent` in `EntityComponentKey`, and `CompanionParking` restores under the
same UUID.

**Commit:** `ee81814` — *Never hand a restored companion the pathfinder of its discarded body*

**Symptom:** after the owner reconnects to a dedicated server, the companion answers chat and speaks
but never walks or turns its head. `/data get entity … Rotation` is identical to every digit across
minutes. A fresh `/companion spawn` works. Single player is unaffected.

**Cause:** Baritone's per-entity components are cached by UUID and hold a hard reference to the
entity object they were built for. Park discards the entity; restore rebuilds it with the same UUID
and gets the discarded body's pathfinder. Single player escapes because leaving the world stops the
integrated server, which clears the cache.

**Port:**
- Add `ComponentStore.java` beside `EntityComponentKey` in `engine/src/main/java/baritone/api/component/`
  (change the package line to `baritone.api.component`). It has no Minecraft imports, so the rest is unchanged.
- Rewire `EntityComponentKey` onto it as on 1.21.11. Engine is Mojmap here, so `Entity::getUUID` and
  `Entity::isRemoved` are the same names.
- Tests: `ComponentStoreTest` → `engine/src/test/java/baritone/api/component/`.
- Verify in game: log out, log in, `/companion come`.

---

## 2. Config screen: warn when the file is not what the game is using

**Status:** not ported. **Confirmed missing** on both branches (no stale check in the screen).

**Commit:** `512e039` — *Say when the config screen shows settings the game is not using, and expose useGrammar*

**Symptom:** a hand-edited API key shows correctly in the screen, since the screen re-reads the
file, but the game still uses the one it loaded at launch. Result: repeated 401s with a key that
looks right.

**Port:** `unappliedLlmEdits()` and the gold warning at the top of `buildLlm()` in
`aicompanion/…/client/CompanionConfigScreen.java`. Read `LlmConfig` from the engine package, and
use Yarn for the text: `Text.literal(…).formatted(Formatting.GOLD)` rather than
`Component…withStyle(ChatFormatting.GOLD)`. `envApiKeySet()` already exists there.

---

## 3. Config screen: JSON Mode toggle

**Status:** not ported. **Confirmed missing:** no `useGrammar` control and no `_useGrammar` help text.

**Commits:** `512e039` (toggle and help text), `32e428d` (corrected wording). Port the wording from
`32e428d`, not the first version. The first version wrongly blamed Ollama.

**Port:** the `startBooleanToggle("JSON Mode", …"useGrammar"…)` entry directly under Max Tokens,
tooltip ending *"(llm.useGrammar in the config file.)"*; the `_useGrammar` string in the
`DEFAULT_JSON` llm block of `CompanionConfig.java`. The label is **JSON Mode** by decision. The
readme documents the mismatch with the key name (entry 7).

---

## 4. LM Studio: send `json_schema` when `json_object` is refused 🔴

**Status:** not ported. **Confirmed present:** 1.20.1 sends only `json_object`. The failure itself
was **observed on 1.20.1** (0.3.2, 2026-09-17, 33 refusals in one session).

**Commit:** `7bba7ff` — *Send LM Studio the JSON format it accepts, …*

**Symptom:** `HTTP 400 {"error":"'response_format.type' must be 'json_schema' or 'text'"}`,
shown in chat as a raw `HttpApiException`. Turning JSON mode off only swaps it for prose replies
that run nothing (observed the next morning).

**Port:** in `engine/src/autoclef/java/adris/altoclef/player2api/Player2APIService.java`:
`chatCompletion` → `sendChat` split with the catch-and-retry, `schemaOnlyEndpoint`,
`needsJsonSchema()`, `rejectsJsonObject()`, `responseFormat()`, and `applyLlmParams` calling
`responseFormat(needsJsonSchema())`. `HttpApiException` is at `…/player2api/utils/`. Tests:
`LocalServerQuirksTest` (JSON half).

⚠️ **Not yet verified against a live LM Studio** on either line. Verify on whichever line gets
tested first, and note it here.

---

## 5. Token-cap advice that tells a 2000 cap to rise to 1000 🔴

**Status:** not ported. **Confirmed present:** the same `"Raise llm.maxTokens to at least "`
fallthrough, plus the two hard-coded copies on the deterministic and plain-text paths. The failure
was **observed on 1.20.1** (qwen3:4b on Ollama, 2026-09-22, about 16 times; lumberjack and building
requests).

**Commit:** `7bba7ff` (same commit as entry 4).

**Symptom:** `LLM reply was cut off by the output token limit again (llm.maxTokens=2000). … Raise
llm.maxTokens to at least 1000. Raw=<<>>`. The reply is empty because a thinking model spent the
budget on hidden reasoning.

**Port:** `truncationNote()` gains the empty-reply branch first and the "above N" branch last. The
deterministic and plain-text warnings call `truncationNote(content)` instead of hard-coding the
sentence. Tests: `LocalServerQuirksTest` (token half).

---

## 6. Chat advice: say how to apply a config edit, and name the JSON Mode toggle

**Status:** not ported. **Confirmed present:** `adviseOn` exists at
`…/player2api/brain/NetworkBrainTransport.java` without the `APPLY` suffix or the `response_format`
branch.

**Commits:** `512e039` (`APPLY` suffix on the key, endpoint and no-reason advice), `7bba7ff`
(`response_format` branch). Tests go in the existing `BrainFailureAdviceTest` on 1.20.1.

---

## 7. Readme

**Status:** not ported.

**Commits:** `6296ad4` (*Screen names that differ from the file* table; the config is read at
launch, on reload and on Save), `32e428d` (JSON Mode row and qwen3 thinking-model note).

Check the 1.20.1 screen's labels before copying the table. It was generated from the 1.21.11 screen.

---

## 8. A companion that finds nothing nearby wanders in place forever 🔴

**Status:** not ported. **Confirmed present:** 1.20.1's
`engine/src/autoclef/java/adris/altoclef/tasks/movement/TimeoutWanderTask.java` has the same
`isFinished()` that returns false for an infinite distance before checking `failCounter`.

**Commit:** `4f364e6` — *Give up on a command whose wander has stood still for a minute*.

**Symptom:** `[Alto Clef] Failed exploring.` in the server log every ~6 s with no end. The companion
stands still, and the model is never told. Observed 2026-09-25: `get wool 3` in a snow biome with no
sheep ran more than three minutes before a human stepped in.

**Cause:** most resource tasks fall back to an unbounded `new TimeoutWanderTask()` when nothing they
want is loaded, and an unbounded wander can never finish. Ending only the wander does not help,
because the parent hands back a new one on the next tick.

**Port:** `giveUpIfStalled()` and its four fields go into the 1.20.1 `TimeoutWanderTask`, called
after the `Failed exploring.` block. Rename for the old engine: `PlayerEngineController` →
`AltoClefController`, and `chains.UserTaskChain` stays at `adris.altoclef.chains`. `logAgentNotice`,
`isRunningIdleTask`, `TaskChain.getTasks()` and `TaskRunner.getCurrentTaskChain()` all exist there.
Verify in game: in a biome without sheep, `get wool 3` should give up after about a minute and show
"I gave up —" in chat.

---

## 9. Chests: `chests`, `withdraw`, a working `deposit`, and two item dupes 🔴

**Status:** not ported. **Both dupes are confirmed present** on 1.20.1:
`StoreInContainerTask.java:159` grows the container's real stack during a simulated insert, and
`PickupFromContainerTask.java:56` inserts a one-item probe that is never taken back.

**Commit:** `dcaef42` — *Let companions list, take from and put into chests*.

**What changed:**
- `ContainerAccess` (new, `util/helpers/`): finds storage containers in loaded chunks within 16 blocks,
  combines both halves of a double chest into one, and `take`/`put` items without creating or losing
  any. Tests: `ContainerAccessTest`. These bootstrap Minecraft's registries, the first tests to do so.
- `VisitContainersTask` (new): walks to each container, skips any it cannot reach in 30 s, and opens
  the chest lid while it is using it. Subclasses: `SurveyContainersTask` (`chests`),
  `WithdrawFromContainersTask` (`withdraw`), `DepositInContainersTask` (`deposit`).
- `deposit` no longer uses `StoreInAnyContainerTask`. That task counted items already in the chest
  toward the deposit, and never finished a partial deposit.
- `pendingResult` / `reportCommandResult`: a command's answer is written into the finish event, the
  same way `pendingFailure` is. `mod.log()` only reaches stdout, never the model.
- The dupes are fixed in place in `StoreInContainerTask` (the stash task still uses it) and in
  `PickupFromContainerTask`.

**Port:** the new classes have no mappings-specific code beyond Mojmap Minecraft names, and the
engine is Mojmap on 1.20.1 too. Check `ChestBlock.getContainer`, `ChestBlockEntity.getOpenCount`,
`LevelChunk.getBlockEntities` and `ItemStack.isSameItemSameComponents` against the 1.20.1 jar with
`javap`. **`isSameItemSameComponents` is 1.20.5+; on 1.20.1 it is `ItemStack.isSameItemSameTags`.**
Rename `PlayerEngineController` → `AltoClefController`.

---

## 10. A companion building at negative coordinates walls itself in and suffocates 🔴

**Status:** not ported. **Confirmed present:** 1.20.1's `WorldHelper.java:348-349` builds the box
bounds with `(int)`, and the same `(int)` casts are at `:59` and `:63`. `BuildStructureTask` relies on
`getBlocksTouchingPlayer` for its "never brick ourselves in" check.

**Commit:** `44f2546` — *Floor block coordinates so the build's body check looks where the body is*.

**Symptom:** observed 2026-09-25 at x≈-3, z≈-131. The build logged `no standing position reaches
-3, 77, -131; placing it from here` 465 times in about 25 s. Health then fell about 2 HP/s, and the
companion died: *"suffocated in a wall"* at -4, 77, -132.

**Cause:** `(int)` rounds toward zero, so every negative X or Z comes out one block off. The body check
wrongly treated a free cell as occupied, deferring it every tick, and treated the occupied cell as free,
so a solid block was placed in it. Every other caller of `getBlocksTouchingPlayer` was also a block
off at negative coordinates: the survival, mob-defence and food chains, and the stuck tracker.

**Port:** `BlockPos.containing(...)` in `getBlocksTouchingBox`, `toBlockPos` and `toVec3i`, plus
`FollowPlayerTask` and `ProjectileProtectionWallTask`. Check that `BlockPos.containing` exists on
1.20.1 with `javap`; if not, use `Mth.floor` on each axis. The no-station log line is now written once
per cell. Test: `WorldHelperBlocksTouchingTest`.

---

## 11. Powder snow: the pathfinder walks into it, and the escape misses half the cases

**Status:** not ported. **Not yet checked on 1.20.1.** Powder snow exists there, so check its
`MovementHelper.canWalkThrough` / `fullyPassable` and its `UnstuckChain.checkStuckInPowderSnow`.

**Commit:** `6204c3f` — *Treat powder snow as an obstacle, and break out of it from any cell the body is in*.

**Cause:** `PowderSnowBlock.isPathfindable()` returns true, so the pathfinder treated drifts as air
and routed through them. The body sank in, slowed and froze, and stood still logging
`Failed exploring.`. The escape looked only at the column under the body's centre, relied on
`isInPowderSnow` (which vanilla clears every tick), and used the off-by-one box from entry 10.

**Port:** `Blocks.POWDER_SNOW` excluded in `canWalkThrough` and `fullyPassable`; the rewritten
`checkStuckInPowderSnow` plus its 30 s notice timer. Needs entry 10 first.

---

## 12. The build planner calls a model from the server, even with the client brain on

**Status:** not ported. **Present but latent** on 1.20.1: `BuildStructureTask.java:197` calls
`completer.processToString(service, …)` on the server. 1.20.1 has no separate server config file, so
a server that keeps its own key in `aicompanion.json` never notices. A server run without a key, as
the client-brain design intends, gets a 401 on every new build.

**Commit:** `dbebe35` — *Send the build planner's model call to the owner's client*.

**Symptom (1.21.11, 2026-09-25):** `LLM Transport Error=HTTP 401 … Missing Authentication header` ×3,
then `Could not build (…): the build plan failed to generate 3 times`. The companion told the owner
"the build service had an auth error".

**Port:** `BrainWire.PLAN_REQUEST`, registered in `registerServerToClientChannels`, with
`writePlanRequest` / `readPlanMessages` and `MAX_RESULT_BYTES`. `NetworkBrainTransport.completeTextOnClient`
and `PendingText`, plus the `deliver` and `forget` hooks. `RequestLLMCode.onStart` tries the client
first. `ClientBrain` gets the `PLAN_REQUEST` receiver and `plan()`. The answer rides `TURN_RESULT`,
so the server's C2S receiver is unchanged.

---

## Checked and not applicable

- **Client-only mixins crash a dedicated server** (1.21.11 `e3a143a`, 2026-09-03).
  `PlayerCollidesWithEntityMixin` and `ClientBlockBreakMixin` do not exist on 1.20.1, and its
  `mixins.altoclef.json` has no client-only targets listed under `"mixins"`.
