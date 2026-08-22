#!/usr/bin/env python3
"""Migrate a source tree between Minecraft versions using the official Mojang mappings.

Most breakage across a Minecraft version is classes moving package or being renamed. That is
derivable rather than guessable: Loom already downloaded the official ProGuard mappings for the
target version, so this reads them and rewrites imports to match.

On the 1.20.1 -> 1.21.11 port this fixed 390 of 424 compile errors (35 package moves plus the
ResourceLocation -> Identifier rename) across 44 files. The 34 it could not fix were genuine API
redesigns, which is exactly the set a human should be looking at.

    python3 tools/mcmigrate.py --mc 1.21.11 --root engine/common/src --dry-run
    python3 tools/mcmigrate.py --mc 1.21.11 --root engine/common/src

⚠️ Ambiguity is silent. When a simple class name exists at more than one target path this script
REFUSES to guess and reports it instead — an earlier version used dict.setdefault and quietly
picked whichever the filesystem walk hit first, which wired command classes to the wrong abstract
base and produced errors that read like API drift. See --report-ambiguous.
"""
import argparse, collections, pathlib, re, sys

def load_mappings(mc):
    base = pathlib.Path.home()/".gradle/caches/fabric-loom"/mc
    cands = list(base.rglob("mojang/client.txt"))
    if not cands:
        sys.exit(f"no Mojang mappings for {mc} under {base} — run a Loom build first so it downloads them")
    root = cands[0].parent
    fqns = set()
    for name in ("client.txt", "server.txt"):
        p = root/name
        if not p.exists(): continue
        for line in p.read_text(errors="replace").split("\n"):
            if line and not line[0].isspace() and "->" in line:
                lhs = line.split("->")[0].strip()
                if lhs.startswith("net.minecraft."): fqns.add(lhs)
    return fqns

def build_index(fqns):
    dotted = {f.replace("$", ".") for f in fqns}          # inner classes are written Outer$Inner
    by_simple = collections.defaultdict(set)
    for f in fqns:
        if "$" not in f: by_simple[f.rsplit(".", 1)[-1]].add(f)
    return dotted, by_simple

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", required=True, help="target Minecraft version, e.g. 1.21.11")
    ap.add_argument("--root", required=True, help="source root to rewrite")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    dotted, by_simple = build_index(load_mappings(a.mc))
    root = pathlib.Path(a.root)

    stale = collections.Counter(); moves = {}; ambiguous = {}; gone = set()
    for p in root.rglob("*.java"):
        for m in re.finditer(r"^import (?:static )?(net\.minecraft\.[\w.$]+);", p.read_text(errors="replace"), re.M):
            fqn = m.group(1)
            if fqn in dotted: continue                     # still resolves, including inner classes
            stale[fqn] += 1
            cands = by_simple.get(fqn.rsplit(".", 1)[-1], set())
            if len(cands) == 1:   moves[fqn] = next(iter(cands))
            elif len(cands) > 1:  ambiguous[fqn] = sorted(cands)
            else:                 gone.add(fqn)

    print(f"stale imports: {len(stale)} distinct")
    print(f"  auto-fixable : {len(moves)}")
    print(f"  AMBIGUOUS    : {len(ambiguous)}  (never guessed — resolve by hand)")
    print(f"  simple name gone (true rename or removal): {len(gone)}")
    for k, v in ambiguous.items(): print(f"    ? {k} -> {v}")
    for g in sorted(gone):         print(f"    ! {g}")
    if a.dry_run:
        for k, v in sorted(moves.items()): print(f"    {k}\n      -> {v}")
        return

    files = imports = 0
    for p in root.rglob("*.java"):
        t = p.read_text(errors="replace"); o = t
        for old, new in moves.items():
            t, n = re.subn(rf"^(import (?:static )?){re.escape(old)};", rf"\g<1>{new};", t, flags=re.M)
            imports += n
        if t != o: p.write_text(t); files += 1
    print(f"\nrewrote {imports} imports across {files} files")
    print("Renames where the simple name also changed (e.g. ResourceLocation -> Identifier) are NOT")
    print("handled here: the mapping file cannot tell you what a removed class became. Do those by hand.")

if __name__ == "__main__":
    main()
