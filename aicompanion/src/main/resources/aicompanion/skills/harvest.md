# Harvest
Harvest and replant an existing crop field, stashing the yield in a chest.

## What you can actually do
Harvesting is ONE command: `farm <range>`. It walks the field, breaks fully-grown crops, replants them,
and keeps going for as long as it runs. You do not break blocks yourself, you do not re-till anything,
and you do not right-click seeds — there are no commands for those. Never describe yourself doing them.

## Steps
1. If you are more than about 20 blocks from the field, `goto <x> <y> <z>` first, using the ground
   coordinates of the field. If you do not know where it is, ask the owner.
2. Issue `farm 32`. The range is measured from where you are standing, so be at or beside the field.
3. Tell the owner you are tending the field, and where.

## Check this before every single reply
Read `taskStatus` in agentStatus. It is the only truth about whether you are working. What you said
last turn is not evidence, and neither is what you intended.

- It starts with `<Farming` → the job really is running. Follow the rules below.
- It says `<Idle>`, `No tasks currently running`, or anything else → **you are NOT harvesting.** Issue
  `farm 32` right now, this turn, no matter what you said a moment ago.

Never say you are harvesting, starting to harvest, or heading to the field while `taskStatus` is
`<Idle>`. If you are idle and you talk about harvesting without issuing `farm`, you will stand
motionless describing work that is not happening — which is worse than saying nothing.

## When the owner speaks while farming IS running
Decide which of these it is. Getting this wrong is the main way this skill goes bad.

**They asked a question** ("how's it going?", "are you nearly done?", "is it working?") — answer them
with an EMPTY command. Every command replaces the farming, so answering a question with `idle`,
`bodylang` or another `farm` would stop the very work they are asking about.

**They told you to do something** ("put the wheat in the chest", "come here", "stop", "harvest the
other field") — DO IT. Issue the command they asked for. Never reply to an instruction with an empty
command: that leaves you standing still while the owner watches you ignore them. If the instruction
means abandoning the farming, that is the owner's call to make, not yours.

If they point out something is wrong — the chest is empty, there is still wheat standing, you have not
moved — treat that as an instruction, not a question. Act on it, and if you genuinely cannot, say what
is blocking you instead of repeating that everything is fine.

## The chest
`farm` never uses the chest; stashing is a separate command that costs you the farming task.

- **When asked**, deposit immediately, whatever the amount. `deposit wheat <count you are holding>`.
- **Unprompted**, deposit once you are holding about 64 wheat.

Either way: after `deposit` finishes, issue `farm 32` again to resume.

Deposit the CROP only — `deposit wheat 64`, never a bare `deposit`. A bare `deposit` dumps everything
including the seeds you need to replant, and the field stops refilling.

## Progress and honesty
It walks to each crop one at a time, so a big field takes many minutes and your wheat count creeps up.
Slow is normal. But if `taskStatus` still says `<Farming` and your wheat count has not moved across
several replies, say so plainly instead of repeating that the field is being tended. A companion that
reports being stuck is useful; one that insists it is working is not.

## If something goes wrong
If `farm` reports an error, say what the error was. If `deposit` cannot find a chest, say so and resume
farming rather than wandering off. If there is no field where you thought there was one, say so and
offer to build one with the farming skill instead of pretending to harvest.
