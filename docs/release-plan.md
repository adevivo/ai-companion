# Release plan — 0.2.6 through 0.2.9

What ships in which version, and why it's split that way. Companion docs: [PLAN.md](../PLAN.md) is the
long-term phase architecture; [BUGS.md](../BUGS.md) is the working defect list. This doc is scheduling.

Written to be implementable in a fresh session with no prior context. MC 1.20.1, Fabric, JDK 17.
**0.2.6 and the survival half of 0.2.7 have shipped** — current version is `0.2.7`
(`aicompanion/gradle.properties:4`), engine `1.0.58`. What remains in 0.2.7 is the stance system.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build
```

Don't deploy or restart the server — hand over the jar path.

---

## Why these changes at all

Four problems.

1. **Combat reads as cheating.** Companions shred zombies and creepers at a rate that looks like
   one-hit kills. Two independent defects, not a tuning problem: a zombie stat line, and a cooldown
   gate wired to a counter that is permanently zero.
2. **Stir-crazy demolition.** A companion idling indoors sometimes decides to mine the building it's
   standing in. Nothing in the codebase prevents it — grep for `protect|allowlist|forbid|claim` across
   `aicompanion/src` and `player2api/` returns zero hits. `dig`, `get`, and `build_structure` are
   always advertised, in every situation.
3. **Token cost for routine work.** Repetitive loops (chop, farm, fish) run through the LLM command
   loop one step at a time. `/companion come` is free; "come here" is not. Skills are prompt injection
   by design (`CompanionSkills.java:20`), so a 200-log chopping session is ~200 LLM round trips.
4. **Untidy behavior.** Doors left open. Baritone opens them to path through and has no concept of
   closing them.

Problems 2–4 share a root cause: the LLM is the *only* arbiter of what the companion does, so every
decision costs tokens and every decision can be wrong. The fix is a deterministic layer that owns
routine and safety, leaving the LLM for novel and social decisions.

## Why it's four releases and not one

Three of these items each independently change how the companion *feels*, and two of those are in
combat. Shipped together, a playtester reporting "it feels off now" leaves you with no way to
attribute the change.

The grouping rule used below:

- **0.2.6** — everything that changes combat, plus one standalone safety fix that needs no new
  machinery. One subject, one playtest.
- **0.2.7** — stances, plus the survival work (healing cost, health-aware retreat) that 0.2.6's combat
  change made necessary. New subsystem, changes what the companion will agree to do, needs its own
  playtest.
- **0.2.8** — protections and doors. Grouped because they share one unknown (where Baritone commits a
  block break and a door interaction). One spike serves both.
- **0.2.9+** — the job chain. Largest, and it's an architecture decision that needs its own doc.

Note that 0.2.6 and 0.2.7 have **verified seams with line numbers** throughout. 0.2.8 does not — that's
the tell that it's a research spike wearing a task's clothing, and why it isn't scheduled earlier.

---

# 0.2.6 — Combat honesty

Companions fight like players. Plus one command-advertisement fix that has no dependencies and
shouldn't wait.

## Combat Bug A: companions have a zombie's stat line, not a player's

`AiCompanion.java:82`:

```java
return ZombieEntity.createAttributes()
        .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0);
```

`ZombieEntity.createAttributes()` gives `ATTACK_DAMAGE 3.0`, `ARMOR 2.0`, `MAX_HEALTH 20`,
`MOVEMENT_SPEED 0.23`, `FOLLOW_RANGE 35`. Vanilla `Player` is `ATTACK_DAMAGE 1.0`, `ARMOR 0.0`.

So before any gear is considered:

| | Player | Companion | Delta |
|---|---|---|---|
| Bare-handed damage | 1.0 | 3.0 | **3×** |
| With diamond sword (+7) | 8.0 | 10.0 | **+25%** |
| Armor with no armor equipped | 0.0 | 2.0 | **+2 free** |

This is the part that reads as cheating, and it's a two-line fix. The `ATTACK_SPEED 4.0` addition is
correct and should stay — that genuinely is the vanilla player base, and the comment explaining why it
was added is accurate.

**Fix:** set the combat attributes explicitly for player parity rather than swapping in
`PlayerEntity.createPlayerAttributes()` wholesale:

```java
return ZombieEntity.createAttributes()
        .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0)
        .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)   // was 3.0 (zombie)
        .add(EntityAttributes.GENERIC_ARMOR, 0.0);          // was 2.0 (zombie)
