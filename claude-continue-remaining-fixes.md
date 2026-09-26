# Continue: remaining fixes on `mc/1.21.11` (after 2026-09-26)

Handoff for the next session. Read this, then `docs/backport-1.20.1.md` (entries 13–15 are from this
session). The workspace-root `CLAUDE.md` is for **Minecraft 26.1.2 and does not apply here**. This
branch is **Minecraft 1.21.11, Mojmap in both the engine and the mod half**.

## Where things stand

Committed on `mc/1.21.11` (not pushed; the user pushes):

| Commit | What |
|---|---|
| `4e7273d` | Docs: build benchmark, single-pass decisions design note |
| `6bac1fb` | Plans get `brain.planTimeoutMs` (180 s); no request after the last attempt; a timeout gives up at once; failure advice names the timeout key or the 503 instead of `llm.endpoint` |
| `8eebd34` | `ChestPermissions`: `chests` / `withdraw` / `deposit` use only containers the owner allowed. Allowed by opening it, `usechest`, or a deposit placing it; `forgetchest` removes one |
| `fd5418b` | `get` withdraws from allowed containers first (`GetFromStorageFirstTask`), then gathers the rest |
| `8db4088` | `scan`, `eat`, `give`, `stand_ground`, `get` report to the agent instead of `mod.log()` |
| `ee31043` | `/companion reload` no longer claims it re-applied an edited persona |
| `db21b90` | Engine `1.21.11-1.0.29`, mod repinned |
| `1b938a3`, `30e67c9` | Backport ledger entries 13–15 |

**Built, not yet deployed:** `aicompanion/build/libs/aicompanion-0.4.0+mc1.21.11.jar` with engine
1.0.29. The deployed build is still engine 1.0.26. The user deploys to both client and server.

**Server config:** `brain.clientTimeoutMs` raised 45000 → 120000 in holly's
`config/aicompanion-server.json` (backup beside it as `.bak`). It takes effect at the next restart or
`/companion reload`. **Confirm it in the log or with a slow turn.**

## Verify in play (first thing next session)

1. The 120 s turn timeout loaded; a two-storey build plans without timing out.
2. `withdraw` from a chest nobody has opened: the companion skips it and asks, not takes.
3. Open that chest yourself, `withdraw` again: it works.
4. Stand at another chest, say "use this chest": `usechest` runs and it is allowed.
5. Put wool in an allowed chest, `get white_wool 3`: it takes from the chest, no sheep hunt.
6. `scan` for a block: the companion answers with a position.
7. Carried over, still unverified: powder snow, "come here", honest `get`, the voice cap.

## Open

- **Persona reload (only the message was fixed).** A roster edit still needs despawn and spawn. The full
  fix would take the persona from the owner's own current roster when a companion of that name is in
  it, and re-announce `ClientProfiles` on reload for a LAN host. Not worth it unless personas are
  edited often.
- **`docs/config.example.json` has drifted.** It lacks the whole `clientBrain` / `clientBrainTimeoutMs`
  / `clientPlanTimeoutMs` group. Regenerate from `CompanionConfig.DEFAULT_JSON` (see memory:
  *config.example.json is generated*).
- **Chest permission choices the user may revisit:** no `usechest all`; opening a chest always allows
  it (no config switch); a deposit with no allowed container places a new chest rather than asking.
- **`get` does not look for ingredients in chests.** `get wooden_pickaxe` takes pickaxes from chests,
  not planks. Ingredient lookup would mean turning on `ResourceTask.allowContainers`, whose branch must
  first be moved to the permission-checked, dupe-free path.
- Low priority, unchanged: `ConversationHistory` summarising on a keyless server; noisy WARN/WARN/ERROR
  on one client failure; free Nvidia models return 503 often (accepted, do not "fix").

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
- **Settings that belong to one line go in the engine, not the core.** `LlmConfig` and `ServerPolicy`
  live in `ai-companion-memory` (core 0.5.0), shared with 1.20.1; a new field there means a core
  release. `NetworkBrainTransport.planTimeoutMs` is the engine-side precedent.
- **SavedData on 1.21.11:** copy `WorldIdentity` / `ChestPermissions` — one static `SavedDataType`, a
  real `DataFixTypes`, `setDirty()` on creation if it must be written.
- **Test setup and log locations** are in memory (*test-server-paths*). Writes to holly over ssh are
  refused by the permission classifier; hand the user the command.
- **Deploy only when asked.** Copy to a temporary name, then `mv` into place, and keep the old jar as
  `.jar.bak`. The user restarts the server and the game.
- **Reading an unfamiliar Minecraft API:** `javap` against the Mojmap merged jar under
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings…/`.
  Architectury 19.0.1's jar is intermediary-named (`class_1657` = `Player`).
- **Watching a play session:** `tail -F` both logs through the Monitor tool, grepping for commands,
  plan events, deaths and errors. Filter out `no standing position` lines.
- Model results must reach the agent through `reportCommandResult` / `logAgentNotice`, never
  `mod.log()`.
