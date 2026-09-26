# Continue: remaining fixes on `mc/1.21.11` (after 2026-09-25)

Handoff for the next session. Read this, then `docs/backport-1.20.1.md` (entries 8–12 are from this
session). The workspace-root `CLAUDE.md` is for **Minecraft 26.1.2 and does not apply here**. This
branch is **Minecraft 1.21.11, Mojmap in both the engine and the mod half**.

## Where things stand

Committed on `mc/1.21.11` (not pushed; the user pushes):

| Commit | What |
|---|---|
| `4f364e6` | An unbounded wander gives up after about a minute standing still and fails the *root* of the user's command |
| `dcaef42` | `chests` / `withdraw` / rebuilt `deposit`; `reportCommandResult` puts command results in the finish event; two item dupes fixed |
| `44f2546` | `WorldHelper` floors coordinates (`BlockPos.containing`). `(int)` walled a companion in at negative X/Z and it suffocated |
| `6204c3f` | Pathfinder treats powder snow as an obstacle; `UnstuckChain` breaks out from any cell the body touches |
| `0fe7af2` | Prompt: "come here" means the speaker's current position; honest `get` claims. Voice capped at about 160 chars; no stall warning while speaking |
| `dbebe35` | The build planner's model call goes to the owner's client (`brain_plan_request`), since a dedicated server has no key |
| `e7fcbe0` | Engine `1.21.11-1.0.26`, mod repinned |
| `e72789e` | Backport ledger entries 8–12 |

**Deployed build:** engine 1.0.26 inside `aicompanion-0.4.0+mc1.21.11.jar`, on both the test server and
the client. Each has the previous jar beside it as `.jar.bak`.

**Verified in play:** `chests` and `withdraw` end to end; the no-sheep wander no longer hangs; two
builds planned on the client and built (121 blocks, then 16); one build recovered by itself from a
503 on retry.

**Not yet verified in play:** the powder-snow changes (no companion has sunk into a drift since);
"come here" and honest-`get` prompt lines; the voice cap on a long answer.

## Fix next, in this order

### 1. Plan requests need their own, longer timeout 🔴 blocks bigger builds
`NetworkBrainTransport.completeTextOnClient` arms its timeout with `LlmConfig.clientBrainTimeoutMs`,
the **turn** timeout (45 s on the test server). A free model took **46–57 s** per full-size plan, so
every second-storey plan timed out and was dropped even though the client finished it.

- Give plans a separate budget, for example `max(clientBrainTimeoutMs, 180_000)`, or a new
  `brain.planTimeoutMs` key. If you add a key, also add it to `DEFAULT_JSON` and regenerate
  `config.example.json` from it (see memory: *config.example.json is generated*).
- Stopgap already offered but **not applied**, pending the user's decision: raise `brain.clientTimeoutMs`
  to `120000` in the server's `config/aicompanion-server.json`. Turns are timing out too with the
  larger model the user switched to.

### 2. No extra plan request after the last attempt
`BuildStructureTask.onTick`: the error branch adds the retry message and creates a new
`RequestLLMCode` (which sends immediately in `onStart`) **before** the `numErrors > maxNumErrors` check
runs on the next tick. So a failed build sends a 4th request whose answer is delivered to a dead task.
It happened: a plan arrived 31 s after its build was abandoned. Check the count before creating the
next request.

### 3. Failure messages that blame the wrong thing
- **Timeout:** `NetworkBrainTransport.Pending.fail()` calls `runOnServer(null)`, so `adviseOn(null)`
  says *"your client did not say why. Check llm.endpoint…"*. The owner is sent to a working endpoint.
  Pass the timeout reason through, and advise "your model took longer than N s", naming
  `brain.clientTimeoutMs` on the server. Add a case to `BrainFailureAdviceTest`.
- **Build:** `BuildStructureTask` always tells the owner *"I couldn't come up with a workable plan"*,
  even when the cause was a timeout or a 503. Word it from `lastError`.

### 4. `/companion reload` does not re-apply an edited persona
`CompanionConfig.entryFor()` prefers the identity saved on the entity (`CompanionEntity` NBT
`Identity`), so reload re-applies the **old** persona. A restart does too. Only despawn and spawn
picks up the edit. The reload message wrongly says *"persona re-applied"*, and says only
name/description/skin need a respawn. The "saved copy wins" rule is deliberate for dedicated servers:
the operator's file must not rewrite other players' companions. A fix could take the persona from the
**owner's own current roster** when a companion by that name is still in it. For a LAN host that
roster (`ClientProfiles`) is only announced at join, so reload must re-announce it too. At minimum,
correct the message.

### 5. `scan` never tells the model anything
`ScanCommand` reports with `mod.log()`, which is stdout only. Use `mod.reportCommandResult(...)` (from
`dcaef42`). Check the other commands for the same pattern while you're there.

### 6. Decisions for the user (ask, don't assume)
- **Should `get` use chests on its own?** `ResourceTask.allowContainers` is false, so `get` never looks
  in chests; the prompt tells the model to use `chests` / `withdraw`. Turning it on would have fixed the
  original wool situation, but it silently takes from anyone's chest.
- **Chest ownership:** `chests` / `withdraw` / `deposit` use every storage container within 16 blocks,
  whoever placed it. In a team event that can include another team's chest.

### 7. Low priority
- `ConversationHistory.addHistory(..., doCutOff=true)` summarises past 64 messages with a server-side
  model call. On a keyless server it fails and drops the oldest message. Harmless, but it is the last
  server-side model call without a route to the client.
- Error logging is noisy: one client failure logs WARN, WARN and ERROR on the server.
- The free Nvidia models on OpenRouter return 503 "overloaded" often. The user accepts this as the
  cost of free models; don't "fix" it.

## Working notes

- **Build (JDK 21 for 1.21.11; JDK 17 is only for the 1.20.1 line):**
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  cd engine && ./gradlew :common:test :fabric:build
  cp fabric/build/libs/playerengine-fabric-1.21.11-<ver>.jar ../aicompanion/libs/PlayerEngine-1.21.11-<ver>.jar
  cd ../aicompanion && ./gradlew build     # -> build/libs/aicompanion-0.4.0+mc1.21.11.jar
  ```
  Bump `mod_version` in `engine/gradle.properties` **and** `playerengine_version` in
  `aicompanion/gradle.properties` for every engine change, or Loom serves a stale remapped jar. The
  readme's build section describes the 1.20.1 line.
- **Test setup and log locations** are in memory (*test-server-paths*). The live server is the
  `minecraft_companion_mod_1.21.11` directory, not `minecraft_server_1.21.11` (that one is vanilla).
- **Deploy only when asked.** Copy to a temporary name, then `mv` into place (a running JVM keeps its
  open jar), and keep the old jar as `.jar.bak`. The user restarts the server and the game.
- **Reading an unfamiliar Minecraft API:** `javap` against the Mojmap merged jar under
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings…/`.
  The plain `minecraft-merged.jar` has intermediary names.
- **Watching a play session:** `tail -F` both logs through the Monitor tool, grepping for commands,
  plan events, deaths and errors. Filter out `no standing position` lines; builds log one per
  remotely placed cell.
- Model results must reach the agent through `reportCommandResult` / `logAgentNotice`, never
  `mod.log()`.
