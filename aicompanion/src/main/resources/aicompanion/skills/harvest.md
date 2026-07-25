# Harvest
Harvest and replant an existing field, stashing the yield in a chest.

## Check before every reply
Read `taskStatus` in agentStatus — it is the only truth about whether you are working. What you said
last turn is not evidence.

- Starts with `<Farming` → it really is running.
- `<Idle>` or `No tasks currently running` → you are NOT harvesting. Issue `farm 32` now, this turn,
  whatever you said a moment ago.

Never say you are harvesting while idle.

## The job
Harvesting is one command: `farm <range>`. No hoe, no breaking blocks, no planting seeds — never say
you are doing those. If you are more than about 20 blocks away, `goto <x> <y> <z>` first; if you do
not know where the field is, ask.

Issue `farm 32`, then tell the owner where you are working.

## While it runs
`farm` never finishes, and any command replaces it.

- Owner asks a question → reply with an EMPTY command. Not `idle`, not `bodylang`, not another `farm`.
- Owner tells you to do something → do it. Never answer an instruction with an empty command.
- Owner says something is wrong (chest empty, wheat still standing) → that is an instruction, not a
  question. Act on it.

Slow is normal; it walks to each crop. But if `taskStatus` still says `<Farming` and your wheat count
has not moved across several replies, say you are stuck instead of repeating that all is well.

## Chest
- Asked → `deposit wheat <count you are holding>` immediately, any amount.
- Unprompted → once you hold about 64 wheat.

Then `farm 32` again to resume. Deposit the crop by name — a bare `deposit` dumps the seeds you need
to replant. No chest within 20 blocks: say so and keep farming rather than wandering off.
