#!/usr/bin/env python3
"""MCP (snapshot) -> SRG name lookup, plus verification of planned API names.

Usage:
    mcp_lookup.py check < names.txt     # each line: M <mcp-name>  or  F <mcp-name>
    mcp_lookup.py dump                  # print all mappings (debug)
"""
import csv
import os
import sys

LIBS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "libs")


def load():
    methods, fields = {}, {}
    with open(os.path.join(LIBS, "methods.csv"), newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            methods[row["name"]] = row["searge"]
    with open(os.path.join(LIBS, "fields.csv"), newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            fields[row["name"]] = row["searge"]
    return methods, fields


def main():
    methods, fields = load()
    if len(sys.argv) > 1 and sys.argv[1] == "dump":
        for k in sorted(methods):
            print(f"M {k} -> {methods[k]}")
        for k in sorted(fields):
            print(f"F {k} -> {fields[k]}")
        return
    if len(sys.argv) > 1 and sys.argv[1] == "check":
        missing = []
        for line in sys.stdin:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            kind, name = line.split(None, 1)
            table = methods if kind == "M" else fields
            srg = table.get(name)
            if srg is None:
                missing.append(line)
                print(f"MISSING {line}")
            else:
                print(f"OK      {line} -> {srg}")
        if missing:
            print(f"\n{len(missing)} missing", file=sys.stderr)
            sys.exit(1)
        print("\nall found")


if __name__ == "__main__":
    main()
