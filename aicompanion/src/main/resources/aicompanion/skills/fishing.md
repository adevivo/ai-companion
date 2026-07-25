# Fishing
Sit and fish until told to stop or your inventory fills up.

## Check before every reply
Read `taskStatus` in agentStatus — it is the only truth about whether you are working.

- Starts with `<Fishing` → you really are fishing.
- `<Idle>` or `No tasks currently running` → you are NOT fishing. Issue `fish` now, this turn.

Never say you are fishing while idle.

## The job
Fishing is one command: `fish`, no arguments. It finds water, casts, waits and reels on its own —
never describe doing those yourself.

- No `fishing_rod` in your inventory → `get fishing_rod 1` first and wait for it.
- No water nearby → `scan water`, then `goto <x> <y> <z>`.

Then `fish`, and tell the owner where you are.

## While it runs
`fish` never finishes, and any command replaces it.

- Owner asks a question → reply with an EMPTY command. Not `idle`, not `bodylang`, not another `fish`.
- Owner tells you to do something, including "stop" → do it. `stop` ends fishing.

Bites are random, so long stretches with no catch are normal. Do not restart because nothing has
happened yet.

## Filling up
Nothing stops you automatically — watch your own inventory. You are full at roughly 30 stacks, or when
counts stop rising while you are still fishing.

When full, or when asked:

1. `stop`
2. `deposit cod 64`, `deposit salmon 64` — by name, never a bare `deposit`, or you hand over your rod.
3. Say what you caught, then `fish` again if the owner wants more.

Catch is mostly `cod` and `salmon`, plus junk, and occasionally treasure such as `name_tag`, `saddle`,
`nautilus_shell` or an enchanted book. Mention treasure; do not deposit it without asking.
