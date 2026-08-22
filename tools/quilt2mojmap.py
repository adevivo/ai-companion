#!/usr/bin/env python3
"""Translate a source tree from Quilt/Yarn names to Mojang names, across Minecraft versions.

Two changes at once — mapping namespace (Quilt -> Mojmap) and Minecraft version — and both are
derivable rather than guessable. Loom has already downloaded a tiny-v2 mapping file for each
version, and every one of them carries an `intermediary` column. Intermediary names are stable
across Minecraft versions by construction, so they are the pivot:

    quilt name @ old version  ->  intermediary  ->  mojmap name @ new version

That single join covers classes, methods and fields. Anything that fails to come out the far side
is a genuine removal or redesign, which is exactly the set worth a human's attention.

    python3 tools/quilt2mojmap.py --from 1.20.1 --to 1.21.11 --root aicompanion/src --dry-run
    python3 tools/quilt2mojmap.py --from 1.20.1 --to 1.21.11 --root aicompanion/src

Never guesses, in three separate ways:

  * A quilt name that maps to more than one mojmap name is reported and skipped, never resolved by
    picking one. (`mcmigrate.py` learned this the hard way — see tools/README.md.)
  * Member renames are skipped when the same name is declared by the source tree itself. Yarn
    `getWorld` becomes Mojmap `level`, and rewriting our own `getWorld()` would be silent damage.
  * Comments and string literals are masked before any rewrite. An import-only pass misses live
    references in code bodies, and a body-inclusive pass corrupts javadoc and error messages unless
    they are held out.
"""
import argparse, collections, pathlib, re, sys

TINY_HEADER = "tiny\t2\t0\tofficial\tintermediary\tnamed"


def find_mappings(spec):
    """The named-namespace tiny file Loom cached, given a version or a path to one.

    Quilt lands in a directory named for its own coordinate; a Mojmap+Parchment layered spec lands
    in `loom.mappings.<ver>.layered+hash.<n>-v2`. Both are tiny v2 with the same three namespaces,
    so the caller does not have to care which it got — but a version that has been built more than
    one way has more than one cached set, and which one is meant is not for this script to decide.
    Pass the path in that case.
    """
    if "/" in spec:
        return pathlib.Path(spec)
    base = pathlib.Path.home() / ".gradle/caches/fabric-loom" / spec
    cands = sorted(base.glob("*/mappings.tiny"))
    if not cands:
        sys.exit(f"no cached mappings for {spec} under {base} — run a Loom build for it first")
    if len(cands) > 1:
        sys.exit(f"more than one mapping set cached for {spec}, refusing to pick — pass a path:\n  " +
                 "\n  ".join(str(c) for c in cands))
    return cands[0]


def parse_tiny(path):
    """-> (classes, members): intermediary -> named, for classes and for methods/fields alike.

    Method and field ids (`method_10866`, `field_1234`) are globally unique in intermediary, so one
    flat member map is enough and the enclosing class does not need tracking. Where an id does
    appear under several classes it is an override, and the name agrees.
    """
    classes, members = {}, {}
    for line in path.read_text(errors="replace").split("\n"):
        if not line or line[0] != "c" and line[0] != "\t":
            continue
        depth = len(line) - len(line.lstrip("\t"))
        parts = line.split("\t")
        if depth == 0 and parts[0] == "c" and len(parts) >= 4:
            classes[parts[2]] = parts[3]
        elif depth == 1 and parts[1] in ("m", "f") and len(parts) >= 6:
            # \t<m|f>\t<desc>\t<official>\t<intermediary>\t<named>
            members[parts[4]] = parts[5]
    return classes, members


def join(old, new):
    """Compose old-named -> intermediary -> new-named, keeping every candidate.

    Returned as name -> set, so ambiguity survives to be reported rather than being flattened by
    whichever mapping line happened to be read last.
    """
    out = collections.defaultdict(set)
    for inter, old_name in old.items():
        if inter in new:
            out[old_name].add(new[inter])
    return out


def declared_locally(root):
    """Method and field names the source tree defines for itself — off limits to a rename.

    A member rename is keyed on the bare name, so it cannot tell `player.getWorld()` (Minecraft,
    now `level()`) from `config.getWorld()` (ours, unchanged). Holding out every name the tree
    declares costs a few genuine renames and prevents silent corruption of our own API.
    """
    names = set()
    decl = re.compile(
        r"^\s*(?:public|protected|private|static|final|abstract|synchronized|native|default|\s)*"
        r"[\w.<>\[\],?\s]+?\s+(\w+)\s*[(;=]", re.M)
    for p in root.rglob("*.java"):
        names.update(decl.findall(p.read_text(errors="replace")))
    return names


