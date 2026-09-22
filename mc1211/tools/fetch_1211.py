#!/usr/bin/env python3
"""Download the complete MC 1.21.1 manual-javac toolchain (idempotent).

Layout (all under mc1211/libs/):
  minecraft-1.21.1-client.jar / -server.jar   obfuscated Mojang jars (piston-meta)
  minecraft-1.21.1-server-inner.jar            inner jar if server is a bundle
  client_mappings.txt                          obf -> official (ProGuard format)
  joined-1.21.1.tsrg                           obf -> SRG (MCPConfig)
  mcp_config-1.21.1-20240808.132146.zip        MCPConfig source zip (kept)
  forge-1.21.1-52.1.16-universal.jar           Forge 1.21.1-52.1.16 (SRG names)
  mc-deps/                                     vanilla 1.21.1 libraries (maven layout)
  forge-deps/                                  Forge userdev libraries (maven layout)
  intermediary-1.21.1.jar / .tiny             official -> intermediary (Fabric)
  fabric-loader-0.16.14.jar
  fabric-api-0.116.17+1.21.1.jar
  SpecialSource-1.11.6.jar + deps              official -> SRG remapper
  tiny-remapper-0.14.1.jar + deps              official -> intermediary remapper

Pinned versions (resolved 2026-09-17):
  FORGE=1.21.1-52.1.16  LOADER=0.16.14  FAPI=0.116.17+1.21.1
  SS=1.11.6  TR=0.14.1  MCP_CFG=1.21.1-20240808.132146
"""
import json
import os
import sys
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = os.path.join(HERE, "..", "libs")
MC_DEPS = os.path.join(LIBS, "mc-deps")
FORGE_DEPS = os.path.join(LIBS, "forge-deps")
UA = {"User-Agent": "marketblocks-build/1.0"}

FORGE_VER = "1.21.1-52.1.16"
LOADER_VER = "0.16.14"
FAPI_VER = "0.116.17+1.21.1"
SS_VER = "1.11.6"
TR_VER = "0.14.1"
MCP_CFG = "1.21.1-20240808.132146"
FORGE_MAVEN = "https://maven.minecraftforge.net"
FABRIC_MAVEN = "https://maven.fabricmc.net"
CENTRAL = "https://repo1.maven.org/maven2"


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


def dep_dest(root, group, artifact, version):
    g = group.replace(".", "/")
    return os.path.join(root, g, artifact, version, f"{artifact}-{version}.jar")


