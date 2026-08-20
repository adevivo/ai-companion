# Moving the brain to the client

Written 2026-08-19. Supersedes the multiplayer sequencing in KB
`aicompanion-persistent-companion-service` only where noted; the architecture there still stands.

## Why, now that the paid service is shelved

The hosted memory service is **shelved** (decided 2026-08-19). With the brain on the client, a
player's corpus lives on their own machine and follows them across every world and every server for
free, so the service's remaining value was multi-device sync and backup — not enough to sell, and
not enough to build. **Memory stays file-based, locally, permanently.**

That removes the *commercial* reason for this work but not the others, and the others were always
the stronger ones:

1. **`LlmConfig` is server-global**, so on a dedicated server the owner funds every player's tokens.
   That is a hard adoption blocker and it is live today on holly.
2. **Per-player everything falls out for free** — personas, memory, rate limits, keys.
3. **The player's memories stay on the player's machine**, which is the privacy story the shelved
   service was going to charge for and which now costs nothing.

## What is already done

**Step 1 of the four-step plan in `aicompanion-dedicated-server-support` is complete.** Verified
2026-08-19: `TimerGame` runs off `ServerClock.current()` (a `MinecraftServer`) with the reconnect
offset guard kept and keyed on server identity, exactly as that entry prescribes. The only remaining
`net.minecraft.client` references in `engine/` are the four render-only baritone files that entry
lists as "verify never load server-side", and holly has been running the mod for days.

**Step 2 is smaller than recorded.** That entry calls de-static-ing `ConversationManager` "a larger
refactor than the client-only class fixes". In fact `queueData` is *already* per-companion
(`ConcurrentHashMap<UUID, AgentConversationData>` keyed on the companion entity). What is genuinely
server-global is `LlmConfig` / `MemoryConfig` / `EmbeddingsConfig` and the shared `llmCompleters`
pool — and **moving the call to the client makes per-player config automatic**, because the client
uses its own. So step 2 largely dissolves into step 3 rather than preceding it.

## Consequence of shelving: the wire-format blockers unblock

`ai-companion-memory/docs/memory-service-plan.md` lists three things that had to be settled before
the service stored a byte. With no service and a single writer per install:

| blocker | status now |
|---|---|
| `serial` conflates ordering with sync cursor | **moot** — no cursor, one writer, no ties |
| per-record blobs make `vectorRow` meaningless | **moot** — sidecars stay local |
| nothing records the embedding model | **still worth doing, no longer urgent** — a player who changes embedder silently turns their own corpus into noise |

None of them block this work. Keep the third on the list.

## Stages

### Stage A — extract the brain-turn seam ⬅️ start here

Introduce a `BrainTransport` boundary in `AgentConversationData.process()` and a `LocalBrainTransport`
that does exactly what happens today. **No behaviour change, no packets, nothing user-visible.**

Memory goes on the *transport* side of the boundary from the start, so the request carries the turn
text rather than a prompt with memories already baked in. That is what makes stage C a matter of
implementing the same interface over the network instead of reshaping the DTO.

```java
record BrainTurnRequest(UUID companionUuid, UUID ownerUuid, String companionName,
                        String turnText, String worldId, JsonArray messages)
record BrainTurnResult(UUID companionUuid, JsonObject reply, String error)
```

**Exit:** build green, 92 engine tests pass, companion behaves identically in game.

### Stage B — network transport, brain still server-configured

Two channels (`brain_turn_request` S2C, `brain_turn_result` C2S) alongside the six that already
exist. Client handler calls the LLM with **its own `LlmConfig`**.

⚠️ **Fallback is mandatory, not a nicety.** A vanilla client, an out-of-date client, or one that
never answers must fall back to `LocalBrainTransport` rather than freezing the companion. Timeout
plus a capability handshake at join.

⚠️ **Two LLM call paths.** `BuildStructureTask` builds its own `LLMCompleter` (line ~1489), separate
from `ConversationManager`'s pool. The build path is the expensive one and the easy one to miss.

**Exit:** singleplayer unchanged; on holly the *client's* key is spent, verified by the usage report
appearing client-side and the server's counters staying flat.

### Stage C — client-side corpus

`CompanionMemory`, `MemoryStore`, `EmbeddingsService`, `MemoryGate`, `MemoryLearner` run client-side;
the corpus moves from the server's config dir to the client's. Singleplayer is unaffected (one JVM,
one config dir). On a dedicated server the memories stop living on holly.

⚠️ Migration: existing corpora under holly's `config/aicompanion/memories/<uuid>/` do not move
themselves. One player, one directory — a documented manual copy is fine, an automatic migration is
not worth writing.

### Stage D — command validation (trust inversion)

Once the client tells the server what the companion does, every command is hostile input. Same
surface as the land-claim problem (the companion is a `LivingEntity`, so claim mods hooking player
block-break events cannot see it) — solve together.

**This stage is not optional before anyone else's server runs the mod**, but it is not needed for a
family LAN where every client is trusted. Sequenced last deliberately, and the mod should refuse to
enable client-brain on a server it does not own until this exists.

## Not doing

- **The hosted service.** Shelved 2026-08-19. `memory-service-plan.md` and `memory-service.md` stay
  in the memory repo as a record of a design that was thought through and not built.
- **Client-side embedding model swaps, sync, multi-device.** All were service features.
- **Server TPS work** (per-server companion cap, pathfinding budget). Real, flagged in the KB as the
  likely adoption blocker, and orthogonal to this.