# Constant-pool entry sizes past the tag byte. Utf8 (1) is variable and handled separately; Long
# (5) and Double (6) additionally consume the following pool slot, per JVMS 4.4.5.
CP_SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
            15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def class_member_names(data):
    """Every method and field name a single .class file declares.

    Reads the constant pool only far enough to step over it, then walks the field and method
    tables. Taking declarations rather than every Utf8 constant keeps string literals and type
    descriptors out of the result, which matters — this set is used to *suppress* renames, and an
    over-broad one silently costs real translations.
    """
    import struct
    if data[:4] != b"\xca\xfe\xba\xbe":
        return ()
    pool, i, n = {}, 10, struct.unpack_from(">H", data, 8)[0]
    idx = 1
    while idx < n:
        tag = data[i]; i += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, i)[0]
            pool[idx] = data[i + 2:i + 2 + ln]
            i += 2 + ln
        else:
            i += CP_SIZES[tag]
            if tag in (5, 6):
                idx += 1
        idx += 1
    i += 6                                                  # access_flags, this_class, super_class
    i += 2 + 2 * struct.unpack_from(">H", data, i)[0]       # interfaces
    names = []
    for _ in range(2):                                      # fields, then methods — same shape
        count = struct.unpack_from(">H", data, i)[0]; i += 2
        for _ in range(count):
            names.append(pool.get(struct.unpack_from(">H", data, i + 2)[0], b"").decode("utf-8", "replace"))
            attrs = struct.unpack_from(">H", data, i + 6)[0]
            i += 8
            for _ in range(attrs):
                i += 6 + struct.unpack_from(">I", data, i + 2)[0]
    return names


def declared_by_dependencies(paths):
    """Member names declared by everything on the classpath that is not Minecraft.

    The same blindness that makes `declared_locally` necessary applies to libraries: Quilt's
    `strip` maps 1:1 to Mojmap `stripFormatting`, and `String.strip()` is not Minecraft. Jars and
    JDK `.jmod` files are both ZIP archives, so one scanner covers the JDK, Gson, the engine and
    the rest.
    """
    import zipfile
    names = set()
    for path in paths:
        p = pathlib.Path(path)
        if not p.exists():
            print(f"  holdout MISSING, skipped: {p}", file=sys.stderr)
            continue
        with zipfile.ZipFile(p) as z:
            entries = [e for e in z.namelist() if e.endswith(".class")]
            for e in entries:
                try:
                    names.update(class_member_names(z.read(e)))
                except Exception:
                    pass                                    # a malformed entry costs one class
        print(f"  holdout {len(entries):6} classes  {p.name}", file=sys.stderr)
    return names


def mask(src):
    """Blank out comments and string/char literals, preserving offsets.

    Rewrites are computed against the masked text and applied to the original by span, so a javadoc
    `{@link Text}` or an exception message quoting a class name is left exactly as written.
    """
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] in "/*":
            end = src.find("\n", i) if src[i + 1] == "/" else src.find("*/", i + 2) + 2
            if end <= 0:
                end = n
            for j in range(i, end):
                if src[j] != "\n":
                    out[j] = " "
            i = end
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            for k in range(i + 1, min(j, n)):
                out[k] = " "
            i = j + 1
        else:
            i += 1
    return "".join(out)


# A fully-qualified type reference: package segments, then a type name. Anchored to the package
# roots this codebase actually uses, so an ordinary field chain is not mistaken for one.
FQ_REF = re.compile(r"\b(?:net|com|org|java|javax|me|dev|io)(?:\.\w+)*\.[A-Z]\w*(?:\.[A-Z]\w*)*\b")


def mask_qualified(src):
    """`mask`, and additionally blank every fully-qualified type reference.

    Only the member pass wants this. Its pattern is "a word after a dot", and every segment of a
    qualified name is exactly that — `java.util.UUID` would have `UUID` rewritten to whatever
    Mojmap calls the unrelated Minecraft field of the same name, and
    `net.minecraft.server.network.ServerPlayerEntity` would have its `network` package segment
    rewritten to `user`. Both were observed. Qualified names belong to the class pass, which
    rewrites them whole.
    """
    masked = mask(src)
    out = list(masked)
    for m in FQ_REF.finditer(masked):
        for j in range(*m.span()):
            out[j] = " "
    return "".join(out)


