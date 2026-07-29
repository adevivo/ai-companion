# Changelog

Release notes as published on CurseForge. Newest first. Paste the version's section into the
CurseForge changelog field at upload — it renders Markdown there.

---

## 0.2.1 — Skills that can finish a sentence, and building underground

Bundles PlayerEngine 1.0.44. Self-contained jar as always — don't install a standalone engine
alongside it.

### ⚠️ Two things to do after updating

**1. Run this command:**

```
/companion skills reset farming
```

The Farming skill changed in this version, and a copy already on disk is never overwritten
automatically — that protects skills you have edited yourself. Without this it keeps tending the wrong
field (see below). The command backs the old one up to `farming.md.bak`. Fresh installs need nothing.

**2. Open `config/aicompanion.json` and set:**

```json
"llm": { "maxTokens": 1000 }
```

The old default of `200` was too small for the skills to work — see below. New installs get 1000
automatically; an existing config keeps whatever it has, so this one is manual. The companion now
tells you in red, on every message, while the value is too low.

### The farming skill could never run

- **Fixed: skills no longer get cut off mid-reply.** A skill hands the companion a command to
  reproduce word for word, and the farming one is about 700 characters. At the old default token
  limit the reply was truncated part-way through that command every single time, so no command ever
  ran — the companion simply stood there having said it was starting. Four attempts, nothing built,
  and the only clue was a JSON parse error in the log.
- **A cut-off reply now says so.** It was previously reported — to you and to the companion — as
  "not valid JSON", which is wrong and unfixable: the reply was fine, it just stopped early. The
  companion would re-send the same too-long command and be cut off again. It's now named properly in
  chat and in the log, and the companion is asked to be brief instead.
- **The default token limit is now 1000, up from 200.** This costs nothing: the limit is a ceiling,
  not a budget — you're billed for what's actually generated, and a short reply is short either way.
  Use `llm.maxRequests` and `behavior.maxAutonomousTurns` to control spend.

### Farming stops talking over you

- **A tended field no longer repeats itself every few seconds.** Waiting for crops to regrow used to
  print "standing by for regrowth" and "pass complete: harvested=0, sown=0" on a loop — about 120 lines
  in half an hour, all saying the same nothing, and loud enough to bury messages that mattered. It now
  says why it's standing still once, then stays quiet until something actually changes. A *different*
  reason, like running out of seeds, still comes through straight away, and progress reports during an
  active pass are unchanged.

### It tends the field it just built

- **Fixed: after building a field it would tend whichever one happened to be nearest.** Between
  finishing the build and starting to farm there's a pause while it thinks, and it walks back to you
  during that pause — so "now tending the crops" could mean a field 37 blocks from the one it had just
  made. This went unnoticed whenever an older field was nearby; build somewhere new and it would find
  nothing to do while reporting success. `farm` now takes the field's coordinates, and the skill passes
  the ones it built at.
- Tending several fields at once still works exactly as before — the range applies around that point,
  so everything close to it gets worked in the same pass.

### A full inventory no longer brings it to a halt

- **It makes room for itself instead of getting stuck.** With a full inventory it could not pick
  anything up, and the gather that needed one more log just retried — in one session, 3,814 times over
  27 minutes, without placing a single block of the farm it was building. The one piece of code that
  frees a slot could only run if it was already touching the item it was trying to collect, which a
  full inventory is exactly what prevents. It now runs whenever there's no room.
- **It tells you.** That whole 27 minutes produced nothing in chat at all — you had to notice and ask.
  The companion now says "I can't pick anything up — my inventory is full", and tells its own reasoning
  the same thing, so it goes and deposits something rather than repeating the same request.
- **"It's unreachable" no longer means "I'm full".** Those are different problems and needed different
  answers; only one of them was ever reported.
- **It stops hoovering junk once full.** Walking around, it picks up every stray cobblestone and leaf.
  Once there's no free slot it will now only take things that stack onto what it's already carrying, so
  it stops spending its last slots on debris. Below that, collecting is unchanged.

### Building in caves

- **It can place blocks underground.** Ask for a crafting table in a cave and you got "I can't build
  that here — the plan came out 28 blocks underground" every single time, however you phrased it. It
  was measuring the ground from the sky, so anything with a roof over it — a cave, a mineshaft, a
  ravine, the inside of your base — read as buried under the whole hillside and was refused. It now
  measures the floor it is actually standing on. Building above ground is unchanged.
- **It stops arguing with itself about where the ground is.** The refusal used to quote a ground level
  that contradicted the reason it gave, so it would "correct" itself one block lower and fail again —
  six times in a row in one session before the owner told it to stop.
- **`groundLevel` is now the block it is standing on**, as the prompt always claimed. Underground it
  was reporting the surface far overhead, so every height it reasoned about down there was wrong —
  and standing on a rooftop over water it reported the water, seven blocks down, so a field built
  "at ground level" would have gone in the lake. It now asks the game which block is holding it up,
  which is also right when it's stood on the very edge of something.

### Also fixed

- **You get told when it's too far away to hear you.** Past 64 blocks your messages were silently
  dropped — indistinguishable from a dead companion or a broken model. It now says
  `(<name> is 154 blocks away and can't hear you — /companion come)`, at most once every 30 seconds.

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