```

**Do not touch `MOVEMENT_SPEED` in the same change.** It's load-bearing — Baritone drives this entity
through input overrides, and the mob-scale `0.23` may be doing real work that the player-scale value
wouldn't. If it needs tuning, tune it as its own change with its own playtest, so a movement regression
isn't tangled up with a combat fix.

`FOLLOW_RANGE 35` is worth a look too. It governs how far the companion can target, and 35 blocks is a
mob's aggro range, not a person's reasonable engagement distance. Consider ~16 — and once stances exist
(0.2.7), let the stance system own "how far will you go to fight something."

## Combat Bug B: the swing rate

> **Corrected 2026-08-04.** An earlier draft of this section claimed there were *two* attack-cooldown
> counters — a private `CompanionEntity.lastAttackedTicks` that worked, and a vanilla
> `attackStrengthTicker` that sat at zero forever and made every gate in the engine dead code. **That
> was wrong, and the fix it proposed was a no-op.** They are the same field. Verified below. The
> section is rewritten around what's actually broken; the symptom analysis survived, the mechanism
> didn't.

### What was verified

`CompanionEntity` declares no cooldown field of its own — grep the file, there is no declaration. The
`lastAttackedTicks` it increments is **inherited from `LivingEntity`**, and it is the same field the
engine reads:

| | aicompanion (Quilt mappings) | engine (Mojmap + Parchment) |
|---|---|---|
| 7th `int` field of `LivingEntity` | `protected int lastAttackedTicks` | `protected int attackStrengthTicker` |

Same position, same modifier — confirmed by `javap` against both mapped jars in the Loom cache. The two
gradle projects use different mappings (`aicompanion/build.gradle:38` quilt-mappings,
`engine/build.gradle:48` official Mojang + Parchment), which is what made them look like two fields in
source.

The loop is closed and live:

- `CompanionEntity.tick()` (`:281`) increments it unconditionally every tick.
- `PlayerExtraController.attack()` (`:41-46`) calls `doHurtTarget()` — Mojmap for the `tryAttack()`
  that `CompanionEntity` overrides — which resets it to 0 (`:737`).
- `KillAura.getAttackCooldownProgress()` reads it through the mixin accessor.

So the gates **do** fire, the melee kill task **does** swing, and the charge curve in `tryAttack()`
**is** being applied against a real counter. Anything built on "the counter is zero" is void.

### What's actually wrong — two things

**B1 — `AbstractKillEntityTask` swings 2.5× too fast.** `getAttackCooldownProgressPerTick` returned a
flat `5.0F` (`AbstractKillEntityTask.java:85`): a full-strength swing every 5 ticks, four a second, no
matter what's held. A diamond sword's real figure is 12.5 ticks. This is the exact bug already fixed in
`KillAura.java:156-163` by reading the `ATTACK_SPEED` attribute; the fix was never applied here.

Not masked, not theoretical — this gate passes on every tick past the too-short cooldown, and each of
those swings lands at **full charge** because the gate only opens once `getAttackCooldownProgress()`
reaches 1.0. Four full-damage hits a second. This is the single largest contributor to the melee path
looking like one-hit kills, and it is a genuine live defect.

**B2 — one genuinely ungated melee path, and one deliberate exemption.** The original draft listed
three ungated `attack()` calls and treated all three as the same bug. Two of them are not:

| Site | Guard | What it actually is |
|---|---|---|
| `KillAura.java:112-113` (`SMART` + `forceHit`) | *(none)* | **ghast fireball only** — keep ungated |
| `KillAura.java:128-130` (`performDelayedAttack`, before its own check) | *(none)* | **ghast fireball only** — keep ungated |
| `performFastestAttack` | *(none)* | genuinely ungated melee — **gate it** |
| `performDelayedAttack` melee target | `progress < 1.0F` | already correct |

`forceHit` is assigned in exactly one place — `KillAura.applyAura`, `if (entity instanceof
LargeFireball)` — and `tickStart()` clears it every tick. So it is **always a ghast fireball and can
never go stale**. Batting a fireball back has no damage-charge component and a player does it by
spam-clicking; gating it would make the companion strictly worse at it than the person standing next to
it. Left ungated, commented at all three sites so it doesn't get "fixed" later.

That leaves `performFastestAttack`, reachable only via `Strategy.FASTEST` — not the default
(`Settings.java:59` is `SMART`).

**Which means KillAura was already behaving under default settings.** Against zombies and creepers with
no ghast around, `SMART` routes to `performDelayedAttack`, whose melee path was correctly gated all
along. **B1 was the whole of the machine-gun problem.**

The symptom analysis still holds for what B1 was doing, just at 4 swings/second rather than 20: each
one at full charge, with knockback applied on every swing (`:754-758`), which stunlocks the target so it
never gets to act. **Not literal one-hit kills** — a stunlock plus a fast swing animation. Arguably the
stunlock is the worse half, because it removes any sense that the fight was contested.

### The fixes

1. **B1** — `AbstractKillEntityTask.getAttackCooldownProgressPerTick` reads `ATTACK_SPEED`, mirroring
   `KillAura`, with the old `5.0F` kept as a fallback for entities lacking the attribute. **Done.**
2. **B2** — `performFastestAttack` now respects the cooldown. `FASTEST` stays selectable: it's existing
   working code and the default is already the honest one. What still distinguishes it from `DELAY` is
   breadth (all targets in range vs. the nearest), not rate. **Done.**
3. **Expect companions to get noticeably worse at fighting.** That is the point, but it means combat
   needs a real playtest: a companion that can no longer stunlock will actually take damage, which
   exercises `FoodChain`, `MobDefenseChain`, and retreat behavior in ways they haven't been before.

### The grass-block swinging — one hypothesis eliminated

The original doc's leading theory was `forceHit` holding a stale entity reference after its target
died. **Ruled out:** `tickStart()` clears `forceHit` every tick and only a `LargeFireball` ever sets it,
so it cannot persist and cannot point at a mob. The remaining lead is `LookHelper.lookAt` aiming at a
mining target while swings continue independently. Retest after B1 before investigating further —
4-swings-per-second at a block may simply have been what was being seen.

The engine is a separate bundled artifact (`Bundles PlayerEngine 1.0.55`), so both fixes need an engine
version bump.

### Why A and B ship together

Bug A cuts damage; Bug B cuts swing rate. Ship A alone and a tester still sees the stunlock — they'd
report "still feels like cheating" and conclude the parity fix didn't work. One changelog story, one
playtest.

### Lesson for future analysis

The mistake was reading two source trees with different mappings and treating a rename as a distinct
field. Anything in this repo that compares an `aicompanion` symbol against an `engine` symbol has to be
checked against the mapping tables, not against the source text. `javap` on the two jars under
`~/.gradle/caches/fabric-loom/minecraftMaven/` settles it in one command.

## Combat config

Once parity is restored, expose the knobs so servers can opt into stronger companions deliberately
rather than getting it by accident:

```
combat.attackDamageBase   (default 1.0 — player parity)
combat.armorBase          (default 0.0)
combat.maxHealth          (default 20.0)
combat.followRange        (default 16.0)
```

Deliberate opt-in is fine. Silent 3× bare-handed damage is what people mean by "feels like cheating."

## The `NEVER` command bucket

Independent of everything else in this release, and independent of the stance system in 0.2.7 — it
needs none of that machinery. It rides along in 0.2.6 because it's a live safety bug, not an
enhancement.

Four of the 24 registered commands are owner-only plumbing that is currently advertised to the model as
things it may choose to do:

```
gamer, chatclef, reload_settings, resetmemory
```

A companion wiping its own memory because it "seemed like a fresh start" is a bug waiting to happen.
These should be `/companion` subcommands, not LLM commands.

Remove them from the collection passed to `Prompts.getAINPCSystemPrompt(...)` at the two call sites in
`AIPersistantData.java` (`:22` and `:60`), and reject them in `CommandExecutor.executeRecursive()`
(`CommandExecutor.java:34,58`) in case they show up from conversation history.

In 0.2.7 this becomes a bucket in `CommandPolicy`. For 0.2.6 a hardcoded set is fine — don't build the
policy class early just to hold four strings.

## 0.2.6 ship checklist

- [x] Bug A — attribute parity in `AiCompanion.createCompanionAttributes`
- [x] Combat config keys, applied per-entity via `CompanionEntity.applyCombatConfig()`
- [x] B1 — `AbstractKillEntityTask` reads `ATTACK_SPEED`
- [x] B2 — `performFastestAttack` gated; fireball carve-out documented
- [x] Owner-only bucket — `CommandExecutor.OWNER_ONLY`, filtered from the prompt via `agentCommands()`,
      rejected on the agent dispatch path in `AgentSideEffects`
- [x] Engine bumped to 1.0.56, mod to 0.2.6; both build
- [ ] **Combat playtest** — companion takes damage now; watch `FoodChain`, `MobDefenseChain`, retreat
- [ ] Retest the grass-block swinging; investigate only if it survives B1
- [ ] Changelog entry

---

# 0.2.7 — Stances, and surviving a fight

Two threads. Stances make unsafe actions *impossible* rather than discouraged and shrink the system
prompt on every call. Healing and retreat make 0.2.6's combat change actually mean something — right
now a companion regenerates faster than anything can hurt it, and its decision to run doesn't consider
whether it's hurt.

**Note for scheduling:** the healing fix is the one item here that arguably belongs in 0.2.6. A
companion that heals a full heart every half second is still effectively unkillable, so the 0.2.6
combat playtest will under-report the change. Deferred deliberately, but if the playtest is
uninformative, this is why — pull it forward rather than re-tuning combat.

## Verified seams

| What | Where |
|---|---|
| Prompt template with `{{validCommands}}` | `engine/.../player2api/Prompts.java:101` |
| Command list built from a `Collection<Command>` | `Prompts.getAINPCSystemPrompt(...)` — `Prompts.java:104-121` |
| Called with `mod.getCommandExecutor().allCommands()` | `AIPersistantData.java:22` **and** `:60` |
| **Hot-swap hook that already exists** | `AIPersistantData.updateSystemPrompt()` — `AIPersistantData.java:59` |
| Rewrites the base prompt without dumping history | `ConversationHistory.setBaseSystemPrompt()` — `ConversationHistory.java:178` |
| Registry (24 commands) | `AltoClefCommands.init()` — `AltoClefCommands.java:30` |
| Execution path (enforcement point) | `CommandExecutor.execute()` → `executeRecursive()` — `CommandExecutor.java:34,58` |

`updateSystemPrompt()` existing already is the lucky break here — stance changes re-advertise through a
method that is already written and already wired.

## The stance enum

New file: `aicompanion/src/main/java/com/neovetta/aicompanion/stance/Stance.java`

```java
public enum Stance {
    ESCORT,   // default. bodyguard: stays with owner, fights, no world modification
    WORK,     // jobs and skills. full block modification, within protections
    PARKED,   // sits. talks only. no movement, no combat initiation
    FREE      // everything (today's behavior). opt-in via /companion stance free
}
```

`ESCORT` is the default because it matches how the mod is actually used and is the safest useful state.
`FREE` exists so nobody loses capability they had in 0.2.6 — it's an escape hatch, not a default.

**`GUARD` is deliberately deferred.** The earlier draft had a fifth stance for holding a position and
defending an area. Two problems: it breaks the ordinal ladder (it *removes* `follow` rather than adding
anything), and its actual value — hold anchor, patrol radius, return after combat — lives in the
rewritten `home-guard.md` skill below, which is prose for the LLM, not code. A `GUARD` stance shipped
now would be a mode that only subtracts commands and adds no behavior. Add it when there's real
area-defense code behind it.

## Command classification

All 24 registered names, verified from each command's constructor. Tag each with a minimum stance rather
than an explicit per-stance list — fewer places to get out of sync.

```java
// Stance.java or a companion CommandPolicy class
ALWAYS  = { idle, stop, bodylang, status, inventory, list, food, meat, scan,
            locate_structure, eat, give, equip }
