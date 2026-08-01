# Changelog

Release notes as published on CurseForge. Newest first. Paste the version's section into the
CurseForge changelog field at upload — it renders Markdown there.

---

## 0.2.5 — It can dig, it goes where you send it, and two of them stop taking turns

Bundles PlayerEngine 1.0.55. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. A new `staircase-mine` skill appears alongside the others; your existing
skill files are untouched.

### `dig` — a staircase you can walk back up

New command:

```
dig east 30     # cuts east, ending 30 blocks lower
dig 15          # 15 down, in whichever direction it's already facing
dig             # facing direction, 30 down
```

It descends at 45° — one block down for every block along — so the result is a stairwell you can walk
out of, not a pit you're stuck at the bottom of. It stops by itself at lava, at water, or near
bedrock, and tells you where it got to and how deep.

There was no digging command before. Excavation happened as a side effect of walking somewhere, which
worked but was discoverable by nobody — including the companion, which tried inventing `dig`, then
reached for `build_structure` (the opposite operation: it *places* blocks and costs materials out of
its inventory), then tried to run a skill name as a command. None of it produced a staircase.

The bundled **staircase-mine** skill now just asks where you want the entrance and which way, walks
there, checks it has a pickaxe, and issues one `dig`. Everything else is done for it.

### It goes where you send it

**Every `goto` in the mod travelled to world origin.** The destination was read before it had been
filled in, so the coordinates were always zero, while the command otherwise looked entirely
successful. Anything built on movement inherited it: "go to these coordinates" walked to spawn, and
the old staircase procedure aimed every step at the same wrong place — which is what was seen as a
companion setting off toward the horizon and never coming back.

Coordinates are also read more forgivingly now. Asked to go to a position it had just been shown, the
companion would echo it back in the game's own format — brackets, commas, long decimals — which the
command then rejected; one session spent 39 turns re-sending the same rejected string. Brackets,
commas and decimals are all accepted, and positions are reported as plain block coordinates.

### Two companions no longer take turns

With more than one out, only one could think at a time. Whichever was busiest held the floor: a
companion working a long task kept every other one frozen for the duration, so the second looked
broken while the first worked. Speech was part of it — one companion talking stopped every other one
thinking until it finished the sentence.

Each companion now thinks independently, and how many can do so at once is configurable
(`llm.maxConcurrentRequests`, default 2 — raise it for a hosted model or a bigger roster, since every
extra slot is another request running at the same time).

### It stops claiming to do things it isn't

Three separate versions of the same problem, all fixed:

- **"I'm following you"** while standing still. Asked to follow you, the companion would write your
  name in lowercase, the lookup was case-sensitive, and it matched nobody — then waited forever
  without saying so. Names now match regardless of case, and if the player genuinely can't be found it
  says so after a few seconds instead of pretending.
- **Talking sensibly and then doing nothing at all.** On a long session the companion would answer
  perfectly and never act. Its instructions had grown past what the model could hold, and the first
  thing lost was the part telling it how to phrase a command. Conversations are now trimmed to fit
  (`llm.maxPromptChars`), and a well-reasoned answer in the wrong format is recovered rather than
  discarded.
- **Repeating a command that could never work.** A command that failed was retried indefinitely — one
  session logged 39 identical attempts, another 30. After three the companion is told plainly that it
  will not work and to try something else or explain itself.

### Fewer wasted turns

- Invented a command that doesn't exist? The error now lists the ones that do, so it can correct
  itself on the next turn instead of guessing again.
- The list of your skills read to the companion as a second menu of commands, and it tried to run the
  skill names. It now says plainly that those are routines *you* start.

---

## 0.2.4 — It runs on a real server now, and they don't all sound alike

Bundles PlayerEngine 1.0.48. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating.

### It works on a dedicated server

Until now it didn't, and it failed badly: the companion spawned, stood there doing nothing, and the
first time you walked into it the server dropped you with *"Internal server error"*. Every version up
to this one was single-player-only in practice, whatever the description said.

The cause was code that only exists in the Minecraft client being reached by the server. In single
player the two run in the same process, so nothing ever noticed. On a dedicated server the companion's
AI threw on its very first tick and could never take one, and a separate piece of the same problem
crashed the player's connection on contact.

Both are fixed, along with two more of the same kind waiting further along — one in placing blocks,
one in the engine's logging. A companion on a dedicated server now spawns, paths, fights, gathers,
completes tasks and survives a restart.

