#!/usr/bin/env python3
"""Convert Mojang client_mappings.txt (ProGuard format) to SpecialSource .srg.

Emits libs/obf_to_official.srg:
    CL: <obf> <official>
    FD: <obf-owner>/<obf-field> <official-owner>/<official-field>
    MD: <obf-owner>/<obf-method> <obf-desc> <official-owner>/<official-method> <official-desc>

Usage: proguard2srg.py [client_mappings.txt] [obf_to_official.srg]
"""
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "client_mappings.txt"
DST = sys.argv[2] if len(sys.argv) > 2 else "obf_to_official.srg"

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")   # official -> obf (Mojang order)
# member line with optional proguard line-number prefix "12:34:"
MEMBER_RE = re.compile(r"^    (?:\d+:\d+:)?(\S+) (\S+?)(\(.*\))? -> (\S+)$")

PRIMITIVES = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C",
    "short": "S", "int": "I", "long": "J", "float": "F", "double": "D",
}


def internal(name):
    # official binary-ish name -> internal form (handle '.' inner-class seps)
    return name.replace(".", "/")


def to_desc(t, off2obf):
    """Official type string -> descriptor in obf namespace (or official if unknown)."""
    dims = 0
    while t.endswith("[]"):
        t = t[:-2]
        dims += 1
    if t in PRIMITIVES:
        d = PRIMITIVES[t]
    else:
        obf = off2obf.get(internal(t), internal(t))
        d = "L" + obf + ";"
    return "[" * dims + d


def main():
    with open(SRC, encoding="utf-8") as f:
        lines = f.readlines()

    obf2off, off2obf = {}, {}
    members = []  # (obf_owner, kind, obf_name, official_owner, official_name, params, ret)
    owner_obf = owner_off = None
    n_class = 0
    for ln in lines:
        ln = ln.rstrip("\n")
        if not ln or ln.startswith("#"):
            continue
        m = CLASS_RE.match(ln)
        if m:
            owner_off, owner_obf = internal(m.group(1)), internal(m.group(2))
            obf2off[owner_obf] = owner_off
            off2obf[owner_off] = owner_obf
            n_class += 1
            continue
        m = MEMBER_RE.match(ln)
        if m and owner_obf:
            rtype, name, params, obf_name = m.groups()
            if params is not None:
                members.append((owner_obf, "m", obf_name, owner_off, name,
                                params[1:-1], rtype))
            else:
                members.append((owner_obf, "f", obf_name, owner_off, name, None, rtype))
        elif ln.startswith("    "):
            print(f"WARN: unparsed member line: {ln!r}", file=sys.stderr)

    out = []
    n_m = n_f = 0
    for obf_owner, kind, obf_name, off_owner, off_name, params, rtype in members:
        if kind == "f":
            out.append(f"FD: {obf_owner}/{obf_name} {off_owner}/{off_name}")
            n_f += 1
        else:
            # constructor: proguard lists it under the simple class name
            simple = off_owner.rsplit("/", 1)[-1].split("$")[-1]
            if off_name == simple:
                off_name = "<init>"
            pdesc = "".join(to_desc(p.strip(), off2obf)
                            for p in params.split(",") if p.strip())
            rdesc = to_desc(rtype, off2obf)
            # official-side descriptor needs official class names
            def to_desc_off(t):
                dims = 0
                while t.endswith("[]"):
                    t = t[:-2]
                    dims += 1
                d = PRIMITIVES[t] if t in PRIMITIVES else "L" + internal(t) + ";"
                return "[" * dims + d
            pdesc_off = "".join(to_desc_off(p.strip())
                                for p in params.split(",") if p.strip())
            obf_desc = f"({pdesc}){rdesc}"
            off_desc = f"({pdesc_off}){to_desc_off(rtype)}"
            if obf_name == "<init>":
                off_name = "<init>"
            out.append(f"MD: {obf_owner}/{obf_name} {obf_desc} "
                       f"{off_owner}/{off_name} {off_desc}")
            n_m += 1

    cls_lines = [f"CL: {o} {obf2off[o]}" for o in sorted(obf2off)]
    with open(DST, "w", encoding="utf-8") as f:
        f.write("\n".join(cls_lines + out) + "\n")
    print(f"classes={n_class} methods={n_m} fields={n_f} -> {DST}")


if __name__ == "__main__":
    main()
