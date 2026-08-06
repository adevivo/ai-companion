# Design — job chains

Status: **scope revised 2026-08-06. Do not build the chain as originally sketched.**

This document was written to promote the "0.2.9+ — Resumable work loops" sketch in
[release-plan.md](release-plan.md) into an implementation plan. Researching it against the code
invalidated the sketch's central premise. The finding is in §1; the revised proposal is in §3.

Everything marked **verified** was read out of the code, not recalled.

---

## 1. The finding: the loop already exists

### What the sketch claims

> `CompanionSkills` injects a skill body verbatim into the LLM queue and the model drives it with the
> normal one-command-per-turn loop. **Every iteration of an inherently repetitive task is a round
> trip.**

**This is false for all four jobs the sketch proposes shipping.** — verified

### The evidence

`get log 64` is **one** task, not 64 round trips:

- [`GetCommand`](../engine/src/autoclef/java/adris/altoclef/commands/GetCommand.java) resolves the
  request through `TaskCatalogue.getItemTask(...)` and calls `mod.runUserTask(targetTask, onFinish)` —
  a single user task handed to `UserTaskChain`.
- [`ResourceTask.isFinished()`](../engine/src/autoclef/java/adris/altoclef/tasks/ResourceTask.java#L51-L52)
  is `StorageHelper.itemTargetsMet(this.controller, this.itemTargets)`. The task ticks until the target
  count is met.
- `MineAndCollectTask extends ResourceTask`, constructed as
  `MineAndCollectTask(Item item, int count, Block[] blocksToMine, MiningRequirement requirement)`.

So the model issues one command and the engine loops to completion with **zero** LLM involvement.

| Sketch's proposed job | Already shipping as |
|---|---|
| `ChopJob` | `get log 64` → `MineAndCollectTask extends ResourceTask` |
| `FarmJob` | `farm <range>` — `FarmCommand` |
| `FishJob` | `fish` — `FishCommand` |
| `MineJob` | `get <ore> <n>`, or `dig <direction> <depth>` |

### The bundled skills already say this in plain language

Verified against `CompanionSkills.BUNDLED`:

- **`farming.md`** — *"`farm <range>` is the whole job. It harvests the ripe crops **and replants the
  empty farmland**, over and over, by itself. You do not need any other command for it, and you should
  not issue one."*
- **`fishing.md`** — *"Fishing is one command: `fish`, no arguments. It finds water, casts, waits and
  reels on its own — never describe doing those yourself."*
- **`staircase-mine.md`** — *"The `dig` command cuts the whole staircase on its own... You issue it
  ONCE and wait."*
- **`harvest.md`** — *"`farm 32` ... over and over, by itself."*
- **`lumberjack.md`** — *"Collect 64 logs of any type"*, which the model emits as one `get`.

Building `ChopJob` would reimplement `ResourceTask`. The token reduction the feature exists to deliver
has already been banked.

---

## 2. Where the token cost actually comes from

The skills themselves are the evidence. Look at what two of the five spend their opening section
defending against:

> **`harvest.md`** — *"Check before every reply. Read `taskStatus` in agentStatus — it is the only
> truth about whether you are working. What you said last turn is not evidence. ... `<Idle>` or `No
> tasks currently running` → you are NOT tending the field. Issue `farm 32` now, this turn, whatever
> you said a moment ago. Never say you are farming while idle."*

`fishing.md` carries a near-identical block. Both exist because **the model loses track of whether its
own long-running task is still running.** The observable failures are:

1. It narrates work it is not doing ("I'm harvesting the field") while idle.
2. It re-issues a command that is already running, restarting the task.
3. Every completed command triggers up to `maxAutonomousTurns` (default 2) further LLM turns.

None of these are fixed by a job chain. A job chain would inherit all three, because the model's
confusion is about *reading its own status*, not about how many commands a loop costs.

### The cheap fixes this suggests — not yet designed, not part of this document

- Make `taskStatus` in `AgentStatus` unambiguous and instruction-shaped, the way `unfinishedBuild` and
  `healingStatus` already are. Both of those exist precisely because a bare fact did not change the
  model's next move; `taskStatus` appears to have the same problem and has not had the same treatment.
- Consider suppressing autonomous turns while a long-running user task is active.

Either is hours, not weeks, and both target the measured failure rather than a supposed one. **Worth
scoping as its own item before anything in §3.**

---

## 3. Revised proposal: what is genuinely missing

Jobs are not worthless — three real gaps survive the finding. None of them is "a loop", and none needs
a new chain.

### 3.1 Radius bounding — the strongest one

`get log 64` has no anchor and no limit. A companion short of logs will walk to the horizon. There is
no way to say "chop, but stay within 32 blocks of here."

**Shape:** an optional anchor + radius on the existing resource tasks, not a new chain. Candidate
blocks outside the sphere are not considered; exhausting the radius ends the task with a reason.

### 3.2 Persistence across restart

`ResourceTask` has no equivalent of `UnfinishedBuild`. A gathering run interrupted by a restart is
simply gone, and the model reconstructs it from a conversation that may have been summarised away —
the exact failure `UnfinishedBuild` was built to fix for builds.

**Shape:** a `JobRecord` mirroring `UnfinishedBuild` — `config/aicompanion/jobs/<companionUuid>.json`,
one per companion, cleared on completion, surfaced in the status feed. Do **not** auto-resume on load.

### 3.3 Structured stop reasons

A task that gives up reports through `taskStatus` prose. There is no structured "why", so the model
cannot reliably distinguish "done" from "axe broke" from "nothing left in range".

**Shape:** a stop-reason string on task completion, surfaced through the status feed following the
`unfinishedBuild` precedent (§A.3).

### Revised sizing

This is **parameters and a record on existing tasks**, not a chain at priority 49. Materially smaller
than item 6 as originally written — plausibly days rather than weeks — and it should be re-scoped and
re-prioritised against items 3, 4 and 5 rather than assumed to be the largest item in the release.

---

## 4. Recommendation

1. **Do not build `JobChain`, `Job`, `ChopJob`, `FarmJob`, `FishJob` or `MineJob`.**
2. Treat §2 (status legibility) as the actual token-cost item, and scope it separately.
3. Treat §3 (bounding, persistence, stop reasons) as a smaller follow-on, re-prioritised against the
   rest of 0.3.0.
4. Correct the sketch in [release-plan.md](release-plan.md) so it does not get promoted again.

---
---

# Appendix — verified research, retained

None of this is needed for the revised proposal, but it was verified against the code and would be
needed if a chain is ever genuinely warranted. Recorded so the work is not repeated.

## A.1 How `TaskRunner` elects a chain — verified

[`TaskRunner.tick()`](../engine/src/autoclef/java/adris/altoclef/tasksystem/TaskRunner.java#L19-L48):
each tick it walks all registered chains, skips any whose `isActive()` is false, and picks the single
highest `getPriority()`. If the winner changed since last tick, `onInterrupt(newChain)` fires on the
outgoing one. Plain argmax, re-run every tick — no pre-emption threshold, no hysteresis.

## A.2 The real priority landscape — verified

| Chain | Priority | Notes |
|---|---|---|
| `WorldSurvivalChain` | 100 / 90 / 60 | 100 is lava escape |
| `MLGBucketFallChain` | 100 / 60 | 100 while falling |
| `MobDefenseChain` | 80 / 70 / 65 / 60 / 0 | tiered by threat |
| `UnstuckChain` | 65 | |
| `FoodChain` | 55 | |
| `PlayerDefenseChain` | 55 / 0 | |
| **`UserTaskChain`** | **50** | what an owner command runs as |
| **`ScavengeFoodChain`** | **48** | opportunistic food pickup |
| `PreEquipItemChain` | -1 | |
| `AutoEquipArmorChain` | `-∞` always | does its work as a side effect inside `getPriority()`, never wins |
| `PlayerInteractionFixChain` | `-∞` always | same pattern |

That last pattern is worth knowing before adding any chain: two existing chains never win the election
and exist purely for side effects performed while being asked their priority.

**The sketch's proposed priority of 45 was wrong twice**, and this stands as a caution regardless of
whether a chain is ever built:

1. It claims 45 is *"between `UserTaskChain` (50) and `UnstuckChain` (65)"*. It is below both.
2. 45 sits under `ScavengeFoodChain`'s **48**, so a companion would abandon a job to fetch a dropped
   pork chop — breaking a promise the scavenge config help already makes to users: *"never does this in
   preference to a job you have given it — tidying up happens once the work is done."*

Had a chain been built, **49** was the correct slot: below `UserTaskChain` so an owner order always
interrupts, above `ScavengeFoodChain` so tidying waits.

## A.3 The status-feed precedent — verified, and still the right pattern for §3.3

[`AgentStatus.fromMod`](../engine/src/autoclef/java/adris/altoclef/player2api/status/AgentStatus.java#L8-L26)
carries `position`, `groundLevel`, `health`, `food`, `saturation`, `healing`, `inventory`, `heldItem`,
`taskStatus`, `oxygenLevel`, `armor`, `unfinishedBuild` and `gamemode` on every turn.

`unfinishedBuild` is the pattern to copy, and its rationale is written into the source:

> *Phrased as an instruction with the description quoted verbatim, for the same reason `healingStatus`
> is a sentence: what changes the model's next move is being told exactly what to pass, not being
> handed a fact to reason from.*

Its error handling matters too: the whole thing is wrapped in a `try/catch` returning a neutral string,
because the status feed runs every single turn and a bad record must never stop the companion thinking.

**A synthesised user turn was considered and rejected** as a handback mechanism: it costs a full LLM
turn on every stop — the exact cost the feature was meant to remove — and stop→turn→start→stop is a
loop with no natural brake.

## A.4 `SingleTaskChain` already handles interruption — verified

[`SingleTaskChain`](../engine/src/autoclef/java/adris/altoclef/chains/SingleTaskChain.java):
`onInterrupt()` sets an `interrupted` flag, and the next tick calls `mainTask.reset()`. `isActive()` is
`mainTask != null`. `onTaskFinish()` fires when the task reports finished or stopped. Any future
loop-shaped chain gets resume-after-interruption for free by extending it.

## A.5 Command shape — verified, applies to any new command

`Command` subclass with a name, a long natural-language description that *is* the model-facing
documentation, and typed `Arg<>`s. Optional arguments carry defaults, and `EatCommand` records why:

> *a bare `eat` is the natural thing to emit, and hard-failing on the missing argument burns a whole
> LLM round-trip recovering.*