def substitute(src, pattern, resolve, masker=mask):
    """Match against the masked text, splice into the real text."""
    edits = [(m.start(), m.end(), r) for m in pattern.finditer(masker(src))
             if (r := resolve(m)) is not None]
    for start, end, repl in reversed(edits):
        src = src[:start] + repl + src[end:]
    return src, len(edits)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="src_mc", required=True, help="source Minecraft version, e.g. 1.20.1")
    ap.add_argument("--to", dest="dst_mc", required=True, help="target Minecraft version, e.g. 1.21.11")
    ap.add_argument("--root", required=True, help="source root to rewrite")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--members", action="store_true", help="also rewrite method and field names")
    ap.add_argument("--holdout", action="append", default=[], metavar="JAR",
                    help="jar or .jmod whose declared member names must never be rewritten; "
                         "repeatable. Give it every non-Minecraft dependency, JDK included.")
    a = ap.parse_args()

    old_c, old_m = parse_tiny(find_mappings(a.src_mc))
    new_c, new_m = parse_tiny(find_mappings(a.dst_mc))
    print(f"{a.src_mc}: {len(old_c)} classes, {len(old_m)} members")
    print(f"{a.dst_mc}: {len(new_c)} classes, {len(new_m)} members")

    cls_all, mem_all = join(old_c, new_c), join(old_m, new_m)
    root = pathlib.Path(a.root)

    # Classes: keyed on the fully-qualified quilt name, so ambiguity is nearly absent by
    # construction. Inner classes are written Outer$Inner in the mapping and Outer.Inner in source.
    cls = {k: next(iter(v)) for k, v in cls_all.items() if len(v) == 1}
    cls_ambig = {k: sorted(v) for k, v in cls_all.items() if len(v) > 1}
    fqn = {k.replace("/", "."): v.replace("/", ".") for k, v in cls.items()}
    inner = {k.replace("/", ".").replace("$", "."): v.replace("/", ".").replace("$", ".")
             for k, v in cls.items() if "$" in k}

    # Simple names, for references in code bodies. Far more collision-prone: many packages hold a
    # `Type` or a `State`, and two quilt classes with one simple name may land on two mojmap names.
    simple = collections.defaultdict(set)
    for k, v in cls.items():
        if "$" not in k:
            simple[k.rsplit("/", 1)[-1]].add(v.rsplit("/", 1)[-1])
    simple_ok = {k: next(iter(v)) for k, v in simple.items() if len(v) == 1 and next(iter(v)) != k}

    local = declared_locally(root) | declared_by_dependencies(a.holdout)
    mem = {k: next(iter(v)) for k, v in mem_all.items()
           if len(v) == 1 and next(iter(v)) != k and k not in local}
    mem_ambig = {k: sorted(v) for k, v in mem_all.items()
                 if len(v) > 1 and any(n != k for n in v) and k not in local}
    mem_local = {k for k, v in mem_all.items()
                 if k in local and any(n != k for n in v)}

    print(f"\njoined on intermediary:")
    print(f"  classes renamed or moved : {sum(1 for k, v in cls.items() if k != v)}")
    print(f"  class names AMBIGUOUS    : {len(cls_ambig)}  (skipped)")
    print(f"  members renamed          : {len(mem)}")
    print(f"  member names AMBIGUOUS   : {len(mem_ambig)}  (skipped)")
    print(f"  members the tree declares itself, held out: {len(mem_local)}")

    # What the source actually asks for, and what has no answer — the genuine redesigns.
    wanted, unmapped = set(), set()
    for p in root.rglob("*.java"):
        for m in re.finditer(r"^import (?:static )?(net\.minecraft\.[\w.$]+);",
                             p.read_text(errors="replace"), re.M):
            wanted.add(m.group(1))
            if m.group(1) not in fqn and m.group(1) not in inner:
                unmapped.add(m.group(1))
    print(f"\nimports in tree: {len(wanted)}   translatable: {len(wanted) - len(unmapped)}")
    if unmapped:
        print(f"  NO MAPPING (gone at {a.dst_mc}, or a name this join cannot reach):")
        for u in sorted(unmapped):
            print(f"    ! {u}")

    # Qualified names, wherever they appear. Imports are the bulk of them, but not all: a live
    # `net.minecraft.network.PacketByteBuf buf = ...` in a method body is the same thing and needs
    # the same rewrite, and an import-only pass leaves it behind to fail as an API error later.
    # `com.mojang` as well as `net.minecraft`: blaze3d is obfuscated too, so it is in the mapping
    # and it is renamed there — Quilt's `InputUtil` is Mojmap's `InputConstants`. Anything outside
    # the map resolves to None and is left alone, so a wider pattern costs nothing.
    qual_re = re.compile(r"\b((?:net\.minecraft|com\.mojang)(?:\.\w+)+)\b")
    ref_re = re.compile(r"\b([A-Z]\w*)\b")
    mem_re = re.compile(r"(?<=\.)(\w+)\b")

    if a.dry_run:
        # The member map is keyed on a bare name, so what matters is not its size but which of it
        # actually touches this tree. Print that, with counts, for review before anything is written.
        hits = collections.Counter()
        for p in root.rglob("*.java"):
            for m in mem_re.finditer(mask_qualified(p.read_text(errors="replace"))):
                if m.group(1) in mem:
                    hits[m.group(1)] += 1
        print(f"\nmember renames that would fire: {len(hits)} distinct, "
              f"{sum(hits.values())} occurrences")
        for k, c in hits.most_common():
            print(f"  {c:4}  {k:32} -> {mem[k]}")
        return

    files = n_qual = n_ref = n_mem = 0
    for p in root.rglob("*.java"):
        text = original = p.read_text(errors="replace")

        text, c = substitute(text, qual_re, lambda m: fqn.get(m.group(1)) or inner.get(m.group(1)))
        n_qual += c
        text, c = substitute(text, ref_re, lambda m: simple_ok.get(m.group(1)))
        n_ref += c
        if a.members:
            text, c = substitute(text, mem_re, lambda m: mem.get(m.group(1)), mask_qualified)
            n_mem += c

        if text != original:
            p.write_text(text)
            files += 1

    print(f"\nrewrote {files} files: {n_qual} qualified names, {n_ref} type references, "
          f"{n_mem} members")


if __name__ == "__main__":
    main()
