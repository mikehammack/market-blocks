#!/usr/bin/env python3
"""Download all 1.12.2 Forge toolchain artifacts (idempotent - skips existing files).

Artifacts (all from public Maven / Mojang):
  - Forge 1.12.2-14.23.5.2860 universal jar (SRG-named)
  - MCP config 1.12.2 (joined.tsrg: obf -> SRG)
  - MCP snapshot 20171003 == stable_39 (SRG -> MCP names)
  - SpecialSource 1.8.5 (applies the SRG remap to vanilla jars)
  - Vanilla 1.12.2 client + server jars (obfuscated, from Mojang piston-meta)
  - Minimal compile deps: guava, gson, commons-lang3, log4j-api, jsr305
"""
import json
import os
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(ROOT, "..", "libs")
os.makedirs(LIBS, exist_ok=True)

FORGE_MAVEN = "https://maven.minecraftforge.net"
CENTRAL = "https://repo1.maven.org/maven2"

FILES = [
    # (url, local name)
    (f"{FORGE_MAVEN}/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar",
     "forge-1.12.2-14.23.5.2860-universal.jar"),
    (f"{FORGE_MAVEN}/de/oceanlabs/mcp/mcp_config/1.12.2/mcp_config-1.12.2.zip",
     "mcp_config-1.12.2.zip"),
    (f"{FORGE_MAVEN}/de/oceanlabs/mcp/mcp_snapshot/20180814-1.12/mcp_snapshot-20180814-1.12.zip",
     "mcp_snapshot-20180814-1.12.zip"),
    (f"{CENTRAL}/net/md-5/SpecialSource/1.8.5/SpecialSource-1.8.5.jar",
     "SpecialSource-1.8.5.jar"),
    (f"{CENTRAL}/com/google/guava/guava/21.0/guava-21.0.jar", "guava-21.0.jar"),
    (f"{CENTRAL}/com/google/code/gson/gson/2.8.0/gson-2.8.0.jar", "gson-2.8.0.jar"),
    (f"{CENTRAL}/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar",
     "commons-lang3-3.5.jar"),
    (f"{CENTRAL}/org/apache/logging/log4j/log4j-api/2.8.1/log4j-api-2.8.1.jar",
     "log4j-api-2.8.1.jar"),
    (f"{CENTRAL}/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
     "jsr305-3.0.2.jar"),
]


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"  skip (exists): {os.path.basename(dest)}")
        return
    print(f"  downloading: {os.path.basename(dest)}")
    req = urllib.request.Request(url, headers={"User-Agent": "marketblocks-build/1.0"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    print(f"    -> {os.path.getsize(dest)} bytes")


def main():
    print("== Fetching toolchain artifacts ==")
    for url, name in FILES:
        download(url, os.path.join(LIBS, name))

    # Vanilla 1.12.2 client + server jars from Mojang piston-meta.
    for side in ("client", "server"):
        dest = os.path.join(LIBS, f"minecraft-1.12.2-{side}.jar")
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            print(f"  skip (exists): {os.path.basename(dest)}")
            continue
        print(f"  resolving vanilla 1.12.2 {side} url via piston-meta...")
        req = urllib.request.Request(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
            headers={"User-Agent": "marketblocks-build/1.0"})
        with urllib.request.urlopen(req, timeout=60) as r:
            manifest = json.load(r)
        ver_url = next(v["url"] for v in manifest["versions"] if v["id"] == "1.12.2")
        with urllib.request.urlopen(urllib.request.Request(
                ver_url, headers={"User-Agent": "marketblocks-build/1.0"}),
                timeout=60) as r:
            ver = json.load(r)
        dl_url = ver["downloads"][side]["url"]
        download(dl_url, dest)

    # Extract the MCP mapping files we need.
    for zname, members in [
        ("mcp_config-1.12.2.zip", ["config/joined.tsrg"]),
        ("mcp_snapshot-20180814-1.12.zip", ["methods.csv", "fields.csv", "params.csv"]),
    ]:
        zpath = os.path.join(LIBS, zname)
        with zipfile.ZipFile(zpath) as z:
            for m in members:
                dest = os.path.join(LIBS, os.path.basename(m))
                if not os.path.exists(dest):
                    print(f"  extracting {m} from {zname}")
                    with z.open(m) as src, open(dest, "wb") as f:
                        f.write(src.read())

    print("== All artifacts ready ==")


if __name__ == "__main__":
    sys.exit(main())