ESCORT+ = { follow, goto, attack, hero }
WORK+   = { get, dig, farm, fish, deposit, build_structure }
NEVER   = { gamer, chatclef, reload_settings, resetmemory }   // shipped in 0.2.6
```

With `GUARD` deferred, a strict ordinal ladder works — `ESCORT ⊂ WORK ⊂ FREE`. If `GUARD` arrives later
it breaks that, so prefer `EnumSet<Stance> allowedIn` per command or a `Map<Stance, Set<String>>` even
now; the ladder framing is a readability aid, not a commitment about the data structure.

## Where the filter goes

Keep `Prompts` dumb — it's a formatter. The signature at `Prompts.java:104` stays as-is:

```java
public static String getAINPCSystemPrompt(Character character,
                                          Collection<Command> altoclefCommands,
                                          String ownerUsername)
```

Filter at both call sites in `AIPersistantData`:

```java
// AIPersistantData.java:22 and :60 — identical change
String systemPrompt = Prompts.getAINPCSystemPrompt(
        character,
        CommandPolicy.visibleIn(mod.getStance(), mod.getCommandExecutor().allCommands()),
        mod.getOwnerUsername());
```

`AltoClefController` needs a `Stance` field with a getter/setter; the setter calls
`aiPersistantData.updateSystemPrompt()` so the change takes effect on the next turn without restarting
the conversation.

## Stance persists — decided

**Resolved: stance survives a relog.** The alternative was every companion silently reverting on world
load, which a user who parked one in `WORK` would rightly file as a bug.

It goes in **entity NBT**, not a config file and not `SavedData`. Stance is per-companion state that
should travel with the body — the same argument that already puts `RosterName` and `Owner` there.
`CompanionEntity` has the read/write pair already (`readCustomDataFromNbt` `:213`,
`writeCustomDataToNbt` `:231`); this is two lines in each, following the `RosterName` pattern exactly.

Store the enum **by name, not ordinal**. Ordinals renumber the moment `GUARD` is added back and every
saved companion silently changes stance.

### The migration default is not the spawn default

Worth getting right, because the obvious implementation is wrong. A companion saved under 0.2.6 has
today's full capability. If it loads with no stance tag and falls back to `ESCORT`, it silently loses
`dig`, `farm` and `build_structure` — an existing companion halfway through a job would just stop being
able to do it, with nothing explaining why.

So:

- **Absent tag** (saved before stances existed) ⇒ `FREE`. Preserves exactly what that companion could
  already do. `tag.getString` returns `""` when the key is missing, which is the signal.
- **Fresh spawn** ⇒ `ESCORT`. The safe default only applies to companions created after the feature
  exists.

Say this plainly in the changelog: existing companions keep full capability and need `/companion stance
escort` to opt in; new ones start escorted.

### Not shared with 0.2.8

Protected regions are world state and belong in `SavedData`/JSON. Stance is entity state. There's no
common store to build and no reason to couple the two releases.

## Enforcement, not just advertising

Filtering the prompt is necessary but not sufficient — a model that saw `dig` in an earlier turn's
history will occasionally emit it anyway. Gate the execution path too, in
`CommandExecutor.executeRecursive()` (`CommandExecutor.java:34`), right where the `command == null`
branch already lives:

```java
if (!CommandPolicy.allowed(mod.getStance(), command.getName())) {
    getException.accept(new CommandException(
        "You cannot use `" + command.getName() + "` while in " + mod.getStance()
        + " stance. Ask your owner to change your stance, or pick another command."));
    this.executeRecursive(commands, parts, index + 1, onFinish, getException);
    return;
}
```

The reason this shape matters: that `getException` consumer already routes back to the model through
`gameDebugMessages` in the situation packet (`docs/brain-contract.md:36-41`). So a stance rejection is
**self-correcting** — the model sees why it failed and picks something legal next turn. Same channel the
existing "Invalid command" error uses. No new plumbing.

This generalises the hardcoded `NEVER` rejection from 0.2.6; fold that into `CommandPolicy` here.

## Player-facing

```
/companion stance <name> [escort|work|parked|free]
/companion stance                       # report current stance
```

Register in `CompanionCommands.java` alongside the existing subcommands (the `literal("...")` chain
starts at line 48). Free to invoke — this is the point.

Also worth surfacing stance in `CompanionRadarHud` or the nameplate, so it's visible at a glance which
mode each companion is in.

## Rename `home-guard` → `bodyguard`, and write a real `home-guard`

Ships here because it's trivial once stances exist, and it makes stances visible to users on day one.

The current file is misnamed. Its body
(`aicompanion/src/main/resources/aicompanion/skills/home-guard.md`):

> *"Follow your owner and keep within about eight blocks of them... Do not wander off to chase distant
> mobs — your job is to guard, not hunt."*

That is a bodyguard. It's escorting a person, not defending a place.

1. **`bodyguard.md`** — current content, renamed. Update the `# Home Guard` heading to `# Bodyguard`
   (the heading is what generates the invocation key, `CompanionSkills.java:96-107` — the filename only
   matters as a fallback).
