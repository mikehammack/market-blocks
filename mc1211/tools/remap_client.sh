#!/bin/bash
# Remap the obfuscated 1.21.1 client jar to official Mojang names.
# Produces: libs/minecraft-1.21.1-client-official.jar
# (compile target for both Forge and Fabric 1.21.1 mods)
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -x "$HOME/.jdks/jdk-25.0.4.1+1/bin/java" ]; then
    JAVA="$HOME/.jdks/jdk-25.0.4.1+1/bin/java"
else
    JAVA="$(command -v java)"
fi

L=libs
SS_CP="$L/SpecialSource-1.11.6.jar"
SS_CP="$SS_CP:$L/org/ow2/asm/asm/9.9.1/asm-9.9.1.jar"
SS_CP="$SS_CP:$L/org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar"
SS_CP="$SS_CP:$L/org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar"
SS_CP="$SS_CP:$L/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar"
SS_CP="$SS_CP:$L/com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar"
SS_CP="$SS_CP:$L/com/google/guava/failureaccess/1.0.3/failureaccess-1.0.3.jar"
SS_CP="$SS_CP:$L/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
SS_CP="$SS_CP:$L/com/opencsv/opencsv/5.12.0/opencsv-5.12.0.jar"
SS_CP="$SS_CP:$L/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar"

if [ -s "$L/minecraft-1.21.1-client-official.jar" ]; then
    echo "skip (exists): $L/minecraft-1.21.1-client-official.jar"
    exit 0
fi

echo "==> Remapping client jar obf -> official (SpecialSource)"
"$JAVA" -cp "$SS_CP" net.md_5.specialsource.SpecialSource \
    -i "$L/minecraft-1.21.1-client.jar" \
    -m "$L/obf_to_official.srg" \
    -o "$L/minecraft-1.21.1-client-official.jar" \
    --kill-lvt -q
echo "==> Done: $L/minecraft-1.21.1-client-official.jar ($(du -h "$L/minecraft-1.21.1-client-official.jar" | cut -f1))"
