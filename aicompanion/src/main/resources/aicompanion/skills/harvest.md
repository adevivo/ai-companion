# Harvest
Tend an existing field: harvest it, replant it, and keep it going. Stash the yield in a chest.

## Check before every reply
Read `taskStatus` in agentStatus — it is the only truth about whether you are working. What you said
last turn is not evidence.

- Starts with `<Farming` → it really is running.
- `<Idle>` or `No tasks currently running` → you are NOT tending the field. Issue `farm 32` now, this
  turn, whatever you said a moment ago.

Never say you are farming while idle.

## The job
`farm <range>` is the whole job. It harvests the ripe crops **and replants the empty farmland**, over
and over, by itself. You do not need any other command for it, and you should not issue one: no `equip`
for a hoe, no breaking blocks, no separate planting step — `farm` already does all of that. Saying "I'm
harvesting and replanting" is accurate.

If you are more than about 20 blocks away, `goto <x> <y> <z>` first; if you do not know where the field
is, ask.

Issue `farm 32`, then tell the owner where you are working.

## This is ongoing maintenance, not a one-off
`farm` does not finish. You stay at the field and keep tending it — harvest, replant, wait, harvest
again — until the owner gives you something else to do. That is the intended behaviour, not a problem.
Do not stop on your own, do not wander off, and do not re-issue `farm` when it is already running.

## Standing still can be correct
Once everything ripe is cut and every empty tile is sown, there is genuinely nothing to do until the
crops grow back. Waiting at the field is the right move.

`taskStatus` tells you which situation you are in — when the farm is not making progress it says why,
in brackets after `<Farming`:

- *waiting for the crops to regrow* → you are fine. Say you are waiting for the next crop, not that you
  are harvesting right now.
- *no seeds left* → tell the owner; those tiles stay empty until you have seeds.
- *not actually farming* / *block scan* → you are stuck and nothing is happening. Say so plainly. Do
  not claim to be working.

Also read `gameDebugMessages` — the farm reports its own state there. Trust it over your own memory of
what you said last turn.

## While it runs
Any command you issue replaces the farm, so only issue one when you actually mean to stop farming.

- Owner asks a question → reply with an EMPTY command. Not `idle`, not `bodylang`, not another `farm`.
- Owner tells you to do something → do it. Never answer an instruction with an empty command.
- Owner says something is wrong (chest empty, wheat still standing, you are not replanting) → that is an
  instruction, not a question. Check `taskStatus` and `gameDebugMessages`, tell them what those actually
  say, and act on it.

## Chest
- Asked → `deposit wheat <count you are holding>` immediately, any amount.
- Unprompted → once you hold about 64 wheat.

Then `farm 32` again to resume. Deposit the crop by name — a bare `deposit` dumps the seeds you need to
replant. No chest within 20 blocks: say so and keep farming rather than wandering off.
