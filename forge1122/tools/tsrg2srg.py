#!/usr/bin/env python3
"""Convert joined.tsrg (obf -> SRG, TSRG format) to classic .srg for SpecialSource.

SRG format emitted:
    CL: <obf-class> <srg-class>
    FD: <obf-owner>/<obf-field> <srg-owner>/<srg-field>
    MD: <obf-owner>/<obf-method> <obf-desc> <srg-owner>/<srg-method> <srg-desc>
Descriptors are remapped obf -> SRG using the class map.
"""
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "joined.tsrg"
DST = sys.argv[2] if len(sys.argv) > 2 else "joined.srg"

DESC_CLASS = re.compile(r"L([^;]+);")


def remap_desc(desc, classmap):
    def sub(m):
        return "L" + classmap.get(m.group(1), m.group(1)) + ";"
    return DESC_CLASS.sub(sub, desc)


with open(SRC, "r", encoding="utf-8", newline="") as f:
    lines = [ln.rstrip("\r\n") for ln in f]

# Pass 1: class map.
classmap = {}
for ln in lines:
    if not ln or ln.startswith("\t"):
        continue
    parts = ln.split()
    if len(parts) == 2:
        classmap[parts[0]] = parts[1]

out = []
owner_obf = owner_srg = None
for ln in lines:
    if not ln:
        continue
    if not ln.startswith("\t"):
        parts = ln.split()
        if len(parts) == 2:
            owner_obf, owner_srg = parts
            out.append(f"CL: {owner_obf} {owner_srg}")
        continue
    parts = ln.strip().split()
    if len(parts) == 2:
        # field: obf-name srg-name
        out.append(f"FD: {owner_obf}/{parts[0]} {owner_srg}/{parts[1]}")
    elif len(parts) == 3:
        # method: obf-name descriptor srg-name
        srg_desc = remap_desc(parts[1], classmap)
        out.append(f"MD: {owner_obf}/{parts[0]} {parts[1]} {owner_srg}/{parts[2]} {srg_desc}")
    else:
        print(f"WARN: unparsed member line: {ln!r}", file=sys.stderr)

with open(DST, "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
print(f"wrote {DST}: {len(out)} lines")
