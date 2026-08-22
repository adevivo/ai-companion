#!/usr/bin/env python3
"""Three-way merge our fork's changes onto a re-based upstream tree.

We fork Goodbird-git/PlayerEngine, and upstream maintains a branch per Minecraft version. Moving
to a new version means taking their tree and re-applying our work. There IS a real merge base —
upstream `main` @ 13fddc1 is the exact tree we forked, recorded in engine/UPSTREAM_FORK.txt — so
this is a genuine diff3 rather than hand-copying.

The trick that makes it work: upstream renamed every package (adris.altoclef -> com.player2.
playerengine, baritone -> ...automaton) and several classes. Merged raw, every import line
conflicts. Normalising base and ours into upstream's namespace FIRST turned 68 whole-file
conflicts into 11 real ones on the 1.20.1 -> 1.21.11 port; 56 files merged with no human input.

    git clone --branch main --depth 5 https://github.com/Goodbird-git/PlayerEngine /tmp/pe-base
    python3 tools/upstream_merge.py --base /tmp/pe-base/src --ours <our-branch> --out /tmp/merged

Then review /tmp/merged for conflict markers, resolve, and copy into engine/common.

⚠️ Two failure modes that are silent, both hit for real:
  1. A simple class name that exists at two target paths. Never let the resolver guess — this
     tree has 8 such names (Command, CommandException, Debug, FarmCommand, FishCommand,
     FollowCommand, GotoCommand, TokenStorage).
  2. Fully-qualified references in code BODIES, not just import lines. Rewriting imports alone
     left 17 live `adris.altoclef.X` references behind.
"""
import argparse, os, pathlib, re, subprocess, sys

CLASS_RENAMES = {"AltoClefController": "PlayerEngineController",
                 "AltoClefCommands":   "PlayerEngineCommands"}
FIELD_RENAMES  = {"altoClefMsgBuffer": "playerEngineMsgBuffer"}
PATH_RENAMES   = {"AltoClefCommands": "PlayerEngineCommands", "AltoClefController": "PlayerEngineController",
                  "Settings": "PlayerEngineSettings", "Debug": "util/Debug"}
# classes hoisted out of the engine into the version-neutral core library
CORE = {"BehaviorConfig","BrainTurnContext","EmbeddingsConfig","LlmConfig","MemoryConfig","MemoryGate",
        "MemoryHealth","PlayerPreferences","RosterGuard","ServerPolicy","TtsConfig","CompanionTickGuard"}

def new_rel(f):
    """Old fork-relative path -> path under com/player2/playerengine."""
    if f.startswith("main/java/baritone/"):
        return "automaton/" + f[len("main/java/baritone/"):]
    r = f[len("autoclef/java/adris/altoclef/"):]
    stem = r[:-5]
    if stem in PATH_RENAMES: return PATH_RENAMES[stem] + ".java"
    if r.startswith("commandsystem/"): return "commands/base/" + r[len("commandsystem/"):]
    return r

def index(newroot):
    by = {}
    dupes = {}
    for p in newroot.rglob("*.java"):
        fqn = str(p.relative_to(newroot)).replace("/", ".")[:-5]
        by.setdefault(p.stem, []).append(fqn)
    for k, v in by.items():
        if len(v) > 1: dupes[k] = v
    return by, dupes

def normalize(src, newpkg, by, dupes):
    def resolve(fqn):
        parts = fqn.split(".")
        for i in range(len(parts), 0, -1):
            c = parts[i-1]
            if c in CORE: return ".".join([f"com.neovetta.aicompanion.core.{c}"] + parts[i:])
            if c in dupes: return None                 # ambiguous: refuse, report
            if c in by:    return ".".join([by[c][0]] + parts[i:])
        return None
    unresolved = []
    def fix(m):
        st, fqn = m.group(1) or "", m.group(2)
        cls = fqn.rpartition(".")[2]
        if fqn == "adris.altoclef.Settings": fqn = "adris.altoclef.PlayerEngineSettings"
        r = resolve(fqn)
        if r: return f"import {st}{r};"
        unresolved.append(fqn); return m.group(0)
    src = re.sub(r"^import (static )?((?:adris|baritone)\.[\w.]+);", fix, src, flags=re.M)
    for o, n in {**CLASS_RENAMES, **FIELD_RENAMES}.items():
        src = re.sub(rf"\b{o}\b", n, src)
    # fully-qualified references in code bodies, skipping string literals
    parts = re.split(r'("(?:[^"\\]|\\.)*")', src)
    def fqfix(chunk):
        def rep(m):
            r = resolve(m.group(0).replace("adris.altoclef.", "").replace(".", ".") ) if False else None
            cls = m.group(1)
            if cls in CORE: return f"com.neovetta.aicompanion.core.{cls}"
            if cls in dupes: unresolved.append(m.group(0)); return m.group(0)
            if cls in by: return by[cls][0]
            unresolved.append(m.group(0)); return m.group(0)
        return re.sub(r"\badris\.altoclef(?:\.[a-z_]\w*)*\.([A-Z]\w*)", rep, chunk)
    src = "".join(p if i % 2 else fqfix(p) for i, p in enumerate(parts))
    src = re.sub(r"^package [\w.]+;", f"package {newpkg};", src, count=1, flags=re.M)
    return src, unresolved

