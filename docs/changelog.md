# Changelog

Release notes as published on CurseForge. Newest first. Paste the version's section into the
CurseForge changelog field at upload — it renders Markdown there.

---

## 0.2.0 — She stops fidgeting, listens when you interrupt, and actually farms

Bundles PlayerEngine 1.0.39. Self-contained jar as always — don't install a standalone engine
alongside it.

All of this came out of two short play sessions against a local Llama endpoint. Nothing here crashed —
both sessions had zero exceptions — but a lot was quietly going wrong underneath.

### ⚠️ Run one command after updating

```
/companion skills reset harvest
```

The Harvest skill is rewritten in this version, but your existing copy on disk is never overwritten
automatically — that protects skills you have edited yourself, and it also means an update leaves you
on the old text. The command backs up what you have to `harvest.md.bak` first.

### Fixed — farming

- **She walks the rows now, and every tile she harvests gets a seed back.** Farming used to work
  opportunistically — break whatever happens to be in reach, then plant whatever happens to be in
  reach — which is how fields ended up stripped but never sown, and how she could end up standing
  still in the middle of 122 ripe crops for three minutes without taking a step. She now orders the
  field into rows, walks it end to end, and harvests *then immediately replants* each tile before
  moving to the next one. Harvest and replant are one paired action per tile, not two separate sweeps.
- **She covers the whole field.** The block scan was capped at 256 results regardless of the range you
  asked for, so on a large field she only ever saw a corner of it — and which corner was decided by
  chunk iteration order, not by where you were standing. The scan budget now scales with the range.
- **A tile she cannot reach no longer stops the whole job.** Each tile gets 15 seconds; past that she
  records why she skipped it and moves on. Each pass ends with a summary — tiles harvested, sown and
  skipped — so it is obvious what actually happened.
- **Harvesting no longer stops dead without telling you.** Every time the farm was started it threw
  away its map of the field, and the task above it restarted the farm on any tick where it looked idle
  — so the map got thrown away again before it could ever be used, twenty times a second, forever.
  Restarting now keeps the map unless you have actually moved the field, and the restart is
  rate-limited.
- **Dropped wheat and seeds get collected.** She gathers what is lying around between passes instead of
  leaving it to despawn.
- **A stuck farm now says it is stuck.** Every way the farm can end up doing nothing now names itself,
  in chat and in her own status, so "waiting for the crops to regrow" (correct, and worth saying) is
  distinguishable from "I cannot see the field" (broken). If the block scan throws, you get told —
  previously that failure vanished silently and left her frozen with no explanation.
- **She can tell you she is out of seeds.** Bare farmland with an empty seed pouch now reports as
  exactly that, with the number of tiles left unplanted.
- **The Harvest skill was rewritten.** The old text told her *"No hoe, no breaking blocks, no planting
  seeds — never say you are doing those"*, which was meant as "don't run those as separate commands"
  but read as "you do not replant." Asked directly whether she would replant, she apologised and said
  she had forgotten — she had been told not to talk about it. The skill now states plainly that `farm`
  harvests *and* replants on its own, that tending a field is ongoing work she stays with until you
  give her something else, and that standing still while crops regrow is correct rather than a fault.

### Fixed

- **The companion no longer fights herself over what to hold.** While farming or foraging she was
  swapping the item in her hand roughly fifteen times a second, every second, tearing the seeds out of
  her own hand as she tried to plant them. Three things stacked up to cause it: the "am I mining right
  now?" signal was wired to *your* mining rather than hers, the flag that cleared it only ever fired
  once per game launch, and the check that used it ran every single tick with no cooldown. One session
  logged 822 of these swaps, 662 of them back to back.
- **`scan` works.** It could not find a single block — not dirt, not iron, nothing — because it was
  looking up names in a table that gets renamed when the game is packaged. Asking it to find something
  would suggest you meant `field_9975`. It now goes through the block registry.
- **Ask for a mob and she'll answer instead of failing.** `scan` only ever found blocks, but nothing
  said so, so "find me a chicken" went to `scan chicken` and failed. She can already see every nearby
  mob without a command, and now she knows that.
- **Interrupting her actually works.** If you told her to do something while she was already thinking
  about what to do next, her in-flight thought won and your instruction waited a full round. In one
  session "kill that zombie" lost to a decision to go foraging, and she walked off with the zombie on
  top of her. Your instruction now takes precedence.
- **She stops making up chores.** Every finished command asked her "what next?", which she almost
  always answered with another command — so one instruction could spawn an unbounded chain of them.
  After being told "good job" she went on to attack a spider, attack two skeletons, forage, and start
  farming, none of it requested. She now takes at most two actions on her own initiative before
  waiting to be spoken to. Tune with `behavior.maxAutonomousTurns` (0 = unlimited, old behaviour).

### Also

- Warnings about the conversation being stuck no longer fire while she is simply talking. A
  seven-second pause is her finishing a sentence, not a problem.
- Killing a mob no longer logs a nineteen-item shopping list as though she were hunting a chicken for
  blaze powder.

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
