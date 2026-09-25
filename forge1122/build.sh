#!/bin/bash
# Market Blocks - Forge 1.12.2 manual build (no Gradle).
#
# Usage: ./build.sh [version]   (defaults to 1.0.6)
#
# Pipeline:
#   1. Fetch toolchain artifacts (idempotent; skips what exists).
#   2. Generate SRG<->MCP mappings from the MCP config + snapshot.
#   3. Remap the vanilla + Forge jars from SRG to MCP names (compile jars).
#   4. Add Forge's IForgeRegistryEntry interface to Block/Item in a
#      COMPILE-ONLY copy of the vanilla jar (replicates Forge's runtime
#      binary patches; never shipped).
#   5. Compile the mod with javac --release 8 (Java 8 bytecode).
#   6. Reobfuscate the mod jar from MCP back to SRG names.
#
# Output: build/libs/marketblocks-1.0.6-forge1122.jar
set -euo pipefail
cd "$(dirname "$0")"

FORGE_VER="14.23.5.2860"
MOD_VER="${1:-1.0.6}"
OUT_JAR="build/libs/marketblocks-${MOD_VER}-forge1122.jar"

# Stamp the version into the in-code constant (mcmod.info is handled via a
# staged copy at packaging time, so the source tree stays clean).
sed -i "s/public static final String VERSION = \"[^\"]*\";/public static final String VERSION = \"$MOD_VER\";/" \
    "src/main/java/com/maximarcana/marketblocks/MarketBlocks.java"

# --- Java ---------------------------------------------------------------
if [ -x "$HOME/.jdks/jdk-25.0.4.1+1/bin/java" ]; then
    JB="$HOME/.jdks/jdk-25.0.4.1+1/bin"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JB="$JAVA_HOME/bin"
else
    JB="$(dirname "$(command -v java)")"
fi
JAVA="$JB/java"
JAVAC="$JB/javac"
JAR="$JB/jar"
echo "Using Java: $("$JAVA" -version 2>&1 | head -1)"

# --- 1. Dependencies ----------------------------------------------------
echo "==> Fetching dependencies (skips existing)"
python3 tools/fetch_deps.py

# --- 2. Mappings --------------------------------------------------------
echo "==> Generating SRG<->MCP mappings"
python3 tools/gen_mappings.py

SS_CP="libs/SpecialSource-1.8.5.jar:libs/jopt-simple-5.0.4.jar:libs/asm-6.2.jar:libs/asm-commons-6.2.jar:libs/asm-tree-6.2.jar:libs/guava-21.0.jar:libs/gson-2.8.0.jar"

# --- 3a. Vanilla compile jar (SRG -> MCP names) --------------------------
if [ ! -s build/minecraft-1.12.2-client-mcp.jar ]; then
    echo "==> Remapping vanilla client jar to MCP names"
    "$JAVA" -cp "$SS_CP" net.md_5.specialsource.SpecialSource \
        -i libs/minecraft-1.12.2-client-srg.jar \
        -m build/srg2mcp.srg \
        -o build/minecraft-1.12.2-client-mcp.jar \
        --kill-lvt -q
else
    echo "  skip (exists): build/minecraft-1.12.2-client-mcp.jar"
fi

# --- 3b. Forge compile jar (obf classes -> SRG classes -> MCP names) -----
if [ ! -s build/forge-1.12.2-${FORGE_VER}-mcp.jar ]; then
    echo "==> Remapping Forge universal jar to MCP names (2 passes)"
    "$JAVA" -cp "$SS_CP" net.md_5.specialsource.SpecialSource \
        -i libs/forge-1.12.2-${FORGE_VER}-universal.jar \
        -m build/obf2srg-classes.srg \
        -o build/forge-1.12.2-${FORGE_VER}-srg.jar \
        --kill-lvt -q
    "$JAVA" -cp "$SS_CP" net.md_5.specialsource.SpecialSource \
        -i build/forge-1.12.2-${FORGE_VER}-srg.jar \
        -m build/srg2mcp.srg \
        -o build/forge-1.12.2-${FORGE_VER}-mcp.jar \
        --kill-lvt -q
else
    echo "  skip (exists): build/forge-1.12.2-${FORGE_VER}-mcp.jar"
fi

# --- 4. Compile-only interface patch ------------------------------------
mkdir -p build/tools build/classes
if [ ! -s build/minecraft-1.12.2-client-mcp-patched.jar ]; then
    echo "==> Adding IForgeRegistryEntry to Block/Item (compile-only jar)"
    "$JAVAC" -cp "libs/asm-6.2.jar" -d build/tools tools/AddForgeInterfaces.java
    "$JAVA" -cp "build/tools:libs/asm-6.2.jar:build/forge-1.12.2-${FORGE_VER}-mcp.jar" \
        AddForgeInterfaces \
        build/minecraft-1.12.2-client-mcp.jar \
        build/minecraft-1.12.2-client-mcp-patched.jar
else
    echo "  skip (exists): build/minecraft-1.12.2-client-mcp-patched.jar"
fi

# --- 5. Compile the mod -------------------------------------------------
echo "==> Compiling mod sources (javac --release 8)"
CP="build/minecraft-1.12.2-client-mcp-patched.jar:build/forge-1.12.2-${FORGE_VER}-mcp.jar:libs/guava-21.0.jar:libs/gson-2.8.0.jar:libs/commons-lang3-3.5.jar:libs/jsr305-3.0.2.jar"
rm -rf build/classes
mkdir -p build/classes build/libs
# shellcheck disable=SC2046
"$JAVAC" --release 8 -nowarn -cp "$CP" -d build/classes \
    $(find src/main/java -name "*.java")
echo "  compiled $(find build/classes -name '*.class' | wc -l) classes"

# --- 6. Package + reobfuscate -------------------------------------------
echo "==> Packaging and reobfuscating (MCP -> SRG)"
rm -f build/mod-mcp.jar
# Stage resources so mcmod.info carries the requested version (first "version"
# key only; "mcversion" is untouched).
rm -rf build/stage-res && mkdir -p build/stage-res
cp -r src/main/resources/. build/stage-res/
sed -i "0,/\"version\": \"[^\"]*\"/s//\"version\": \"$MOD_VER\"/" build/stage-res/mcmod.info
"$JAR" cf build/mod-mcp.jar -C build/classes . -C build/stage-res .
FULL_CP="$SS_CP:build/minecraft-1.12.2-client-mcp-patched.jar:build/forge-1.12.2-${FORGE_VER}-mcp.jar:libs/guava-21.0.jar:libs/gson-2.8.0.jar:libs/commons-lang3-3.5.jar:libs/jsr305-3.0.2.jar"
"$JAVA" -cp "$FULL_CP" net.md_5.specialsource.SpecialSource \
    -l \
    -i build/mod-mcp.jar \
    -m build/mcp2srg.srg \
    -o "$OUT_JAR" \
    --kill-lvt -q

echo "==> Done: $OUT_JAR ($(du -h "$OUT_JAR" | cut -f1))"
"$JAR" tf "$OUT_JAR" | grep -c '\.class$' | xargs echo "  classes in jar:"