If you run a server, note that its timings are measured in server ticks. A server running below 20
ticks per second stretches every wait the companion makes, so it will feel slower under load than it
does on your own machine. That is expected, not a fault.

### Each companion can have its own voice

Voice used to be one setting shared by everyone, so a roster of three was three identical voices —
which rather undoes having given them separate names, faces and personalities.

It now belongs to the companion. **`/companion config` → Companions**, and each one has a **Voice**
picker beside its skin, with the common Kokoro voices listed and searchable. `af_`/`am_` are American
female/male, `bf_`/`bm_` British, and you can type any voice id your setup serves if it isn't in the
list.

Leave it on *(use global tts.voice)* and nothing changes — that companion uses the shared setting
exactly as before. The Voice tab keeps that shared setting as the fallback.

### The config screen now admits when it can't reach the server

`/companion config` opens on a multiplayer server, lets you change anything, saves without complaint
— and changes nothing. It edits the copy of the config on *your* machine, and the server reads its
own.

It said nothing about this, and the log even claimed the changes would apply on the next world load,
which they never would. Every tab now says which file it is editing, in red when that file is going
nowhere.

To configure companions on a server, edit `config/aicompanion.json` **on the server** and run
`/companion reload`. Voice, personality, skins, LLM settings — all of it lives there. In single player
and on a LAN host the screen works exactly as it always has.

---

## 0.2.3 — More than one of them, and a way to hand them things

Bundles PlayerEngine 1.0.47. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. Your existing companion keeps its name, personality and skin — its
settings are moved into the new list format for you on first launch, and your previous config file is
kept as `aicompanion.json.bak` in case you want it back.

### Right-click a companion to open its inventory

Empty hand, no sneaking, and a window opens: all 36 of its storage slots, its four armour slots and
its offhand, with your own inventory below. Drag items across, shift-click them across, take things
back.

Until now there was no way to hand a companion anything. Items reached it only by being dropped on
the floor for it to notice, or by asking it to go and fetch them itself — and armour had no route at
all, despite the companion wearing armour, taking less damage for it, and wearing it out. Now you can
kit one out in ten seconds.

Shift-clicking armour or a shield from your side puts it straight into the slot it belongs in rather
than the first free storage slot. The window closes itself if the companion walks out of reach, and
only its owner can open it.

### You can have more than one, and tell them apart

Two companions used to be two copies of the same character: same name, same personality, same face,
both answering to everything you said. Chat showed the same name for both, and every command —
`stats`, `come`, `despawn`, `skill` — picked one of them arbitrarily and didn't tell you which.

Now identity comes from a list, and the easiest place to edit it is in game: **`/companion config` →
Companions**. Each configured companion gets its own section — name, description, personality, skin —
and there's an "Add a companion" box at the bottom. Type a name, save, and you can spawn it:

```
/companion spawn Rook
```

A bare `/companion spawn` takes the first companion that isn't already out, so with two configured
you can spawn both without naming either.

Once they're out:

- **Talk to one of them.** "Rook, go and scout north" reaches Rook and nobody else — the name is
  stripped before the model sees it. An unaddressed line goes to whichever is nearest, rather than to
  all of them at once, so asking a question once costs one reply instead of two.
- **Target commands.** Every subcommand takes an optional name: `/companion stats Rook`,
  `/companion come Rook`, `/companion skill Rook Harvest`. Without one you get the nearest of your
  own, chosen consistently rather than at random. And the replies now say who they mean — "Rook
  coming to 74, 64, 349" instead of "Companion coming to 74, 64, 349".
- **See who's out.** `/companion list` gives you every companion, how far away, how healthy, and what
  it's currently working on. Spawning one that's already out is refused, with a note on where it is —
  in a previous session someone assumed their companion had died, spawned a second, and ended up with
  two identical ones wandering the same valley.
- **The radar handles all of them.** One labelled marker each, instead of a single marker flickering
  between two bodies.

Configure one companion and nothing changes — it behaves exactly as before.

*(For anyone who hand-edits the JSON: identity used to live in a `companion` block. That is now the
`companions` list, and your old block is folded into it automatically. `/companion config` no longer
has an Identity tab because it edits the list directly.)*

### Companions no longer talk each other into circles

