#!/usr/bin/env bash
# Bootstrap the MC 1.21.1 manual-javac toolchain AFTER tools/fetch_1211.py.
#
# Pipeline (all idempotent; safe to re-run):
#   1. client_mappings.txt -> obf_to_official.srg + official/obf tiny files
#   2. remap obfuscated client -> libs/minecraft-1.21.1-client-official.jar
#   3. remap obfuscated client -> libs/minecraft-1.21.1-client-intermediary.jar
#   4. stage libs/tools/ (BatchRemap + remap scripts, as build.sh expects)
#   5. remap Fabric API -> libs/fabric-api-0.116.17+1.21.1-official.jar
#   6. write libs/{fabric,forge}-compile-classpath.txt for build.sh
#
# Usage: ./tools/bootstrap_1211.sh   (run from mc1211/)
set -euo pipefail
cd "$(dirname "$0")/.."

JDK="$HOME/.jdks/jdk-25.0.4.1+1/bin"
JAVA="$JDK/java"
JAVAC="$JDK/javac"
L="libs"

echo "==> [1/6] Generating SRG + tiny mappings from client_mappings.txt"
[ -s "$L/obf_to_official.srg" ] || python3 tools/proguard2srg.py "$L/client_mappings.txt" "$L/obf_to_official.srg"
[ -s "$L/obf_to_official.tiny" ] || python3 tools/gen_obf_tiny.py

echo "==> [2/6] Remapping client obf -> official"
bash tools/remap_client.sh

echo "==> [3/6] Remapping client obf -> intermediary"
if [ ! -s "$L/minecraft-1.21.1-client-intermediary.jar" ]; then
    ( cd "$L" && "$JAVA" -cp "tiny-remapper-0.14.1.jar:net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar:org/ow2/asm/asm/9.9.1/asm-9.9.1.jar:org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar:org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar:org/ow2/asm/asm-util/9.9.1/asm-util-9.9.1.jar:com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" \
        net.fabricmc.tinyremapper.Main \
        minecraft-1.21.1-client.jar minecraft-1.21.1-client-intermediary.jar \
        intermediary-1.21.1.tiny official intermediary \
        minecraft-1.21.1-client.jar )
else
    echo "  skip (exists): $L/minecraft-1.21.1-client-intermediary.jar"
fi

echo "==> [4/6] Staging libs/tools/"
mkdir -p "$L/tools"
if [ ! -s "$L/tools/BatchRemap.class" ]; then
    "$JAVAC" -cp "$L/tiny-remapper-0.14.1.jar:$L/net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar" \
        -d "$L/tools" tools/BatchRemap.java
fi
cp -u tools/*.sh "$L/tools/"
chmod +x "$L/tools/"*.sh

echo "==> [5/6] Remapping Fabric API -> official names (compile-only)"
bash tools/remap_fabric_api.sh

echo "==> [6/6] Writing compile classpath files"
MC_DEPS_CP="$(find "$L/mc-deps" -name '*.jar' ! -name '*natives*' | sort | tr '\n' ':')"
FORGE_DEPS_CP="$(find "$L/forge-deps" -name '*.jar' ! -name '*natives*' | sort | tr '\n' ':')"
echo "$L/minecraft-1.21.1-client-official.jar:$L/fabric-api-0.116.17+1.21.1-official.jar:$L/fabric-loader-0.16.14.jar:${MC_DEPS_CP}" > "$L/fabric-compile-classpath.txt"
echo "$L/minecraft-1.21.1-client-official.jar:$L/forge-1.21.1-52.1.16-universal.jar:${MC_DEPS_CP}${FORGE_DEPS_CP}" > "$L/forge-compile-classpath.txt"
echo "  wrote $L/fabric-compile-classpath.txt"
echo "  wrote $L/forge-compile-classpath.txt"

echo "==> Toolchain bootstrap complete. Run ./build.sh [version] to build."
