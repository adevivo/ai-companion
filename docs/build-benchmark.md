# Build benchmark — comparing models on the build DSL

A fixed set of build descriptions for scoring one model against another on the `build_structure`
codegen path. Satisfies the `[open] Model benchmark` item in `TODO.md`, and doubles as the shooting
script for a model-comparison video.

## The one rule that makes or breaks it

**Every prompt must force codegen.** Ordinary rectangular shapes are generated in Java by
`TemplateLibrary` and never reach the model, so *"build a shelter"* measures nothing at all.

A description reaches the model if it contains one of `DescriptionParser.DISQUALIFIERS`
(`DescriptionParser.java:49`) —

```
l-shaped / l shaped, t-shaped, u-shaped, storey, story, stories, storeys, second floor,
upstairs, staircase, stairs, room, rooms, kitchen, bedroom, bathroom, hallway, corridor,
garden, flower, rose, tree, fireplace, chimney, furniture, bookshelf, painting, balcony,
porch, veranda, courtyard, basement, cellar, attic, modern, castle, mansion, villa, tower,
dome, arch, pyramid, window box, moat, fence around, surrounded by
```

— **or** names something no template covers at all. The three templates match `house`/`hut`/`shelter`
(`HouseTemplate`), `field` (`FieldTemplate`), and `wall`/`path`/`bridge` (`LineTemplate`); anything
else falls through.

Each prompt below carries its trigger in the **Forces codegen via** column. Verify by watching the
log: a template match logs no codegen request, a decline logs the word that caused it.

## The prompts

Append ` at (X Y Z)` to each, using the same coordinates every run.

| # | Prompt | Forces codegen via | What it measures |
|---|---|---|---|
| **B1** | a cobblestone pillar one block wide and five blocks tall | no template for "pillar" | Baseline. Literal size (**expect exactly 5** `setBlock`), `baseY` on top of ground, valid syntax |
| **B2** | a hollow stone brick room seven blocks wide, five deep and four tall, with a glass pane window centred in each of the four walls and a two block tall doorway in the south wall | `room` | **Flagship.** Glass-pane axis in four orientations; hollow rather than filled; 2-tall doorway (player scale); literal dimensions |
| **B3** | an L-shaped stone platform one block thick and flush with the ground, the long arm nine by three and the short arm four by three | `l-shaped` | Non-rectangular geometry; `baseY = groundLevel` (flush, not perched). **Expect ~39** blocks |
| **B4** | a staircase of stone blocks two blocks wide rising six blocks, each step one up and one forward | `staircase` | Vertical reasoning; exact step count; width held across the run |
| **B5** | a hollow cylindrical stone brick tower seven blocks across and eight tall, open at the top | `tower` | **Integer-only circle approximation.** `Math.sin` and floats are forbidden by the codegen prompt — this is where models emit them anyway |
| **B6** | a stone lined pool flush with the ground, five by five and one block deep, filled with water | no template for "pool" | Water containment: source blocks at `baseY` only, solid on all four sides, no flooding |
| **B7** | a stone corridor two blocks wide, three tall and twelve long, lit with torches along its length | `corridor` | Torches on solid supports rather than floating; 3-tall player scale; literal length |
| **B8** | a two room stone cottage with an interior dividing wall, a doorway between the rooms, and a window in each room | `room`, `rooms` | **The aspiration gap** — does it *build* the rooms or only describe them in comments? Observed 2026-09-24: comments promised kitchen/bedroom/study, code delivered a box |

**Core four if time is short: B1, B2, B5, B8.** Baseline, the known discriminator, the
forbidden-construct trap, and the comment-vs-execution gap.

## Scorecard — one row per (model × prompt)

| Field | Values |
|---|---|
| Ran? | executed / refused / truncated / parse error |
| `setBlock` count | actual vs expected |
| Forbidden constructs | none / `Math.*` / `while` / float literal / function definition |
| Geometry | correct / partial / wrong |
| `baseY` placement | flush / sits on top / buried / floating |
| Hollow where asked | yes / filled solid (B2, B5) |
| Glass-pane axis | correct / wrong (B2) |
| Water contained | yes / flooded (B6) |
| Torches supported | yes / floating (B7) |
| Comment–execution gap | comments promise features the code omits: yes / no |
| Cost | prompt + completion tokens, wall-clock latency |

Keep the **raw program** from every run. It is the dev data *and* the video's b-roll.

## Method — controlling the confounds

1. **Superflat creative world, identical coordinates every run.** Terrain must never be a variable;
   `baseY` behaviour is one of the things being scored.
2. **Clear the site between runs.** A partial build left standing changes what the next one looks like.
3. **Fresh conversation state per run** — `/companion despawn` then `spawn`, so one run's history
   cannot inform the next.
4. **Set `behavior.buildCostsMaterials: false`.** Otherwise a model that asks for more blocks is
   penalised for the companion's inventory rather than for its plan.
5. ⚠️ **Raise `llm.maxTokens` well above 4000, or record every truncation.** This is the main
   confound: at 4000 a verbose model is penalised for verbosity instead of quality, and a program cut
   off mid-statement scores as bad geometry when it was actually bad budgeting.
6. **`llm.useGrammar` is not a variable.** JSON mode is scoped to the agent turn; the codegen path is
   always plain text.
7. **Persona is not a variable either.** Codegen uses `Prompts.buildStructurePrompt`, which carries no
   persona — worth confirming once, then ignoring.
8. Hold `temperature` fixed across models, and run each prompt **more than once** on at least one model
   to see how much of the spread is just sampling noise.

## Known-good seed result

`nvidia/nemotron-3-super-120b-a12b:free`, 2026-09-24: built a square house with correctly-oriented
glass panes and no door. Grok has been observed getting pane orientation **wrong** on the same kind of
request — that contrast is the seed of the whole comparison, and B2 exists to measure it properly.
