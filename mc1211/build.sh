#!/usr/bin/env bash
# Market Blocks 1.0.6 - manual javac build for Minecraft 1.21.1.
#
# Builds two jars (Gradle is unusable in this sandbox; see ../../AGENTS.md):
#   build/libs/marketblocks-1.0.6-forge1211.jar   (standard Forge 1.21.1-52.1.16)
#   build/libs/marketblocks-1.0.6-fabric1211.jar  (Fabric Loader 0.16.14)
#
# Namespaces:
#   - Forge 1.21.1 ships Mojang-official names: compile official, ship official.
#   - Fabric needs intermediary names: compile official, then remap
#     official -> obf -> intermediary with libs/tools/remap_mod_fabric.sh.
#
# Usage: ./build.sh   (run from mc1211/)
set -euo pipefail
cd "$(dirname "$0")"

VERSION="1.0.6"
JDK="$HOME/.jdks/jdk-25.0.4.1+1/bin"
JAVAC="$JDK/javac"
JAR="$JDK/jar"

BUILD="build"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes-fabric" "$BUILD/classes-forge" "$BUILD/res-fabric" "$BUILD/res-forge" "$BUILD/libs"

FABRIC_CP="$(cat libs/fabric-compile-classpath.txt)"
FORGE_CP="$(cat libs/forge-compile-classpath.txt)"

echo "==> Compiling common + Fabric sources"
find common1211/src/main/java fabric1211/src/main/java -name "*.java" | sort > "$BUILD/fabric-sources.txt"
wc -l < "$BUILD/fabric-sources.txt" | xargs echo "    sources:"
"$JAVAC" --release 21 -nowarn -cp "$FABRIC_CP" -d "$BUILD/classes-fabric" @"$BUILD/fabric-sources.txt"

echo "==> Compiling common + Forge sources"
find common1211/src/main/java forge1211/src/main/java -name "*.java" | sort > "$BUILD/forge-sources.txt"
wc -l < "$BUILD/forge-sources.txt" | xargs echo "    sources:"
"$JAVAC" --release 21 -nowarn -cp "$FORGE_CP" -d "$BUILD/classes-forge" @"$BUILD/forge-sources.txt"

echo "==> Staging resources (with \${version} substitution)"
cp -r common1211/src/main/resources/. "$BUILD/res-fabric/"
cp -r fabric1211/src/main/resources/. "$BUILD/res-fabric/"
sed -i "s/\${version}/$VERSION/g" "$BUILD/res-fabric/fabric.mod.json"
cp -r common1211/src/main/resources/. "$BUILD/res-forge/"
cp -r forge1211/src/main/resources/. "$BUILD/res-forge/"
sed -i "s/\${version}/$VERSION/g" "$BUILD/res-forge/META-INF/mods.toml"

echo "==> Packaging Fabric jar (official names; remapped below)"
"$JAR" --create --file "$BUILD/marketblocks-$VERSION-fabric1211-official.jar" \
    -C "$BUILD/classes-fabric" . -C "$BUILD/res-fabric" .

echo "==> Remapping Fabric jar official -> obf -> intermediary"
bash libs/tools/remap_mod_fabric.sh \
    "$BUILD/marketblocks-$VERSION-fabric1211-official.jar" \
    "$BUILD/libs/marketblocks-$VERSION-fabric1211.jar"

echo "==> Packaging Forge jar (official names; Forge 1.21.1 needs no remap)"
"$JAR" --create --file "$BUILD/libs/marketblocks-$VERSION-forge1211.jar" \
    -C "$BUILD/classes-forge" . -C "$BUILD/res-forge" .

echo "==> Done"
ls -la "$BUILD/libs/"