2. **`home-guard.md`** — rewritten as genuine area defense: hold an anchor point, patrol a radius,
   engage hostiles that enter it, return to the anchor after combat, never pursue beyond the radius.
   This is `MoveBackToGuardGoal` + `PatrolGoal` from ModernCompanions, and it's what most people expect
   from the name.
3. **`BUNDLED` array** in `CompanionSkills.java:45-47` — add `bodyguard.md`, keep `home-guard.md`. Both
   ship.
4. **Stance mapping.** `bodyguard` ⇒ `ESCORT`. Invoking the skill sets the matching stance
   automatically — that's the payoff for having both systems: the skill supplies the *intent*, the
   stance supplies the *enforcement*. `home-guard` maps to `WORK` for now (it needs to fight and hold
   ground but not dig); it gets its own `GUARD` stance when that lands.

### Migration

`extractExamples()` never overwrites an existing file (`CompanionSkills.java:277-280`), so existing
users keep their edited `home-guard.md` and get the new `bodyguard.md` alongside. Anyone who wants the
rewritten area-defense version runs `/companion skills reset home-guard`, which backs theirs up to
`.bak` first (`resetBundled`, line 221). No destructive migration needed — the existing machinery
already handles this correctly.

Worth calling out in the changelog explicitly, because a user with a customized `home-guard.md` will
otherwise be confused about why it still behaves like a bodyguard.

