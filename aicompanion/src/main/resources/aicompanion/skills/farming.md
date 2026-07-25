# Farming
Build a watered crop field level with the terrain, then tend it.

## Rules
Only `build_structure` places blocks. There is no hoe, dig or plant action — never say you are doing
those. `farm` only tends crops that already exist, so it is the last step, never the first. Building
costs no materials, so never `get` anything first.

## 1. Coordinates
Take `position` from agentStatus, e.g. `(74.9, 65.5, 356.4)`. Those are your feet. Drop the decimals,
then subtract 1 from Y: `(74, 64, 356)`. Whole numbers only. A wrong Y puts the field on a dirt
pedestal with water pouring off the sides.

## 2. Build
Default 9x9, never past 15x15. Send this exactly, changing only the numbers and the crop:

`build_structure a flat 9x9 wheat field at ground level, centred on (X, Y, Z). The layer at y=Y is the
existing ground surface and must be REPLACED, not built on top of: every block of the 9x9 at y=Y
becomes farmland, except the middle row which is water SOURCE blocks at y=Y, flush with the ground. At
y=Y+1 place wheat on every farmland block — no gaps, no alternating rows — and set the middle row at
y=Y+1 to air. Ring the field with a 1-block dirt_path border at y=Y, a torch on that border every 4
blocks at y=Y+1, and a chest at one corner. No dirt, stone or foundation layer below the farmland. Do
not raise the field above the surrounding terrain. Source blocks only, no flowing water.`

Do not reword or summarise it. Other crops: swap both `wheat` for `carrots`, `potatoes` or
`beetroots`. Wider than 9: ask for a water row every 8 blocks instead of one down the middle, because
farmland dries out more than 4 blocks from water.

## 3. Finish
`farm 16`, then tell the owner the crop, size and coordinates.

On error: say what it was, retry once at 5x5, then stop and say what blocked you. No crop specified
means wheat, no size means 9x9 — pick the default and build in the same turn rather than asking.
