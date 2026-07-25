# Fishing
Sit and fish until the owner says stop or your inventory fills up.

## What you can actually do
Fishing is ONE command: `fish`. It takes no arguments. It finds its own water nearby, casts, waits for
a bite, and reels in — over and over, on its own. You do not cast, wait or reel yourself, and there is
no command for those. Never describe yourself doing them step by step.

**You need a fishing rod.** Check your inventory first. If you do not have `fishing_rod`, run
`get fishing_rod 1` and wait for it to finish before you try to fish — without one, `fish` just stands
there reporting it cannot fish.

**You need water within a short walk.** If you are nowhere near any, run `scan water` and `goto` those
coordinates first, then fish. Do not cast in the desert and report success.

## Steps
1. No rod? `get fishing_rod 1` first.
2. No water nearby? `scan water`, then `goto <x> <y> <z>`.
3. Issue `fish`. That is the whole job.
4. Tell the owner you are fishing and where.

## Check this before every single reply
Read `taskStatus` in agentStatus. It is the only truth about whether you are working.

- It says `<Fishing...` → you really are fishing. Follow the rules below.
- It says `<Idle>`, `No tasks currently running`, or anything else → **you are NOT fishing.** Issue
  `fish` right now, this turn, whatever you said last time.

Never claim to be fishing while `taskStatus` is `<Idle>` — you would be standing still describing work
that is not happening.

## While it runs
`fish` never finishes on its own. It keeps going until another command replaces it, so leave it alone.

**If the owner asks a question** ("catch anything yet?", "how's it going?") — answer with an EMPTY
command. Do not issue `idle`, `bodylang` or another `fish`: every command replaces the fishing.

**If the owner tells you to do something** — including "stop fishing" — DO IT. Issue the command they
asked for. To stop fishing, issue `stop`. Never answer an instruction with an empty command.

Bites are random and can take a while, so a long stretch with no new fish is normal. Do not restart the
command because nothing has happened yet.

## Knowing when you are full
Nothing stops you automatically — you have to watch this yourself. Look at your inventory in
agentStatus each time you speak. You are full when it holds about 30 different stacks, or when your
counts stop rising even though you are still fishing.

When you are full, or when the owner asks:
1. `stop` to end the fishing.
2. `deposit cod 64` and `deposit salmon 64` for what you actually caught — deposit each catch type by
   name, never a bare `deposit`, or you will hand over your fishing rod too.
3. Say what you caught, then `fish` again if the owner wants you to carry on.

If there is no chest within about 20 blocks the deposit will fail — say so and keep the fish rather
than wandering off to look for one.

## What you will catch
Mostly `cod` and `salmon`, sometimes `tropical_fish` or `pufferfish`. You will also pull up junk —
`stick`, `string`, `leather`, `bone`, `bowl`, `rotten_flesh` — and very occasionally treasure such as
`name_tag`, `saddle`, `nautilus_shell` or an enchanted book. Mention treasure to the owner; it is the
interesting part. Do not deposit the treasure without asking first.
