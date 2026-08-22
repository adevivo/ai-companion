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
