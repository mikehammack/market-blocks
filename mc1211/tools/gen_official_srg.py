#!/usr/bin/env python3
"""Derive official Mojang -> SRG mappings for SpecialSource reobfuscation.

Joins:
  obf_to_official.srg  (from client_mappings.txt via proguard2srg.py)
  joined-1.21.1.tsrg   (MCPConfig: obf -> SRG)

on the obfuscated (owner, member, descriptor) key, and emits
libs/official_to_srg.srg:
    CL: <official-class> <srg-class>
    MD: <official-owner>/<official-method> <official-desc> <srg-owner>/<srg-method> <srg-desc>
    FD: <official-owner>/<official-field> <srg-owner>/<srg-field>

Members present on only one side keep their official name (identity mapping),
mirroring the forge1122 gen_mappings.py approach.

Usage: gen_official_srg.py (run from mc1211/; reads libs/, writes libs/)
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")

DESC_CLS = re.compile(r"L([^;]+);")


def parse_srg(path):
    classes, methods, fields = {}, {}, {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("CL:"):
                _, obf, new = line.split()
                classes[obf] = new
            elif line.startswith("MD:"):
                _, obf_full, obf_desc, new_full, new_desc = line.split()
                obf_owner, obf_name = obf_full.rsplit("/", 1)
                new_owner, new_name = new_full.rsplit("/", 1)
                methods[(obf_owner, obf_name, obf_desc)] = (new_owner, new_name, new_desc)
            elif line.startswith("FD:"):
                _, obf_full, new_full = line.split()
                obf_owner, obf_field = obf_full.rsplit("/", 1)
                new_owner, new_field = new_full.rsplit("/", 1)
                fields[(obf_owner, obf_field)] = (new_owner, new_field)
    return classes, methods, fields


def parse_tsrg(path):
    """MCPConfig 1.21.1 joined.tsrg is TSRG v2 ("tsrg2 obf srg id"):

        tsrg2 obf srg id
        <obf-class> <srg-class> <id>
        \t<obf-member> <srg-member> <id>                    (fields)
        \t<obf-member> <obf-desc> <srg-member> <id>        (methods)
        \t\tstatic                                      (skip)
        \t\t<idx> o p_<id>_ <id>                        (param info, skip)

    Returns (obf->srg) class/method/field maps keyed like parse_srg.
    Method descriptors in the file are in the OBF namespace; the SRG-side
    descriptor is derived via the class map.
    """
    classes, methods, fields = {}, {}, {}
    with open(path, encoding="utf-8", newline="") as f:
        lines = [ln.rstrip("\r\n") for ln in f]
    assert lines[0].startswith("tsrg2"), f"unexpected header: {lines[0]!r}"
    owner_obf = owner_srg = None
    for ln in lines[1:]:
        if not ln:
            continue
        if not ln.startswith("\t"):
            parts = ln.split()
            assert len(parts) == 3, f"bad class line: {ln!r}"
            owner_obf, owner_srg = parts[0], parts[1]
            classes[owner_obf] = owner_srg
            continue
        if ln.startswith("\t\t"):
            continue  # 'static' marker / parameter info
        parts = ln.strip().split()
        if len(parts) == 3:
            # field: obf-name srg-name id
            fields[(owner_obf, parts[0])] = (owner_srg, parts[1])
        elif len(parts) == 4:
            # method: obf-name obf-desc srg-name id
            methods[(owner_obf, parts[0], parts[1])] = (owner_srg, parts[2], parts[1])
        else:
            print(f"WARN: unparsed tsrg member line: {ln!r}", file=sys.stderr)
    # rewrite SRG-side descriptors obf -> SRG
    def remap_desc(desc):
        return DESC_CLS.sub(lambda m: "L" + classes.get(m.group(1), m.group(1)) + ";", desc)
    methods = {k: (o, n, remap_desc(d)) for k, (o, n, d) in methods.items()}
    return classes, methods, fields


def main():
    off_classes, off_methods, off_fields = parse_srg(os.path.join(LIBS, "obf_to_official.srg"))
    srg_classes, srg_methods, srg_fields = parse_tsrg(os.path.join(LIBS, "joined-1.21.1.tsrg"))
    print(f"official: classes={len(off_classes)} methods={len(off_methods)} fields={len(off_fields)}")
    print(f"srg:      classes={len(srg_classes)} methods={len(srg_methods)} fields={len(srg_fields)}")

    # class-name sanity: how many SRG class names differ from official?
    diff = sum(1 for o, s in off_classes.items()
               if o in srg_classes and srg_classes[o] != s)
    only_srg = sum(1 for o in srg_classes if o not in off_classes)
    only_off = sum(1 for o in off_classes if o not in srg_classes)
    print(f"class-name check: srg!=official: {diff}, only-in-tsrg: {only_srg}, only-in-mojmap: {only_off}")

    out = []
    for obf, official in sorted(off_classes.items()):
        srg = srg_classes.get(obf, official)  # identity fallback
        out.append(f"CL: {official} {srg}")

    n_m = n_f = miss_m = miss_f = 0
    for (obf_owner, obf_name, obf_desc), (off_owner, off_name, off_desc) in sorted(off_methods.items()):
        hit = srg_methods.get((obf_owner, obf_name, obf_desc))
        if hit:
            srg_owner, srg_name, srg_desc = hit
        else:
            srg_owner, srg_name, srg_desc = off_owner, off_name, off_desc
            miss_m += 1
        out.append(f"MD: {off_owner}/{off_name} {off_desc} {srg_owner}/{srg_name} {srg_desc}")
        n_m += 1
    for (obf_owner, obf_field), (off_owner, off_field) in sorted(off_fields.items()):
        hit = srg_fields.get((obf_owner, obf_field))
        if hit:
            srg_owner, srg_field = hit
        else:
            srg_owner, srg_field = off_owner, off_field
            miss_f += 1
        out.append(f"FD: {off_owner}/{off_field} {srg_owner}/{srg_field}")
        n_f += 1

    dst = os.path.join(LIBS, "official_to_srg.srg")
    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print(f"wrote {dst}: {len(out)} lines (methods={n_m} fields={n_f}, "
          f"identity fallback: methods={miss_m} fields={miss_f})")


if __name__ == "__main__":
    main()
