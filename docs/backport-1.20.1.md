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

## Checked and not applicable

- **Client-only mixins crash a dedicated server** (1.21.11 `e3a143a`, 2026-09-03).
  `PlayerCollidesWithEntityMixin` and `ClientBlockBreakMixin` do not exist on 1.20.1, and its
  `mixins.altoclef.json` has no client-only targets listed under `"mixins"`.