Two companions standing near each other used to overhear and answer each other, and each answer
prompted another answer. Every one of those is a full request to the model, with nobody talking to
them. One session logged 382 of them — including four near-identical sentences in ninety seconds as
each reply set off the next.

This is now off by default (`behavior.aiCrossTalk`). Turn it on if you want them chatting between
themselves and you're running a local model where turns are free.

---

## 0.2.2 — Plays by the same rules you do

Bundles PlayerEngine 1.0.46. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. The new settings are added to `config/aicompanion.json` automatically and
all default to sensible values.

### Mobs can see it now

- **Hostile mobs hunt the companion the way they hunt you.** They never did before. The companion is a
  `LivingEntity` rather than a real player, and every vanilla mob looks for targets through a
  hard-coded player filter — so zombies, skeletons, spiders and creepers walked straight past it. The
  only way it ever got attacked was retaliation after it swung first. Covers the goal-based hostiles
  plus piglins and hoglins; piglins honour the gold-armour truce, so kit it out and it can walk the
  Nether. Endermen still only aggro on real players staring at them.
- This also switched on the whole defence system, which had **never run once** in a real world: all of
  it was gated on "is a mob targeting the companion", which was permanently false. Rather than ship
  that untested, it is behind four settings — fighting back and shield use are on, **running away is
  off**. The companion stands its ground and keeps working instead of abandoning a farm to sprint over
  the horizon. Turn `behavior.defenseFleeFromHostiles` on when you want to see the other behaviour.

### Gear wears out

- **Fixed: swords and axes never lost durability.** Mining wore tools down correctly, but melee did
  not — the attack path was hand-written and never called the hook that damages a weapon on hit. A
  companion's sword was effectively unbreakable.
- **Fixed: armour never wore out either**, for the same class of reason. It absorbed damage forever.
- **Fixed: it swung far too fast.** Attack cadence was a flat 5 ticks regardless of weapon, at full
  damage every time — roughly 4 swings a second where you get 1.6 with the same diamond sword. It now
  uses the real attack-speed attribute and scales damage by cooldown like a player does.
- Melee reach tightened from 4 blocks to the vanilla 3.
- Known gap: **shields still don't wear out.** Fishing rods, shears, hoes and flint & steel always did.

### Buildings get built instead of appearing

- **`build_structure` now builds with its hands.** It walks to the site, picks somewhere to stand that
  the plan doesn't need to fill and that it can get back out of, and places a couple of blocks a tick
  within arm's reach — swinging, making placement noise, and moving along as each spot is used up.
  Previously up to 256 blocks a tick with no reach check and no walking, which meant a small house
  materialised in a single tick at coordinates the model named from anywhere on the map.
- Courses go bottom-up and outside-in, so it never stands where a block has to go and a flat roof
  partly scaffolds itself. Doorways and windows are carved **first** — that's what stops it sealing
  itself inside a house cut into a hillside.
- If it genuinely can't reach the rest (the middle of a wide roof), it says so and finishes those from
  where it stands rather than stalling. If it gets shut in anyway, it digs out — and isn't charged
  twice for the blocks it has to replace.
- **A partly-finished build now says it's partly finished** and can be resumed by repeating the same
  description; it places only what's missing. Interrupting a build mid-way keeps the plan instead of
  throwing it away.
- Duplicate positions in a generated plan are collapsed, which also stops the material bill charging
  twice for one block.
- Fixed a bug this exposed: retrying a build re-measured the ground against the part already standing,
  read its own roof as ground level, and could refuse a job that was half done.
- `behavior.buildPhysicalPlacement: false` restores the old instant behaviour exactly, if you want it.

### Farming and harvesting replant again

- **Fixed: harvested tiles were left bare, and the companion insisted it was out of seeds while
  carrying hundreds of them.** Ask it to tend a field and it would strip the wheat, plant nothing, and
  answer *"I need to get more wheat seeds"* with 418 of them in its pockets. Telling it otherwise
  didn't help — it genuinely could not see them.
- The cause was that the farm only ever looked at the companion's **hotbar**, nine of its thirty-six
  slots. Seeds anywhere else were invisible, and since planting needs the stack in hand, a full
  backpack of seeds could never be used. Anything the companion picked up while working — which is
  most of what it carries — landed out of view.
- It now searches the whole inventory and moves a stack into its hand when it finds one, the way you
  would. Bone meal had exactly the same blind spot and is fixed with it, so it will fertilise from the
  backpack too.

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