## Healing costs food

A companion currently regenerates **1.0 HP every 10 ticks — 2 HP per second — forever, at zero cost.**
Near-death to full in ten seconds. Vanilla's *peak burst* rate is its permanent floor.

### Why

`LivingEntityHungerManager.regenerateOnly` deliberately omits `addExhaustion`, and its javadoc explains
the reasoning: `update()` adds exhaustion on every heal, exhaustion drains saturation and then food, and
a companion with no working way to eat would starve.

The reasoning is circular, and the omission is self-defeating. Exhaustion is the *only* thing that
drains saturation, and `update()` is never called for a companion. So:

- `foodSaturationLevel` sits at 20.0 forever, `foodLevel` at 20 forever
- `saturation > 0 && foodLevel >= 20` is permanently true — the fast-regen branch never yields to the
  slow one
- it heals `min(saturation, 6.0) / 6.0` = a flat **1.0 HP per 10 ticks**, indefinitely

Vanilla hits the same rate but pays 6.0 exhaustion per heal (≈1.5 saturation), giving ~13 heals before
dropping to 1 HP per 4 seconds and starting to burn food. **A player cannot sustain 2 HP/s.**

### It also made the whole food system inert

Because food never falls below 20:

- `EatCommand` refuses with "already at full food" — *always* (`EatCommand.java:49`). Eating is
  unreachable, and its comment already points at `regenerateOnly` as the cause.