def fetch_dep(root, group, artifact, version, bases=(FORGE_MAVEN, CENTRAL)):
    dest = dep_dest(root, group, artifact, version)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    g = group.replace(".", "/")
    rel = f"{g}/{artifact}/{version}/{artifact}-{version}.jar"
    last_err = None
    for base in bases:
        try:
            download(f"{base}/{rel}", dest)
            return True
        except Exception as e:
            last_err = e
    print(f"  !! FAILED {group}:{artifact}:{version}: {last_err}")
    return False


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
    print("== Resolving 1.21.1 via piston-meta ==")
    manifest = get_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    ver_url = next(v["url"] for v in manifest["versions"] if v["id"] == "1.21.1")
    ver = get_json(ver_url)
    print("  version json:", ver_url)

    print("== Mojang jars + mappings ==")
    for side in ("client", "server"):
        download(ver["downloads"][side]["url"],
                 os.path.join(LIBS, f"minecraft-1.21.1-{side}.jar"))
    download(ver["downloads"]["client_mappings"]["url"],
             os.path.join(LIBS, "client_mappings.txt"))

    # If the server download is a bundle, extract the real obfuscated server jar.
    srv = os.path.join(LIBS, "minecraft-1.21.1-server.jar")
    inner = os.path.join(LIBS, "minecraft-1.21.1-server-inner.jar")
    with zipfile.ZipFile(srv) as z:
        names = z.namelist()
        bundled = [n for n in names if n.startswith("META-INF/versions/") and n.endswith(".jar")]
        if bundled and not (os.path.exists(inner) and os.path.getsize(inner) > 0):
            print(f"  server is a bundle; extracting {bundled[0]}")
            with z.open(bundled[0]) as src, open(inner, "wb") as f:
                f.write(src.read())
        elif bundled:
            print("  skip (exists): minecraft-1.21.1-server-inner.jar")
        else:
            print("  server jar is NOT a bundle (raw classes)")

    print("== Vanilla 1.21.1 libraries -> mc-deps/ ==")
    n = 0
    for lib in ver["libraries"]:
        if not rules_allow(lib):
            continue
        art = lib["downloads"]["artifact"]
        dest = os.path.join(MC_DEPS, art["path"])
        if download(art["url"], dest):
            n += 1
    print(f"  mc-deps new downloads: {n}")

    print("== Forge universal ==")
    download(f"{FORGE_MAVEN}/net/minecraftforge/forge/{FORGE_VER}/forge-{FORGE_VER}-universal.jar",
             os.path.join(LIBS, f"forge-{FORGE_VER}-universal.jar"))

    print("== MCPConfig (joined.tsrg) ==")
    zname = f"mcp_config-{MCP_CFG}.zip"
    download(f"{FORGE_MAVEN}/de/oceanlabs/mcp/mcp_config/{MCP_CFG}/{zname}",
             os.path.join(LIBS, zname))
    with zipfile.ZipFile(os.path.join(LIBS, zname)) as z:
        dest = os.path.join(LIBS, "joined-1.21.1.tsrg")
        if not os.path.exists(dest):
            print("  extracting config/joined.tsrg")
            with z.open("config/joined.tsrg") as src, open(dest, "wb") as f:
                f.write(src.read())
        else:
            print("  skip (exists): joined-1.21.1.tsrg")

    print("== Forge userdev libraries -> forge-deps/ ==")
    forge_libs = [
        # from forge-1.21.1-52.1.16-userdev.jar config.json "libraries"
        ("net.minecraftforge", "bootstrap", "2.1.8"),
        ("net.minecraftforge", "bootstrap-api", "2.1.8"),
        ("net.minecraftforge", "accesstransformers", "8.2.2"),
        ("net.minecraftforge", "eventbus", "6.2.33"),
        ("net.jodah", "typetools", "0.6.3"),
        ("net.minecraftforge", "forgespi", "7.1.5"),
        ("net.minecraftforge", "coremods", "5.2.6"),
        ("org.openjdk.nashorn", "nashorn-core", "15.4"),
        ("net.minecraftforge", "modlauncher", "10.2.4"),
        ("net.minecraftforge", "mergetool-api", "1.0"),
        ("com.electronwill.night-config", "toml", "3.7.4"),
        ("com.electronwill.night-config", "core", "3.7.4"),
        ("org.apache.maven", "maven-artifact", "3.8.8"),
        ("net.minecrell", "terminalconsoleappender", "1.2.0"),
        ("org.jline", "jline-reader", "3.25.1"),
        ("org.jline", "jline-terminal", "3.25.1"),
        ("org.jline", "jline-terminal-jna", "3.25.1"),
        ("org.spongepowered", "mixin", "0.8.7"),
        ("net.minecraftforge", "JarJarFileSystems", "0.3.26"),
        ("net.minecraftforge", "JarJarSelector", "0.3.26"),
        ("net.minecraftforge", "JarJarMetadata", "0.3.26"),
        ("net.minecraftforge", "fmlcore", "1.21.1-52.1.16"),
        ("net.minecraftforge", "fmlloader", "1.21.1-52.1.16"),
        ("net.minecraftforge", "fmlearlydisplay", "1.21.1-52.1.16"),
        ("net.minecraftforge", "javafmllanguage", "1.21.1-52.1.16"),
        ("net.minecraftforge", "lowcodelanguage", "1.21.1-52.1.16"),
        ("net.minecraftforge", "mclanguage", "1.21.1-52.1.16"),
        ("com.google.guava", "guava", "32.1.2-jre"),
        ("com.google.guava", "failureaccess", "1.0.1"),
        ("net.minecraftforge", "securemodules", "2.2.24"),
        ("net.minecraftforge", "unsafe", "0.9.2"),
        ("org.ow2.asm", "asm", "9.9.1"),
        ("org.ow2.asm", "asm-tree", "9.9.1"),
        ("org.ow2.asm", "asm-util", "9.9.1"),
        ("org.ow2.asm", "asm-commons", "9.9.1"),
        ("org.ow2.asm", "asm-analysis", "9.9.1"),
    ]
    failed = []
    for g, a, v in forge_libs:
        if not fetch_dep(FORGE_DEPS, g, a, v):
            failed.append(f"{g}:{a}:{v}")
    if failed:
        print("  FAILED:", failed)

    print("== Fabric: intermediary + loader + api ==")
    ij = os.path.join(LIBS, "intermediary-1.21.1.jar")
    download(f"{FABRIC_MAVEN}/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar", ij)
    tiny = os.path.join(LIBS, "intermediary-1.21.1.tiny")
    with zipfile.ZipFile(ij) as z:
        if "mappings/mappings.tiny" in z.namelist() and not os.path.exists(tiny):
            print("  extracting mappings/mappings.tiny")
            with z.open("mappings/mappings.tiny") as src, open(tiny, "wb") as f:
                f.write(src.read())
    download(f"{FABRIC_MAVEN}/net/fabricmc/fabric-loader/{LOADER_VER}/fabric-loader-{LOADER_VER}.jar",
             os.path.join(LIBS, f"fabric-loader-{LOADER_VER}.jar"))
    download(f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/{FAPI_VER}/fabric-api-{FAPI_VER}.jar",
             os.path.join(LIBS, f"fabric-api-{FAPI_VER}.jar"))

    print("== Remap tools ==")
    # SpecialSource + runtime deps (from its pom: asm-commons 9.9.1, jopt-simple
    # 5.0.4, guava 33.5.0-jre, opencsv 5.12.0)
    download(f"{CENTRAL}/net/md-5/SpecialSource/{SS_VER}/SpecialSource-{SS_VER}.jar",
             os.path.join(LIBS, f"SpecialSource-{SS_VER}.jar"))
    for g, a, v in [
        ("org.ow2.asm", "asm", "9.9.1"),
        ("org.ow2.asm", "asm-tree", "9.9.1"),
        ("org.ow2.asm", "asm-commons", "9.9.1"),
        ("net.sf.jopt-simple", "jopt-simple", "5.0.4"),
        ("com.google.guava", "guava", "33.5.0-jre"),
        ("com.google.guava", "failureaccess", "1.0.3"),
        ("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
        ("com.opencsv", "opencsv", "5.12.0"),
        ("org.apache.commons", "commons-lang3", "3.14.0"),
    ]:
        fetch_dep(LIBS, g, a, v, bases=(CENTRAL,))
    # tiny-remapper + deps (asm*, mapping-io 0.7.1)
    download(f"{FABRIC_MAVEN}/net/fabricmc/tiny-remapper/{TR_VER}/tiny-remapper-{TR_VER}.jar",
             os.path.join(LIBS, f"tiny-remapper-{TR_VER}.jar"))
    for g, a, v in [
        ("net.fabricmc", "mapping-io", "0.7.1"),
        ("com.google.code.gson", "gson", "2.10.1"),
    ]:
        fetch_dep(LIBS, g, a, v, bases=(FABRIC_MAVEN, CENTRAL))

    print("== All artifacts ready ==")


if __name__ == "__main__":
    sys.exit(main())
