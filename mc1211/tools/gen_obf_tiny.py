#!/usr/bin/env python3
"""Generate official<->obf tiny v1 mappings from Mojang client_mappings.txt.

Emits:
  libs/official_to_obf.tiny   (namespaces: official, obf)
  libs/obf_to_official.tiny   (namespaces: obf, official)

These chain with Fabric's intermediary-1.21.1.tiny (whose first column is
likewise obfuscated despite its "official" header) to remap in either
direction without any fallback ambiguity:
  mod official -> obf -> intermediary   (two tiny-remapper invocations)
  api intermediary -> obf -> official   (two tiny-remapper invocations)

Usage: gen_obf_tiny.py (run from mc1211/; reads libs/client_mappings.txt)
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")  # official -> obf (Mojang order)
MEMBER_RE = re.compile(r"^    (?:\d+:\d+:)?(\S+) (\S+?)(\(.*\))? -> (\S+)$")

PRIMITIVES = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C",
    "short": "S", "int": "I", "long": "J", "float": "F", "double": "D",
}


def internal(name):
    return name.replace(".", "/")


def desc_of(t, classmap):
    """Official type string -> descriptor, class names via classmap (official->X)."""
    dims = 0
    while t.endswith("[]"):
        t = t[:-2]
        dims += 1
    if t in PRIMITIVES:
        d = PRIMITIVES[t]
    else:
        d = "L" + classmap.get(internal(t), internal(t)) + ";"
    return "[" * dims + d


def main():
    with open(os.path.join(LIBS, "client_mappings.txt"), encoding="utf-8") as f:
        lines = f.readlines()

    obf2off, off2obf = {}, {}
    members = []
    owner_off = owner_obf = None
    for ln in lines:
        ln = ln.rstrip("\n")
        if not ln or ln.startswith("#"):
            continue
        m = CLASS_RE.match(ln)
        if m:
            owner_off, owner_obf = internal(m.group(1)), internal(m.group(2))
            obf2off[owner_obf] = owner_off
            off2obf[owner_off] = owner_obf
            continue
        m = MEMBER_RE.match(ln)
        if m and owner_obf:
            rtype, name, params, obf_name = m.groups()
            members.append((owner_off, owner_obf, name, obf_name,
                            params[1:-1] if params else None, rtype))

    o2b = ["v1\tofficial\tobf"]
    b2o = ["v1\tobf\tofficial"]
    for obf, off in sorted(obf2off.items()):
        o2b.append(f"CLASS\t{off}\t{obf}")
        b2o.append(f"CLASS\t{obf}\t{off}")

    identity = {k: k for k in off2obf}  # official-namespace descriptor classmap
    n_m = n_f = 0
    for off_o, obf_o, off_n, obf_n, params, rtype in members:
        if params is None:
            # field
            off_d = desc_of(rtype, identity)  # official ns
            obf_d = desc_of(rtype, off2obf)   # obf ns
            o2b.append(f"FIELD\t{off_o}\t{off_d}\t{off_n}\t{obf_n}")
            b2o.append(f"FIELD\t{obf_o}\t{obf_d}\t{obf_n}\t{off_n}")
            n_f += 1
        else:
            ps = [p.strip() for p in params.split(",") if p.strip()]
            off_d = "(" + "".join(desc_of(p, identity) for p in ps) + ")" + desc_of(rtype, identity)
            obf_d = "(" + "".join(desc_of(p, off2obf) for p in ps) + ")" + desc_of(rtype, off2obf)
            o2b.append(f"METHOD\t{off_o}\t{off_d}\t{off_n}\t{obf_n}")
            b2o.append(f"METHOD\t{obf_o}\t{obf_d}\t{obf_n}\t{off_n}")
            n_m += 1

    with open(os.path.join(LIBS, "official_to_obf.tiny"), "w", encoding="utf-8") as f:
        f.write("\n".join(o2b) + "\n")
    with open(os.path.join(LIBS, "obf_to_official.tiny"), "w", encoding="utf-8") as f:
        f.write("\n".join(b2o) + "\n")
    print(f"classes={len(obf2off)} methods={n_m} fields={n_f}")
    print("wrote libs/official_to_obf.tiny and libs/obf_to_official.tiny")


if __name__ == "__main__":
    main()