- `FoodChain`'s fillup check (`getFoodLevel() >= 20`) never fires.
- `/companion food` and the `meat` command report a permanent 20/20.

Four commands that currently cannot do anything become real as a side effect of this fix.

### The fix

Replace `regenerateOnly` with a companion tick that runs, in order:

1. The exhaustion → saturation → food conversion (the first block of `update()`).
2. Both regen branches **with** their `addExhaustion(f)` / `addExhaustion(6.0F)` calls.
3. **Not** the `foodLevel <= 0` starvation-damage branch.

Dropping starvation is the deliberate part. A hungry companion should stop regenerating and wait to be
fed; it should not die of neglect in a corner while its owner is offline. Healing has a cost, running
out of food has a consequence, and neither is lethal on its own.

### Hunger is deliberately not persisted

**`CompanionEntity` does not save hunger** — `LivingEntityHungerManager` has `readNbt`/`writeNbt` and
nothing calls them, so food and saturation reset to 20/20 on every world load.

**Decided: leave it that way.** What matters is that healing has a cost *within a session*, which is
where the fight actually happens. Persisting it would let a companion start a session already starving
through no fault of the owner, which is a worse experience than the exploit it prevents.

The exploit is real and accepted: quit and rejoin to refill your companions' food. It costs a world
reload, it's obvious, and anyone doing it deliberately has decided they'd rather not play that part of
the game. Not worth the failure mode on the other side.

### Shipped — and it found a second bug

`regenerateOnly` became `tickCompanion`, with the exhaustion/food drain restored and the starvation
branch deliberately left out.

Checking "does the companion reliably feed itself" turned up a real defect: **`CompanionEntity` never
overrode `eatFood`.** Filling a food bar is a `PlayerEntity` override; `LivingEntity.eatFood` consumes
the stack and applies the effects but touches no food level. So every route to eating — the `eat`
command, `FoodChain`'s auto-eat, a player right-clicking food in — destroyed the item for nothing.

It was invisible because hunger could never fall, so nothing ever tried to eat. Shipping the healing
fix without this would have had companions eat their entire supply one item at a time and stay hungry
— exactly the failure mode this section warned about. Overridden in `CompanionEntity.eatFood`.

### And a third, found in the first playtest

The `eatFood` override was necessary but not sufficient. `FoodChain.startEat` fed itself by forcing
`Input.CLICK_RIGHT`, which reaches `LivingEntityInteractionManager.interactItem` — and that calls
`stack.use(world, null, hand)` with a **null player**. Vanilla's `Item.use` dereferences that argument
on its first instruction for anything edible, so the call threw every time and the surrounding `try`
swallowed it. **Automatic eating had never once worked.** `EatCommand`'s javadoc had documented this
exact dead path as the reason it consumes directly; nobody connected it to `FoodChain`.

Fixed by driving `entity.startUsingItem(MAIN_HAND)` directly — skipping the broken `Item.use` entry
point and handing to the machinery beyond it, which does work and gives the eating animation, sound and
particles for free, finishing into `finishUsingItem` → `eatFood`.

Two more from the same playtest:

- **`eat` took one bite per invocation**, so topping up cost an LLM round trip per mouthful and the
  model would often ask again while already full. It now eats until full or out of food.
- **Rotten flesh was scored `-100`** in `calculateFood`, i.e. never chosen unless nothing else exists.
  That penalty is correct for a player and meaningless for a companion: vanilla's Hunger effect is
  gated on `instanceof PlayerEntity` before it adds any exhaustion (verified by disassembling
  `StatusEffect.applyUpdateEffect`), so it lands and does nothing. Rotten flesh is free food, and it is
  the food a companion actually finds. Penalty removed; the `eat` help text now says so explicitly.
  The spider-eye exclusion stays — Poison has no player check and does hurt them.

**Still not automatic:** `CollectFoodTask` never runs, because `minimumFoodAllowed` and
`foodUnitsToCollect` are both `0` in `Settings`. Left alone deliberately — a companion that wanders off
foraging on its own initiative is a bigger behavioural change than this release should carry.

## Retreat is health-aware

Today's flee decision (`MobDefenseChain.java:346-359`) **does not consider health at all.** It compares
gear against crowd size:

```java
int canDealWith = ceil(armor * 3.6 / 20.0 + damage * 0.8 + shield);
if (canDealWith < getDangerousnessScore(toDealWithList)) → RunAwayFromHostilesTask(30.0, true)
```

| Kit | `canDealWith` | Flees from |
|---|---|---|
| Bare hands, no armour | 0 | 1+ |
| Iron sword | 3 | 4+ |
| Diamond sword | 4 | 5+ |
| Diamond sword + full iron | 6 | 7+ |
| …plus shield | 9 | 10+ |

So a companion at 1 HP with a diamond sword stands and fights a zombie, and one at full health with
bare hands runs from a zombie. The only health check anywhere in the chain is `getHealth() <= 10.0F` at
`:281`, which gates *projectile dodging and cover walls* — not disengaging — and is skipped entirely if
it carries a shield.

