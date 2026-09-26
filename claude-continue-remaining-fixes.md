# Continue: `mc/1.21.11` after the 2026-09-26 session

Handoff for the next session. Read this, then `docs/backport-1.20.1.md` (entries 13–17 are from this
session). The workspace-root `CLAUDE.md` is for **Minecraft 26.1.2 and does not apply here**. This
branch is **Minecraft 1.21.11, Mojmap in both the engine and the mod half**.

## Current state

- **Deployed on both machines (holly server, grendel client):** `aicompanion-0.4.0+mc1.21.11.jar` with
  engine **1.21.11-1.0.37**. Every commit below is in it. Nothing is pushed; the user pushes.
- **Server config** (holly, `config/aicompanion-server.json`): `brain.clientTimeoutMs` 120000 (raised
  from 45000 this session), `brain.planTimeoutMs` 180000 (new key, back-filled), `tts.maxSpokenChars`
  0 (back-filled).
- **Client config** (grendel): `llm.maxPromptChars` 32000 (migrated from the old 20000 default),
  `llm.maxTokens` 16000 (set by the user for big build plans), model
  `nvidia/nemotron-3-super-120b-a12b:free` on OpenRouter.

## What this session built

**Build plans and model calls**
- `6bac1fb` Plan requests have their own wait, `brain.planTimeoutMs` / `llm.clientPlanTimeoutMs` (180 s),
  instead of the 45 s turn wait. No request after the last attempt; a timed-out plan is not asked for
  again. Failure advice names the timeout key or a 503 instead of blaming `llm.endpoint`.
- `577929d` An interrupted build no longer sends extra plan requests (`RequestLLMCode.isEqual` compared
  empty Optionals by reference).
- `95566bf` A self-triggered command dropped for a user message is neither announced nor stored, and
  the model is told it did not run. Fixed a false "house complete".
- `576f47c` A build's gather stops and asks after 3 minutes with no inventory change; a build resumed
  mid-gather keeps gathering; the gather takes from allowed chests first; `/companion list` shows the
  subtask tree.
- `6a583f0` The planner is given the companion's inventory and the seen contents of allowed chests.
  The turn model marks intent with tags the code reads exactly (any wording or language):
  `[use inventory]` builds only from what is carried and sends an over-budget design back to be made
  smaller; `[gather ok]` records approval. A plan short of more than 64 items asks first.
- `c095043` `[gather ok]` counts only within 10 minutes of the companion asking. A finished build tells
  the model the block count and not to list features it has not seen.

**Chests**
- `8eebd34` `ChestPermissions` (SavedData, per owner, persistent): `chests` / `withdraw` / `deposit` use
  only containers the owner allowed, by opening it, `usechest` at it, or a deposit placing it.
  `forgetchest` removes one.
- `fd5418b` `get` withdraws from allowed containers first (`GetFromStorageFirstTask`), then gathers.

**Reporting and prompts**
- `8db4088` `scan`, `eat`, `give`, `stand_ground`, `get` report to the model instead of `mod.log()`.
- `ee31043` `/companion reload` no longer claims it re-applied an edited persona.
- `d157373`, `38ca4dd` Prompt budget default 20000 → 32000 (a file holding exactly 20000 is migrated);
  the fixed prompt (~18k) had left no room for history, so every earlier turn was dropped. Trimmed the
  text added for chests and tags. The cut-off warning reports the cap the request was sent with.

**Config screen**
- LLM tab now has every player LLM setting: added Max Prompt Chars, Request Timeout, Turn Wait When
  Hosting, Plan Wait When Hosting (`38ca4dd`).
- TTS tab: Max Spoken Chars, 0 = read everything, the new default (`d011086`).

**Movement**
- `8a63dc2` Doors: `interactBlock` now does vanilla's empty-hand use, which is what opens a door, and
  places block items through `EntityPlaceContext`. Before, clicking a closed door with a torch in hand
  crashed the tick and the pathfinder broke the door. `OpenedDoors` closes doors and gates she opened
  once she is 2.5 blocks clear and nobody is in the doorway. The "internal error" chat line is sent at
  most once a minute.

**Docs:** `4e7273d` build benchmark and the single-pass decisions design note; backport ledger
entries 13–17.

## Verified in play (2026-09-26)

- The 120 s turn and 180 s plan waits load; a 110 s plan was accepted.
- `chests` sees only chests the owner opened, and names the others as not allowed; `withdraw` takes
  from an allowed chest.
- "Build a small house with items from your inv" became `[use inventory]`; a 120-block cottage was
  planned from the inventory and built with no gathering.
- A build gather took planks and doors from an allowed chest and crafted stone bricks from carried
  stone (a 2x2 recipe, no table), then placed 164 blocks.
- Conversation memory works again after the budget change: she recalled a remark three messages back.
- Whole messages are spoken (340 characters).
- She opens a wooden door instead of breaking it, and closes it once clear (it stays open while she
  stands next to it, by design).
- Powder-snow escape worked twice in a row.

## Not yet verified

1. A large build with **no** tag stops and asks before gathering; "yes" then runs it with
   `[gather ok]`. Not tested: the model kept tagging on its own or chose `get` first (see Open 5).
2. The 3-minute stall stop on a build's gather.
3. `withdraw` from a chest nobody opened is refused and she asks; "use this chest" runs `usechest`.
4. `get white_wool 3` with wool in an allowed chest takes it from the chest.
5. `scan` answers with a position; the `/companion reload` wording; "come here" goes to the speaker.

