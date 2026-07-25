# Farming
Build a watered crop field that sits level with the terrain, then tend it.

## What you can actually do
You have no hoe action, no dig action and no way to right-click seeds. Do not plan those steps and do
not tell the owner you are doing them. Everything is placed in one shot by `build_structure`, which
sets blocks directly and needs no materials — so never `get` anything first.
`farm` does not create a field. It only harvests and replants crops that already exist, so it is the
last step, never the first.

## Step 1 — survey
Run `scan water` to find the nearest water. If it comes back within about 20 blocks, build near it and
mention that in your description. Otherwise you will place your own water sources, which is fine.
Prefer a spot that already looks flat. Say where you have decided to build before you build it.

## Step 2 — work out the ground coordinates
Read `position` from agentStatus, e.g. `(74.9, 65.5, 356.4)`. Those are your FEET, and your feet sit in
the air block above the ground. Convert to the ground block, whole numbers only:
- X = drop the decimals. 74.9 becomes **74**.
- Y = drop the decimals, then **subtract 1**. 65.5 becomes 65, minus 1 is **64**.
- Z = drop the decimals. 356.4 becomes **356**.

`(74.9, 65.5, 356.4)` becomes `(74, 64, 356)`. Never put a decimal point in the command. Getting Y wrong
is the most common failure: the field ends up on a dirt pedestal with water pouring off the sides.

## Step 3 — build it
Pick a size. Default 9x9, which one central water row hydrates completely. Farmland dries out when it
is more than 4 blocks from water, so a single middle row only reaches across a field up to 9 wide. If
the owner wants it wider, ask for a water row every 8 blocks instead of one down the middle. Never go
past 15x15. Issue one command, copying this description and changing only the numbers and the crop:

`build_structure a flat 9x9 wheat field at ground level, centred on (X, Y, Z). The layer at y=Y is the
existing ground surface and must be REPLACED, not built on top of: set every block of the 9x9 at y=Y to
farmland, except the middle row (the 5th row) which is water SOURCE blocks at y=Y so the channel sits
flush with the ground and cannot spill. At y=Y+1 place wheat on every single farmland block with no
gaps and no alternating rows, and set the middle row at y=Y+1 to air. Ring the whole field with a
1-block dirt_path border at y=Y, put a torch on that border every 4 blocks all the way round at y=Y+1,
and put a chest on the border at one corner. Do not place any dirt, stone or foundation layer below the
farmland. Do not raise the field above the surrounding terrain. Do not use flowing water, only source
blocks.`

The torches are not decoration: crops need light level 9 or more to grow, so without them the field
stops growing at night and mobs spawn on it.

Send that text with X, Y, Z substituted. Do not reword it, do not summarise it, and do not invent extras
like alternating rows — that is what breaks the field. For a different crop swap both occurrences of
`wheat` for `carrots`, `potatoes` or `beetroots`. For a bigger field change both `9x9` numbers and say
a water row every 8 blocks, not one down the middle.

## Step 4 — tend and report
When the build finishes, issue `farm 16` so you keep harvesting and replanting from then on.
Then tell the owner the crop, the size, and the ground coordinates you built at.

## If it goes wrong
If the build reports an error, say what the error was and retry once at 5x5 at the same coordinates.
If that fails too, stop and tell the owner what blocked you. Never retry more than once, and never fall
back to `farm` on an empty field — it looks like you are doing nothing.

## Only ask if you must
No crop specified means wheat. No size specified means 9x9. Ask only if the owner's request is genuinely
contradictory or the ground is obviously unusable — otherwise pick the default, say what you picked,
and build in the same turn.
