# Farming
Build a watered crop field level with the terrain, then tend it.

## Rules
Only `build_structure` places blocks. There is no hoe, dig or plant action — never say you are doing
those. `farm` only tends crops that already exist, so it is the last step, never the first.

## 1. Coordinates
X and Z come from `position` in agentStatus, decimals dropped — `(74.9, 65.5, 356.4)` gives X=74,
Z=356. Y is the `groundLevel` value from agentStatus, NOT your position's Y. A field replaces the
ground surface, and `groundLevel` is exactly that layer. Do not add or subtract anything from it.

## 2. Materials
Do not `get` anything. `build_structure` collects what it is short of by itself, so go straight to
the build and let it sort the materials out.

Sizes, for choosing one: a 5x5 field runs to about 40 dirt, 20 seeds, 1 water_bucket, 4 torch and
1 chest; a 9x9 about 110 dirt, 70 seeds, 1 water_bucket, 10 torch and 1 chest. One bucket covers any
number of water blocks. Collecting seeds is slow, so prefer 5x5 unless the owner asked for bigger.

## 3. Build
Default 5x5, never past 15x15. Send this exactly, changing only the numbers and the crop:

`build_structure a flat 5x5 wheat field at ground level, centred on (X, Y, Z). The layer at y=Y is the
existing ground surface and must be REPLACED, not built on top of: every block of the 5x5 at y=Y
becomes farmland, except the middle row which is water SOURCE blocks at y=Y, flush with the ground. At
y=Y+1 place wheat on every farmland block — no gaps, no alternating rows — and set the middle row at
y=Y+1 to air. Ring the field with a 1-block dirt_path border at y=Y, a torch on that border every 4
blocks at y=Y+1, and a chest at one corner. No dirt, stone or foundation layer below the farmland. Do
not raise the field above the surrounding terrain. Source blocks only, no flowing water.`

Do not reword or summarise it. Other crops: swap both `wheat` for `carrots`, `potatoes` or
`beetroots`. Wider than 9: ask for a water row every 8 blocks instead of one down the middle, because
farmland dries out more than 4 blocks from water.

The build may take a while — it collects materials first. That is normal; wait for it rather than
sending the command again.

## 4. Finish
`farm 16 X Y Z` — the SAME X, Y and Z you built at. Pass them; do not send a bare `farm 16`. By the
time this runs you have usually walked back to the owner, and without the coordinates it tends
whatever field is near you instead of the one you just built.

Then tell the owner the crop, size and coordinates.

On error: say what it was, retry once at 5x5, then stop and say what blocked you. No crop specified
means wheat, no size means 5x5 — pick the default and build in the same turn rather than asking.