def auto_resolve_imports(text):
    """Union-merge conflicts whose every line is an import — a purely additive collision."""
    pat = re.compile(r"<<<<<<< [^\n]*\n(.*?)\n?\|\|\|\|\|\|\| [^\n]*\n(.*?)\n?=======\n(.*?)\n?>>>>>>> [^\n]*\n", re.S)
    n = [0]
    def rep(m):
        ours, base, theirs = (x.split("\n") if x else [] for x in m.groups())
        rows = [l for l in ours + base + theirs if l.strip()]
        if rows and all(l.startswith("import ") for l in rows):
            seen, out = set(), []
            for l in ours + theirs:
                if l.strip() and l not in seen: seen.add(l); out.append(l)
            n[0] += 1
            return "\n".join(out) + "\n"
        return m.group(0)
    return pat.sub(rep, text), n[0]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True, help="upstream main checkout, .../src")
    ap.add_argument("--ours", required=True, help="git ref holding our 1.20.1-era engine")
    ap.add_argument("--newroot", default="engine/common/src/main/java/com/player2/playerengine")
    ap.add_argument("--out", default="/tmp/merged")
    ap.add_argument("--files", help="file with fork-relative paths, one per line; default = all differing")
    a = ap.parse_args()

    newroot = pathlib.Path(a.newroot)
    by, dupes = index(newroot.parent.parent.parent)      # .../java
    if dupes:
        print(f"note: {len(dupes)} ambiguous simple names; the resolver will refuse these:")
        for k, v in sorted(dupes.items()): print(f"   {k}: {v}")

    files = [l.strip() for l in open(a.files)] if a.files else []
    out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    clean = 0; hand = {}; unres = set()
    for f in files:
        if not f: continue
        nr = new_rel(f)
        theirs_p = newroot/nr
        if not theirs_p.exists(): print(f"  no counterpart: {f}"); continue
        base_s = (pathlib.Path(a.base)/f).read_text(errors="replace")
        r = subprocess.run(["git", "show", f"{a.ours}:engine/src/{f}"], capture_output=True)
        if r.returncode: print(f"  not in ours: {f}"); continue
        pkg = "com.player2.playerengine" + ("." + os.path.dirname(nr).replace("/", ".") if os.path.dirname(nr) else "")
        d = pathlib.Path("/tmp/_m3"); d.mkdir(exist_ok=True)
        for nm, txt in (("base", base_s), ("ours", r.stdout.decode(errors="replace"))):
            s, u = normalize(txt, pkg, by, dupes); unres.update(u); (d/nm).write_text(s)
        (d/"theirs").write_text(theirs_p.read_text(errors="replace"))
        m = subprocess.run(["git", "merge-file", "-p", "--diff3", str(d/"ours"), str(d/"base"), str(d/"theirs")],
                           capture_output=True, text=True)
        fixed, _ = auto_resolve_imports(m.stdout)
        left = fixed.count("<<<<<<<")
        (out/nr.replace("/", "__")).write_text(fixed)
        if left: hand[nr] = left
        else: clean += 1
    print(f"\nmerged clean: {clean}   needing hands: {len(hand)} ({sum(hand.values())} conflicts)")
    for k, v in sorted(hand.items(), key=lambda x: -x[1]): print(f"  {v:>2}  {k}")
    if unres: print(f"\nunresolved references ({len(unres)}):"); [print("   ", u) for u in sorted(unres)]

if __name__ == "__main__":
    main()
