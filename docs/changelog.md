# Changelog

Release notes as published on CurseForge. Newest first. Paste the version's section into the
CurseForge changelog field at upload — it renders Markdown there.

---

## 0.2.0 — Farming that actually farms

Bundles PlayerEngine 1.0.39. Self-contained jar as always — don't install a standalone engine
alongside it.

### ⚠️ Run one command after updating

```
/companion skills reset harvest
```

The Harvest skill is rewritten in this version, and an existing copy on disk is never overwritten
automatically — that protects skills you have edited yourself. The command backs the old one up to
`harvest.md.bak`. Fresh installs need nothing.

### Farming

- **Every tile it harvests gets a seed back.** It now works the field in a zig-zag — up one row, back
  down the next — harvesting and replanting each tile before moving to the one beside it, instead of
  wandering the field and leaving it stripped bare.
- **It sees the whole field**, not just the part nearest to it.
- **Nothing stalls silently.** A tile it cannot reach is skipped and reported, and it says whether it
  is waiting for crops to regrow or genuinely stuck.
- **Dropped wheat and seeds get collected** instead of left to despawn.

### Also fixed

- It no longer swaps the item in its hand many times a second while farming, which was pulling seeds
  out of its own hand as it tried to plant them.
- `scan` works — it previously could not find any block at all.
- Telling it something mid-thought now takes precedence over whatever it was about to do.
- It stops inventing chores once your request is done. Tune with `behavior.maxAutonomousTurns`
  (default `2`, `0` = unlimited).

Tested end to end against a local `Qwen2.5-14B-Instruct-Q4_K_M.gguf` — a quantised 14B model on
consumer hardware handles all of this comfortably.

---

## 0.1.9 — Builds that finish, and a companion that dies like a player

Bundles PlayerEngine 1.0.38. Self-contained jar as always — don't install a standalone engine
alongside it.

The headline is that **builds actually complete now**. Three separate defects were stopping them, and
one of them could take your world down with it.

### ⚠️ Existing configs — one line worth changing

If you have been running an earlier version, open `config/aicompanion.json` and set:

```json
"llm": { "useGrammar": true }
```

It became the default in 0.1.8, but your existing config is preserved on upgrade rather than
overwritten, so an older install is still running with it **off**. It matters: in a measured run, 9 of
21 turns came back unparseable without it and 0 of 6 with it. The symptom is the companion cheerfully
narrating work it never actually starts. Fresh installs already get the right value.

### Fixed

- **A crash that closed your world.** If the companion was holding stairs, doors, fences, chests or any
  other block that has a facing, block placement could throw and end the session — the integrated
  server died and the world shut. Fixed at the cause; placement now also orients blocks correctly
  instead of always facing north.
- **A bug in the companion can no longer take the server down with it.** Anything thrown by the AI is
  caught, logged and reported, and that tick is skipped. After repeated failures the companion switches
  its own AI off and tells you to run `/companion reload`, rather than throwing twenty times a second.
- **Builds could not gather wood.** A defect in the resource catalogue meant `oak_planks`, doors,
  stairs, slabs, fences, trapdoors, boats and signs did not exist as gatherable resources for *any*
  wood type — so a house that needed planks would fetch a torch and give up. `get oak_planks 8` did not
  work either. Generic `get planks` and `get log` always worked, which is why this hid for so long.
- **The companion no longer reports work it did not do.** A build that failed on materials could report
  as finished, and the companion would tell you the house was done when not one block had been placed.
  The outcome now travels with the completion event, so a failure survives into the conversation
  instead of being visible for a single turn.
- **A build that runs out of materials keeps its plan.** Previously each retry designed a *different*
  building with a different bill of materials, so fetching what it asked for never helped — by the time
  you had the logs it wanted cobblestone. Collecting the shortfall and asking again now finishes the
  same structure.

### New

- **The companion drops its inventory when it dies**, exactly like you do. Everything it was carrying
  used to vanish with it, which got worse in 0.1.8 when building started costing real materials — a
  companion killed on the way home took the whole load with it. It now drops the lot (inventory, armour
  and offhand) where it fell, and tells you the coordinates and how many stacks are there so you can go
  and collect them. Note that `keepInventory` does not apply: a companion has no respawn to carry items
  over to, so it always drops.
- **Common structures are built without asking the model.** Houses, crop fields, and walls, paths and
  bridges are now generated in-process. On a paid endpoint that saves roughly 4,000 tokens of prompt
  plus the reply and a full round-trip on every one of those builds; on a local model it is simply
  faster. Anything the templates don't recognise — an L-shape, multiple rooms, a request naming stairs
  or slabs — still goes to the model exactly as before, so nothing you could ask for previously stopped
  working. Templates place full blocks only: doorways are open gaps and roofs are flat.
- **You can see what it's doing.** Gathering reports progress in chat, shortfalls say what is missing
  (*"I can't build that yet — I still need 100 oak planks and 1 red bed"*), and a finished build reports
  how many blocks it placed.

### Known issues

- **Freeform builds still don't converge.** Anything the templates decline goes to the model, which
  designs it without being able to see the inventory — so "build it with the materials you have" cannot
  be honoured, and the companion goes gathering instead. L-shaped and multi-room houses are the common
  case. Being worked on; templated shapes are reliable in the meantime.
- **Incidental pickup fills the inventory.** The companion picks up anything that lands near it, so it
  accumulates cobblestone and leaf drops until it has no room to gather what a build needs. Clearing it
  out means asking for one item type at a time. A bulk drop is planned.
- **`farm` does not stop on its own** — it runs until you tell it to stop.
- **Doors and stairs in model-designed builds place wrong** (a door as its broken lower half, stairs
  always facing north). Build plans cannot carry block states yet. The built-in templates avoid this by
  using full blocks only.