## Open, in priority order

1. **A reload silently drops the running command.** 2026-09-26: `get stone 200` was running when the
   owner died to a creeper; 4 s later the log shows "re-attached brain to restored companion", which
   happens only for a new entity object with no controller, so the companion had been unloaded and
   reloaded. The in-memory task was gone, no finish or failure reached the model, and she sat idle for
   50 minutes. Cause of the reload not confirmed (likely the owner respawning far away and her chunk
   unloading). Builds survive via `UnfinishedBuild`; nothing else does. Fix: save the running command
   in the entity's NBT and, when the brain is rebuilt, tell the model it was interrupted.
2. **Long `get`s are invisible in the log.** Nothing is logged while a `get` runs, so 11 minutes of
   `get stone 200` could not be diagnosed. Log progress toward the count every ~30 s.
3. **`ConversationHistory.summarizeHistory` calls a model on the SERVER THREAD.** On holly it fails
   with a 401 at once (no key); on a server with a key it would freeze the tick loop for the whole
   request. Move it off-thread or route it to the client.
4. **Stopping a build does not cancel its plan request.** The client keeps generating a plan nobody
   uses (a stopped castle's plan ran 5.5 minutes to the 16000-token cap). Send a cancel to the client
   and have `ClientBrain` abort the HTTP call.
5. **The turn model sets build tags on its own.** It added `[gather ok]` unasked (now ignored unless it
   asked, `c095043`), and later `[use inventory]` for a request that never mentioned the inventory,
   apparently carried over from earlier turns. Given explicit permission, it chose `get stone 200`
   first, which by smelting is half an hour of work. Watch it; the planner could also be told what is
   cheap to get nearby.
6. **This free model is poor at big build plans.** Castle plans ran to the token cap at both 8000 and
   16000 (3–5.5 minutes each); the retry after a cut-off came out as a small building. Compare models
   with `docs/build-benchmark.md` before blaming code.
7. **Door logging:** `OpenedDoors` logs nothing; one INFO line per open, close and give-up.
8. **`docs/config.example.json` has drifted.** Regenerate from `CompanionConfig.DEFAULT_JSON` (memory:
   *config.example.json is generated*); it lacks the `clientBrain` group and today's keys.
9. Smaller, when wanted: persona reload only fixed the message (a roster edit still needs despawn and
   spawn); `get` does not look in chests for ingredients (`ResourceTask.allowContainers` would need
   moving to the permission-checked path); chest choices the user may revisit (no `usechest all`,
   opening always allows, deposit places a chest rather than asking); noisy WARN/WARN/ERROR on one
   client failure; free Nvidia models return 503 often (accepted, do not "fix").

## Working notes

- **Build (JDK 21 for 1.21.11; JDK 17 is only for the 1.20.1 line):**
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  cd engine && ./gradlew :common:test :fabric:build
  cp fabric/build/libs/playerengine-fabric-1.21.11-<ver>.jar ../aicompanion/libs/PlayerEngine-1.21.11-<ver>.jar
  cd ../aicompanion && ./gradlew build     # -> build/libs/aicompanion-0.4.0+mc1.21.11.jar
  ```
  Bump `mod_version` in `engine/gradle.properties` **and** `playerengine_version` in
  `aicompanion/gradle.properties` for every engine change, or Loom serves a stale remapped jar.
  Current: `1.21.11-1.0.37`. Tests: 90, all passing.
- **Settings that belong to one line go in the engine, not the core.** `LlmConfig` and `ServerPolicy`
  live in `ai-companion-memory` (core 0.5.0), shared with 1.20.1; a new field there means a core
  release. `NetworkBrainTransport.planTimeoutMs` and `TTSManager.maxSpokenChars` are engine-side.
- **A new config key:** add it to `DEFAULT_JSON` with a `_help` string, read it in `apply()`, and add
  it to `CompanionConfigScreen`. Existing files are back-filled on load; a changed default needs a
  migration (see `migratePromptBudget`).
- **Build tags:** `BuildStructureTask.TAG_USE_INVENTORY` / `TAG_GATHER_OK`, stripped from the
  description before templating and caching (`BuildTagsTest`).
- **SavedData on 1.21.11:** copy `WorldIdentity` / `ChestPermissions` — one static `SavedDataType`, a
  real `DataFixTypes`, `setDirty()` on creation if it must be written.
- **Test setup and log locations** are in memory (*test-server-paths*). Reads on holly over ssh work;
  writes are refused by the permission classifier, so hand the user the command.
- **Deploy only when asked.** The user copies the jar to both machines and restarts.
- **Reading an unfamiliar Minecraft API:** `javap` against the Mojmap merged jar under
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings…/`.
  Architectury 19.0.1's jar is intermediary-named (`class_1657` = `Player`).
- **Reading a session:** the server log dumps the whole prompt every turn; filter for `<Dauk808>`,
  `Processed LLM`, `User Task Set`, `Build (`, `WARN`, `ERROR`, and drop `no standing position` and
  `Refreshed inventory`. The client log has the model calls (`Called/Finished complete conversation`)
  and chat. Each turn's `inventory :` block in the server log shows what she carried.
- Model results must reach the agent through `reportCommandResult` / `logAgentNotice`, never
  `mod.log()`.
