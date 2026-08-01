# Staircase Mine
Cut a staircase down from a spot the owner picks, so they can walk back up.

## What does the work
The `dig` command cuts the whole staircase on its own. It descends at 45 degrees — one block down for
every block along — and stops by itself at lava, at water, or near bedrock, then reports where it got
to. You issue it ONCE and wait.

`dig <direction> <depth>` — direction is north, south, east or west; depth is how many blocks BELOW
your current position to finish. Example: `dig east 30`.

Do not work out any coordinates. Do not issue a `goto` for each step. Do not use `build_structure`,
which places blocks and costs materials — the opposite of what is wanted here.

## 1. Ask where and which way
You need two things before starting, and you ask for both in one message:

1. **Where the entrance goes** — ask the owner to stand there and say when they are set.
2. **Which direction** — north, south, east or west. If they say "you pick", choose east and tell
   them.

Until you have both, reply with an EMPTY command and ask. Depth defaults to 30 blocks; use theirs if
they name a number.

## 2. Stand on the entrance
`dig` starts from wherever you are standing, so be standing in the right place first.

Read the nearby players field in worldStatus for the owner's position, then `goto` those exact
coordinates — three numbers separated by spaces, no brackets and no commas. Wait until you have
arrived before the next step.

If you are already standing where they want it, skip this.

## 3. Check you have a pickaxe
Look at the inventory field in agentStatus for any item ending in _pickaxe. If there is none:
`get stone_pickaxe 1` and wait for it to appear. Without one you will be breaking stone by hand and
the staircase will take forever.

Do not `equip` it — the right tool is chosen automatically.

## 4. Dig
Say what you are about to do in one short line — "starting here, heading east, 30 down" — and issue
`dig <direction> <depth>` the same turn. Do not wait for a second confirmation.

Then wait. Reply with an EMPTY command while it runs. It will tell you when it has finished or why it
stopped; repeat that to the owner in your own words, including the depth reached and the coordinates
of the bottom step.

## 5. Afterwards
Reply with an EMPTY command and wait. Do not start collecting ores, widening the shaft, or heading
back up on your own unless they ask.
