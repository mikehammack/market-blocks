#!/usr/bin/env python3
"""Download the MC 26.3 manual-javac toolchain (idempotent).

Layout (all under libs/):
  minecraft-26.3-client.jar            official Mojang client (piston-meta)
  mc-deps/                             vanilla 26.3 libraries, maven layout (linux rules)
  fabric-loader-0.19.5.jar
  neoforge-26.3.0.3-beta-universal.jar
  fancymodloader-loader-12.0.0.jar     (net.neoforged.fancymodloader:loader:12.0.0)
  neoforge-bus-8.0.5.jar               (net.neoforged:bus:8.0.5)

NOT downloaded -- built from source by tools/build_stubs_1263.sh:
  sponge-mixin.jar                     compile-only mixin annotation stubs
  fabric-api-0.160.5+26.3.jar          compile-only fabric-api stubs
                                       (the mod only touches 3 fabric-api classes;
                                        the real jar is not needed to compile)
  stubs/                               compiled stub classes (Dist, etc.)

Pinned versions (resolved 2026-09-24):
  MC=26.3  LOADER=0.19.5  NEOFORGE=26.3.0.3-beta  FML_LOADER=12.0.0  BUS=8.0.5
"""
import json
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")
MC_DEPS = os.path.join(LIBS, "mc-deps")
UA = {"User-Agent": "marketblocks-build/1.0"}

NEO_MAVEN = "https://maven.neoforged.net/releases"
FABRIC_MAVEN = "https://maven.fabricmc.net"

MC_VER = "26.3"
LOADER_VER = "0.19.5"
NEO_VER = "26.3.0.3-beta"
FML_LOADER_VER = "12.0.0"
BUS_VER = "8.0.5"


def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return json.load(r)


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"  skip (exists): {os.path.relpath(dest, LIBS)}")
        return False
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print(f"  downloading: {os.path.relpath(dest, LIBS)}")
    req = urllib.request.Request(url, headers=UA)
    try:
        with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
    except Exception:
        if os.path.exists(dest):
            os.remove(dest)
        raise
    print(f"    -> {os.path.getsize(dest)} bytes")
    return True


def rules_allow(lib):
    # Mirror Mojang launcher semantics for the "rules" list.
    rules = lib.get("rules")
    if not rules:
        return True
    allowed = False
    for rule in rules:
        os_rule = rule.get("os", {})
        name = os_rule.get("name")
        match = name is None or name == "linux"
        if rule["action"] == "allow" and match:
            allowed = True
        elif rule["action"] == "disallow" and match:
            return False
    return allowed


def main():
    os.makedirs(LIBS, exist_ok=True)
    print("== Resolving 26.3 via piston-meta ==")
    manifest = get_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    ver_url = next(v["url"] for v in manifest["versions"] if v["id"] == MC_VER)
    ver = get_json(ver_url)
    print("  version json:", ver_url)

    print("== Mojang client jar ==")
    dl = ver["downloads"]["client"]
    dest = os.path.join(LIBS, f"minecraft-{MC_VER}-client.jar")
    download(dl["url"], dest)
    # sanity: sha1 check when freshly downloaded
    if os.path.exists(dest):
        import hashlib
        h = hashlib.sha1()
        with open(dest, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest() != dl["sha1"]:
            print(f"  !! SHA1 MISMATCH for minecraft-{MC_VER}-client.jar; deleting")
            os.remove(dest)
            sys.exit(1)
        print("  sha1 ok")

    print("== Vanilla 26.3 libraries -> mc-deps/ ==")
    n = 0
    for lib in ver["libraries"]:
        if not rules_allow(lib):
            continue
        art = lib["downloads"]["artifact"]
        # natives are never needed on the compile classpath
        if "natives" in art["path"]:
            continue
        dest = os.path.join(MC_DEPS, art["path"])
        if download(art["url"], dest):
            n += 1
    print(f"  mc-deps new downloads: {n}")

    print("== Fabric loader ==")
    download(f"{FABRIC_MAVEN}/net/fabricmc/fabric-loader/{LOADER_VER}/fabric-loader-{LOADER_VER}.jar",
             os.path.join(LIBS, f"fabric-loader-{LOADER_VER}.jar"))

    print("== NeoForge ==")
    download(f"{NEO_MAVEN}/net/neoforged/neoforge/{NEO_VER}/neoforge-{NEO_VER}-universal.jar",
             os.path.join(LIBS, f"neoforge-{NEO_VER}-universal.jar"))
    download(f"{NEO_MAVEN}/net/neoforged/fancymodloader/loader/{FML_LOADER_VER}/loader-{FML_LOADER_VER}.jar",
             os.path.join(LIBS, f"fancymodloader-loader-{FML_LOADER_VER}.jar"))
    download(f"{NEO_MAVEN}/net/neoforged/bus/{BUS_VER}/bus-{BUS_VER}.jar",
             os.path.join(LIBS, f"neoforge-bus-{BUS_VER}.jar"))

    print("== All artifacts ready ==")
    print("Next: ./tools/build_stubs_1263.sh   (compile-only API stubs)")


if __name__ == "__main__":
    main()
