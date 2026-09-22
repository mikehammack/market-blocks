#!/usr/bin/env python3
"""Generate SpecialSource mapping files from joined.srg + MCP snapshot CSVs.

Outputs (in build/):
  srg2mcp.srg  - SRG -> MCP (remap vanilla/Forge jars for compilation)
  mcp2srg.srg  - MCP -> SRG (reobfuscate the compiled mod jar; class names are
                 identical in both schemes, so this is the exact inverse)

Members with no MCP name keep their SRG name (identity mapping) so the
reobf pass still resolves them.
"""
import csv
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")
BUILD = os.path.join(HERE, "..", "build")


def load_csv(path):
    m = {}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            srg, name = row["searge"], row["name"]
            if srg not in m:
                m[srg] = name
    return m


def parse_srg(path):
    """Returns (class_map obf->srg, methods[(obf_owner, obf_name, obf_desc)] -> srg_name,
    fields[(obf_owner, obf_field)] -> srg_field)."""
    class_map = {}
    methods = {}
    fields = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("CL:"):
                _, obf, srg = line.split()
                class_map[obf] = srg
            elif line.startswith("MD:"):
                _, obf_full, obf_desc, srg_full, srg_desc = line.split()
                obf_owner, obf_name = obf_full.rsplit("/", 1)
                srg_owner, srg_name = srg_full.rsplit("/", 1)
                methods[(obf_owner, obf_name, obf_desc)] = (srg_owner, srg_name, srg_desc)
            elif line.startswith("FD:"):
                _, obf_full, srg_full = line.split()
                obf_owner, obf_field = obf_full.rsplit("/", 1)
                srg_owner, srg_field = srg_full.rsplit("/", 1)
                fields[(obf_owner, obf_field)] = (srg_owner, srg_field)
    return class_map, methods, fields


DESC_CLS = re.compile(r"L([^;]+);")


def convert_desc(desc, class_map):
    def repl(m):
        cls = m.group(1)
        return "L" + class_map.get(cls, cls) + ";"
    return DESC_CLS.sub(repl, desc)


def main():
    methods_csv = load_csv(os.path.join(LIBS, "methods.csv"))
    fields_csv = load_csv(os.path.join(LIBS, "fields.csv"))
    class_map, srg_methods, srg_fields = parse_srg(os.path.join(LIBS, "joined.srg"))
    print(f"classes={len(class_map)} methods={len(srg_methods)} fields={len(srg_fields)}")
    print(f"mcp methods={len(methods_csv)} fields={len(fields_csv)}")

    os.makedirs(BUILD, exist_ok=True)
    srg2mcp, mcp2srg = [], []
    missing_m, missing_f = 0, 0
    for (owner, _name, desc), (srg_owner, srg_name, srg_desc) in sorted(srg_methods.items()):
        mcp_name = methods_csv.get(srg_name, srg_name)
        if mcp_name == srg_name:
            missing_m += 1
        srg2mcp.append(f"MD: {srg_owner}/{srg_name} {srg_desc} {srg_owner}/{mcp_name} {srg_desc}")
        mcp2srg.append(f"MD: {srg_owner}/{mcp_name} {srg_desc} {srg_owner}/{srg_name} {srg_desc}")
    for (owner, _field), (srg_owner, srg_field) in sorted(srg_fields.items()):
        mcp_field = fields_csv.get(srg_field, srg_field)
        if mcp_field == srg_field:
            missing_f += 1
        srg2mcp.append(f"FD: {srg_owner}/{srg_field} {srg_owner}/{mcp_field}")
        mcp2srg.append(f"FD: {srg_owner}/{mcp_field} {srg_owner}/{srg_field}")

    with open(os.path.join(BUILD, "srg2mcp.srg"), "w", encoding="utf-8") as f:
        f.write("\n".join(srg2mcp) + "\n")
    with open(os.path.join(BUILD, "mcp2srg.srg"), "w", encoding="utf-8") as f:
        f.write("\n".join(mcp2srg) + "\n")
    # Class renames only (obfuscated -> SRG/MCP class names). The Forge
    # universal jar references vanilla classes by their obfuscated names
    # (e.g. "nf" for ResourceLocation), so it needs this pass first.
    with open(os.path.join(BUILD, "obf2srg-classes.srg"), "w", encoding="utf-8") as f:
        for obf, srg in sorted(class_map.items()):
            f.write(f"CL: {obf} {srg}\n")
    print(f"wrote obf2srg-classes.srg ({len(class_map)} lines)")
    print(f"wrote srg2mcp.srg ({len(srg2mcp)} lines), mcp2srg.srg ({len(mcp2srg)} lines)")
    print(f"identity (no MCP name): methods={missing_m} fields={missing_f}")


if __name__ == "__main__":
    main()
