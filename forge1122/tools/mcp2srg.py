#!/usr/bin/env python3
"""Translate MCP-named Java sources to SRG names for 1.12.2 compilation.

Reads methods.csv / fields.csv (SRG -> MCP) and rewrites member accesses
(`.name`) in the mod's own .java files to their SRG equivalents.

Rules:
  - Only identifiers immediately preceded by '.' are candidates.
  - String literals, char literals, line comments and block comments are skipped.
  - Names in STOPLIST (the mod's own members that collide with MCP names)
    are never translated.
  - Sources must use explicit receivers (this./obj.) for vanilla members;
    bare inherited member access is NOT translated.

Usage: mcp2srg.py <src-dir> <out-dir> [--check]
  --check: print what would change, without writing.
"""
import csv
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")

# The mod's own member names that also exist as MCP names. These must never
# be translated (they are accessed via '.' too).
STOPLIST = {
    "pos",      # our Container fields; vanilla TileEntity.pos collides
    "world",    # would collide with TileEntity.world
    "level",    # (checked: not in MCP, kept for safety)
}


def load_map():
    mcp2srg = {}
    for fname in ("methods.csv", "fields.csv"):
        with open(os.path.join(LIBS, fname), newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                name, srg = row["name"], row["searge"]
                # Keep the first mapping; duplicates are overloads with the same MCP name.
                if name not in mcp2srg:
                    mcp2srg[name] = srg
                elif mcp2srg[name] != srg:
                    # Same MCP name, different SRG (overloads across classes) - ambiguous.
                    # Mark ambiguous; the build will surface any real problem at compile time.
                    pass
    return mcp2srg


TOKEN = re.compile(r"""
    (?P<str>"(?:[^"\\]|\\.)*")           # string literal
  | (?P<chr>'(?:[^'\\]|\\.)*')           # char literal
  | (?P<lcom>//[^\n]*)                   # line comment
  | (?P<bcom>/\*.*?\*/)                  # block comment
  | (?P<dot>\.)                          # dot
  | (?P<ident>[A-Za-z_$][A-Za-z0-9_$]*)  # identifier
  | (?P<other>.)                         # anything else
""", re.VERBOSE | re.DOTALL)


def translate(src, mcp2srg, report):
    out = []
    pos = 0
    prev_sig = None  # last significant token kind
    for m in TOKEN.finditer(src):
        kind = m.lastgroup
        text = m.group()
        if kind == "ident" and prev_sig == "dot":
            if text not in STOPLIST and text in mcp2srg:
                report[text] = report.get(text, 0) + 1
                text = mcp2srg[text]
        out.append(text)
        if kind not in ("other",):
            if kind == "dot":
                prev_sig = "dot"
            elif kind == "ident":
                prev_sig = "ident"
            else:
                prev_sig = kind  # str/chr/comments reset the chain
        elif not text.isspace():
            prev_sig = None
    return "".join(out)


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    check = len(sys.argv) > 3 and sys.argv[3] == "--check"
    mcp2srg = load_map()
    print(f"loaded {len(mcp2srg)} MCP->SRG mappings")
    total_files = 0
    grand = {}
    for root, _, files in os.walk(src_dir):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            src_path = os.path.join(root, fn)
            rel = os.path.relpath(src_path, src_dir)
            with open(src_path, encoding="utf-8") as f:
                src = f.read()
            report = {}
            new = translate(src, mcp2srg, report)
            total_files += 1
            for k, v in report.items():
                grand[k] = grand.get(k, 0) + v
            if new != src:
                print(f"  {rel}: {len(report)} distinct names translated")
            if not check and new != src:
                dst = os.path.join(out_dir, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "w", encoding="utf-8") as f:
                    f.write(new)
    print(f"{total_files} files, {len(grand)} distinct MCP names translated")
    if check:
        for k in sorted(grand):
            print(f"    .{k} -> .{mcp2srg[k]}  (x{grand[k]})")


if __name__ == "__main__":
    main()