This was written for a bot that could stunlock its way out of anything, so it never needed a health
input. 0.2.6 removed the stunlock.

### Fold health into the existing comparison

Scale the capability number by health fraction, plus a hard floor:

```java
float frac = entity.getHealth() / entity.getMaxHealth();
int canDealWith = ceil((armor * 3.6 / 20.0 + damage * 0.8 + shield) * frac);
// and, regardless of gear:
if (frac < 0.25f) → flee
```

### Re-engagement is not a component

**Deliberately no state machine, no "return to the fight" task, no memory of what it fled from.** The
same evaluation runs every tick. A fleeing companion heals as it goes; if a mob follows and the
companion is healthy enough that its gear says it can win, it turns and fights, exactly as it would
have on first contact. If it's still hurt, it keeps running. The behaviour people expect falls out of
one health-aware check rather than being built.

One implementation caveat: right at the threshold this can oscillate — flee, heal one tick's worth,
engage, take a hit, flee. A small dead band on the health term (or holding the decision for ~20 ticks
once made) fixes it. That's a detail inside the check, not a second system.

### Letting the LLM override the run instinct

Worth having, and worth being careful about where it sits.

**The cornered case should be deterministic, not a judgement call.** "It's fleeing but making no
progress" is directly detectable — if `RunAwayFromHostilesTask` fails to path or the companion's
distance from the nearest hostile hasn't improved in ~40 ticks, stop fleeing and fight. That's instant
and reliable. An LLM round trip is seconds; a cornered companion is dead before the reply lands. Don't
put a network call on the critical path of a fight.

**What the LLM should own is pre-commitment, not reaction.** The model can't react at tick speed, but it
can decide *ahead of time* that this fight is worth not running from — holding a doorway while the
owner escapes, defending something that matters, buying time. Two seams for that:

- **Stance.** `GUARD`, when it exists, means "hold this ground" — that's the declarative version of the
  same instruction, and it's why the two threads in this release belong together.
- **A short-lived override command.** Something like `stand_ground [seconds]`, defaulting to ~30s and
  expiring on its own, that suppresses the flee branch while active. Expiry matters: a permanent
  override is how a companion ends up dying for a fight nobody remembers starting.

Surface the flee decision to the model through `gameDebugMessages` when it fires, so it knows it ran
and why. That's context for its *next* decision, not a prompt for an in-fight one.

## Expected effect

- Prompt shrinks by roughly a third in `ESCORT`/`PARKED` (13–17 commands advertised instead of 24), on
  **every** call, forever.
- Healing costs food, so a fight has a lasting cost and `eat`/`food`/`meat`/`FoodChain` do something for
  the first time.
- A hurt companion runs; a healthy one fights; neither needs a re-engagement system to do it.
- A companion in `ESCORT` structurally cannot dig, farm, or build. The house-demolition failure mode
  becomes unreachable rather than unlikely — *for companions in `ESCORT`*. `WORK` and `FREE` still need
  0.2.8.
- Fewer legal options measurably reduces wrong-command selection on smaller/quantized models — which is
  the regime this mod targets.

---

# 0.2.8 — Protected structures and door tidying

Grouped because they share one unknown: **where Baritone/Automatone actually commits a block break and
a door interaction.** Neither section below cites a line number, unlike 0.2.6 and 0.2.7 — that gap is
the work. Start with a spike that answers both hook-point questions, then implement.

If the house-destruction complaint is what's driving the schedule, this is the release to hurry, not
0.2.7 — stance only protects companions in `ESCORT`.

## Protected structures — "do not break"

Complements the stance gate. Stance answers *may it dig at all*; protection answers *may it dig here*.
Needed independently, because `WORK` stance legitimately digs.

### Data model

New: `aicompanion/src/main/java/com/neovetta/aicompanion/protect/ProtectedRegions.java`

```java
public record ProtectedRegion(String name, RegistryKey<World> dimension,
                              BlockPos min, BlockPos max, UUID owner) {}
```

Persist as `SavedData`/`PersistentState` keyed per-world, or as JSON under
`config/aicompanion/protected.json` if that's a faster path — the mod already has a config-dir
convention (`CompanionSkills.skillsDir()`, `CompanionConfig.extractTtsSetup`). JSON is probably right:
it's hand-editable, which fits the same philosophy as editable skill markdown.

Independent of stance persistence, which 0.2.7 puts in entity NBT — that's per-companion state, this is
world state. No shared store, no coupling between the two releases.

### Marking a region

Two ways, both free:

```
/companion protect add <name>           # two-corner selection, wand-style or from stored pos1/pos2
/companion protect here <name> [radius] # cube around the player, default radius 16
/companion protect list
/companion protect remove <name>
```

`protect here` is the one people will actually use. Make radius default sane and don't require a
selection tool.

