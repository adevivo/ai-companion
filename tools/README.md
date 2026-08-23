# Porting tools

Written during the 1.20.1 → 1.21.11 port. They exist because most of the work in moving Minecraft
versions is mechanical, and doing it by hand both wastes time and hides the parts that are not.

## `mcmigrate.py` — class moves and renames, derived from Mojang's own mappings

Loom downloads the official ProGuard mappings for the target version. This reads them, works out
which of your imports no longer resolve, and rewrites the ones with exactly one new home.

```bash
python3 tools/mcmigrate.py --mc 1.21.11 --root engine/common/src --dry-run   # look first
python3 tools/mcmigrate.py --mc 1.21.11 --root engine/common/src
```

On 1.20.1 → 1.21.11 this fixed **390 of 424** compile errors. What it leaves is the interesting
part: genuine API redesigns, and classes whose simple name changed (a mapping file cannot tell you
that `ResourceLocation` became `Identifier` — it only knows `ResourceLocation` is gone).

**It never guesses.** When a simple name has more than one candidate it reports and skips.

## `quilt2mojmap.py` — Quilt/Yarn names to Mojang names, across versions

The mod half of a port changes two things at once: the mapping namespace, and the Minecraft
version. Both are derivable. Every tiny-v2 file Loom caches carries an `intermediary` column, and
intermediary names are stable across versions by construction, so they are the pivot:

```
quilt name @ old version  ->  intermediary  ->  mojmap name @ new version
```

```bash
python3 tools/quilt2mojmap.py \
  --from ~/.gradle/caches/fabric-loom/1.20.1/org.quiltmc.quilt-mappings.*/mappings.tiny \
  --to   ~/.gradle/caches/fabric-loom/1.21.11/loom.mappings.*/mappings.tiny \
  --root aicompanion/src --members --dry-run \
  --holdout .../jmods/java.base.jmod --holdout .../gson-2.13.2.jar ...
```

On the 1.20.1 -> 1.21.11 mod port this resolved **all 71** Minecraft imports with no ambiguity, and
applied 158 member renames. Pass a path rather than a version when a version has been built more
than one way — it refuses to pick between two cached mapping sets.

### The three guards, and what each one caught

A member rewrite is keyed on a bare name, and a bare name does not say what the receiver is.

1. **Names the source tree declares itself.** Quilt `getWorld` is Mojmap `level`, and our own
   `getWorld()` is neither.
2. **Names anything on the classpath declares.** `--holdout` takes jars *and* JDK `.jmod` files —
   both are ZIP archives, so one constant-pool scanner covers the JDK, Gson, the engine and the
   rest. This is what stops `String.strip()` becoming `stripFormatting`, `Stream.findFirst()`
   becoming `findNearestMapStructure` and `Map.keySet()` becoming `propertySet`: **135 wrong edits
   across 27 names.**
3. **Comments, string literals and qualified names are masked.** Otherwise
   `net.minecraft.server.network.ServerPlayerEntity` has its `network` package segment rewritten to
   `user`, which is exactly what a bare-name rule will do to a package.

⚠️ **Give it every dependency, Fabric API included.** That one was missed on the real run, and
`FabricEntityTypeBuilder.spawnGroup` was duly "translated" to `category`. It was the only false
positive of its kind and the compiler caught it, but the compiler only catches the ones where the
new name does not also exist on the receiver.

### Passes run together, never in sequence

Quilt `PlayerInventory` is Mojmap `Inventory`, and Quilt `Inventory` is Mojmap `Container`. Applied
in sequence, the qualified-name pass produces `net.minecraft.world.entity.player.Inventory` and the
simple-name pass then reads that as a Quilt name and "translates" it to `...player.Container` — a
real class, in a plausible package, and not the one that was meant. Every pass matches the text as
it arrived and the edits are spliced once.

The greedy match has a second edge: `net.minecraft.text.Text.literal` arrives whole and is not
itself a class name, so the resolver takes the longest prefix that is one and keeps what followed.

### What it deliberately leaves behind

2,373 member names map to more than one Mojmap name. It reports and skips them, because the
receiver is what decides — and **the compiler knows the receiver**. Feed the build log to a repair
pass keyed on javac's file, line and caret column, and `getWorld` becomes `level()` on an entity and
`getLevel()` on a command source *in the same expression*. That resolved 217 renames on this port
and is the right division of labour: derive what is derivable, and let the type checker settle the
rest.

## `upstream_merge.py` — re-apply our fork onto a re-based upstream tree

Upstream keeps a branch per Minecraft version. Moving versions means taking their tree and
re-applying our work, and there is a real merge base for that: upstream `main` @ `13fddc1` is the
exact tree we forked. See `engine/UPSTREAM_FORK.txt`.

```bash
git clone --branch main --depth 5 https://github.com/Goodbird-git/PlayerEngine /tmp/pe-base
python3 tools/upstream_merge.py --base /tmp/pe-base/src --ours fix/perception-radius \
        --files /tmp/modified.txt --out /tmp/merged
```

The point of it is the normalisation: upstream renamed every package, so a raw merge conflicts on
every import line. Normalising base and ours into upstream's namespace first turned **68 whole-file
conflicts into 11 real ones** — 56 files merged with no human input.

## Two failure modes that are silent

Both of these were hit for real, and both produced errors that read like Minecraft API drift when
they were actually tooling bugs.

1. **Ambiguous simple names.** This tree has eight: `Command`, `CommandException`, `Debug`,
   `FarmCommand`, `FishCommand`, `FollowCommand`, `GotoCommand`, `TokenStorage`. A resolver keyed on
   the simple name will silently pick one. Both tools refuse instead.
2. **Fully-qualified references in code bodies.** Rewriting import lines is not enough; 17 live
   `adris.altoclef.X` references survived a pass that only touched imports.

The general lesson: **when a search says a well-known code path has no callers, disbelieve the
search before you disbelieve the code.**