**Auto-protect on spawn** is worth considering: when a companion is first spawned, protect a small
radius around that point. Most people spawn their companion at home. Opt-out via config.

### Enforcement

The check must sit at the lowest level that can actually break a block, not in the command layer —
`dig`, `get`, `build_structure`, and Baritone's own path-clearing all break blocks by different routes.

Find where Baritone/Automatone commits a break in the engine and add a single predicate:

```java
ProtectedRegions.canModify(world, pos, companionOwnerUuid)
```

If it returns false: refuse the break, and — importantly — tell Baritone the block is unbreakable so
pathfinding routes around it rather than retrying forever. Baritone has a
`Settings#blocksToAvoidBreaking` / avoid-breaking concept; wire protections into that at task start so
the pathfinder never plans through a protected region in the first place. Enforce at the break site as
the backstop.

Report a refusal through the same `gameDebugMessages` channel so the model learns it, and say it out
loud once ("I'm not going to break that, it's protected") so the player understands why the companion
looks stuck.

### Interaction with stance

`PARKED` and `ESCORT` never break blocks at all, so protections are moot there. Protections matter in
`WORK` and `FREE`. Don't let one substitute for the other.

## Close doors after passing through

Small, cosmetic, disproportionately good for immersion.

**Constraint discovered while surveying:** `CompanionEntity extends LivingEntity`
(`entity/CompanionEntity.java:67`), **not** `MobEntity`/`PathAwareEntity`. There is no `goalSelector`.
The vanilla `OpenDoorGoal(mob, true)` trick that ModernCompanions uses is not available — this has to be
a `TaskChain`.

New: `engine/.../chains/DoorTidyChain.java`, registered alongside the existing chains
(`engine/src/autoclef/java/adris/altoclef/chains/`).

- Priority: very low, below `PreEquipItemChain` (`-1.0F`). It must never preempt anything. Use something
  like `-5.0F`, and return `Float.NEGATIVE_INFINITY` when there's nothing to close.
- State: a small FIFO (cap ~4) of `BlockPos` for doors/trapdoors/fence gates this companion opened.
  Record on open; Baritone's door interaction is the hook point — **the same spike that finds the break
  site should find this.**
- Fire when: the recorded door is >3 blocks behind the companion, still open, still a door, same
  dimension, and line-of-sight/reachable. Then close it and pop the entry.
- Drop entries silently on dimension change, teleport, death, or if the position is no longer a door.
  Never path *back* to a door — if it's out of reach, forget it.

The "never path back" rule matters. A companion that walks 20 blocks backwards to shut a door is worse
than one that leaves it open.

---

# 0.2.9+ — Resumable work loops (the token fix)

Biggest cost reduction available, and the most implementation work. **This section is a sketch, not a
plan** — "skills become orchestration, jobs become execution" is an architecture decision about what the
markdown skill system fundamentally *is*, and it deserves its own design doc before implementation.

## The problem, concretely

`CompanionSkills` injects a skill body verbatim into the LLM queue and the model drives it with the
normal one-command-per-turn loop (`CompanionSkills.java:17-31`, `docs/brain-contract.md:14`). Every
iteration of an inherently repetitive task is a round trip. ModernCompanions runs the same five concepts
(`LumberjackJobGoal`, `MinerJobGoal`, `FisherJobGoal`, `ChefJobGoal`, `HunterJobGoal`, all extending
`ResumableJobGoal`) for free, forever, because they're plain Java loops.

## Sketch: a job chain the LLM starts and stops

New: `engine/.../chains/JobChain.java` + a `Job` interface, priority between `UserTaskChain` (50) and
`UnstuckChain` (65) — call it 45, so survival and unsticking always win.

```java
public interface Job {
    boolean canContinue(AltoClefController mod);  // false -> hand back to LLM with a reason
    Task nextStep(AltoClefController mod);        // the AltoClef task for this iteration
    String stopReason();                          // surfaced to the model when canContinue() fails
}
```

Ship the obvious ones first: `ChopJob`, `FarmJob`, `FishJob`, `MineJob` — each **radius-bounded** around
an anchor point, which is exactly the `MinerJobGoal(this, radius, true)` pattern worth stealing from
ModernCompanions. Bounding is what keeps a companion from wandering off across the world, and it
composes with protected regions from 0.2.8.

The LLM's involvement collapses to two moments:

```
job chop 64 radius 32      # start: one LLM call
...                        # N iterations: zero LLM calls
                           # stop: one LLM call, with stopReason
```

Hand back to the model when: target count reached, inventory full, tool broken and no replacement,
radius exhausted, threat detected, owner spoke, or an explicit `stop`.

## Keep the markdown skills

They're the good part — openness and user-authorability. The change is that a skill body should be able
to say *"run `job chop 64 radius 32`"* and let deterministic code carry the loop, instead of narrating
every swing. Skills become orchestration; jobs become execution.
