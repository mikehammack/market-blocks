#!/bin/bash
# Market Blocks - manual build script (no Gradle daemon required)
# Usage: ./build.sh [version]
# Produces: build/libs/marketblocks-<version>-fabric.jar
#           build/libs/marketblocks-<version>-neoforge.jar
set -euo pipefail

VERSION="${1:-1.0.0}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-25.0.4.1+1}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

if [ ! -x "$JAVAC" ]; then
    echo "ERROR: javac not found at $JAVAC" >&2
    exit 1
fi

MC_DEPS_CP="$(find "$ROOT/libs/mc-deps" -name '*.jar' ! -name '*natives*' | tr '\n' ':')"
BASE_CP="$ROOT/libs/minecraft-26.3-client.jar:$ROOT/libs/sponge-mixin.jar:${MC_DEPS_CP}"

# Stamp the in-code VERSION constant so the log line matches the built jar version.
sed -i "s/public static final String VERSION = \"[^\"]*\";/public static final String VERSION = \"$VERSION\";/" \
    "$ROOT/common/src/main/java/com/maximarcana/marketblocks/MarketBlocks.java"

echo "==> Compiling common..."
# Clean: deleted sources must not leave ghost classes in the jar.
rm -rf "$ROOT/build/common-classes" && mkdir -p "$ROOT/build/common-classes"
# shellcheck disable=SC2086
"$JAVAC" -encoding UTF-8 --release 25 -nowarn -cp "$BASE_CP" \
    -d "$ROOT/build/common-classes" \
    $(find "$ROOT/common/src/main/java" -name '*.java')

echo "==> Compiling fabric..."
rm -rf "$ROOT/build/fabric-classes" && mkdir -p "$ROOT/build/fabric-classes"
FABRIC_MODULES_CP="$(find "$ROOT/libs/fabric-modules/META-INF/jars" -name '*.jar' | tr '\n' ':')"
"$JAVAC" -encoding UTF-8 --release 25 -nowarn \
    -cp "$ROOT/build/common-classes:$ROOT/libs/fabric-loader-0.19.5.jar:$ROOT/libs/fabric-api-0.160.5+26.3.jar:${FABRIC_MODULES_CP}$BASE_CP" \
    -d "$ROOT/build/fabric-classes" \
    $(find "$ROOT/fabric/src/main/java" -name '*.java')

echo "==> Compiling neoforge..."
rm -rf "$ROOT/build/neoforge-classes" && mkdir -p "$ROOT/build/neoforge-classes"
"$JAVAC" -encoding UTF-8 --release 25 -nowarn \
    -cp "$ROOT/build/common-classes:$ROOT/libs/neoforge-26.3.0.3-beta-universal.jar:$ROOT/libs/fancymodloader-loader-12.0.0.jar:$ROOT/libs/neoforge-bus-8.0.5.jar:$ROOT/libs/stubs:$BASE_CP" \
    -d "$ROOT/build/neoforge-classes" \
    $(find "$ROOT/neoforge/src/main/java" -name '*.java')

echo "==> Packaging jars..."
mkdir -p "$ROOT/build/libs" "$ROOT/build/stage-fabric" "$ROOT/build/stage-neoforge"

# --- Fabric jar ---
rm -rf "$ROOT/build/stage-fabric" && mkdir -p "$ROOT/build/stage-fabric"
cp -r "$ROOT/build/common-classes/"* "$ROOT/build/stage-fabric/"
cp -r "$ROOT/build/fabric-classes/"* "$ROOT/build/stage-fabric/"
cp "$ROOT/LICENSE" "$ROOT/build/stage-fabric/"
cp -r "$ROOT/common/src/main/resources/assets" "$ROOT/build/stage-fabric/"
[ -d "$ROOT/common/src/main/resources/data" ] && cp -r "$ROOT/common/src/main/resources/data" "$ROOT/build/stage-fabric/"
sed "s/\${version}/$VERSION/" "$ROOT/fabric/src/main/resources/fabric.mod.json" > "$ROOT/build/stage-fabric/fabric.mod.json"
FABRIC_JAR="$ROOT/build/libs/marketblocks-${VERSION}-fabric.jar"
rm -f "$FABRIC_JAR"
"$JAR" --create --file "$FABRIC_JAR" -C "$ROOT/build/stage-fabric" .

# NOTE (2026-09-25): no remap step. Mojang ships 26.3 unobfuscated (official
# names, no client_mappings published) and Fabric publishes no intermediary
# for 26.3 (meta reports 0.0.0) -- the loader runs the game in official
# names, so the mod ships official too. (The 1.21.1 build still remaps:
# that version IS obfuscated.)

# --- NeoForge jar ---
rm -rf "$ROOT/build/stage-neoforge" && mkdir -p "$ROOT/build/stage-neoforge"
cp -r "$ROOT/build/common-classes/"* "$ROOT/build/stage-neoforge/"
cp -r "$ROOT/build/neoforge-classes/"* "$ROOT/build/stage-neoforge/"
cp "$ROOT/LICENSE" "$ROOT/build/stage-neoforge/"
cp -r "$ROOT/common/src/main/resources/assets" "$ROOT/build/stage-neoforge/"
[ -d "$ROOT/common/src/main/resources/data" ] && cp -r "$ROOT/common/src/main/resources/data" "$ROOT/build/stage-neoforge/"
mkdir -p "$ROOT/build/stage-neoforge/META-INF"
sed "s/\${version}/$VERSION/" "$ROOT/neoforge/src/main/resources/META-INF/neoforge.mods.toml" > "$ROOT/build/stage-neoforge/META-INF/neoforge.mods.toml"
NEOFORGE_JAR="$ROOT/build/libs/marketblocks-${VERSION}-neoforge.jar"
rm -f "$NEOFORGE_JAR"
"$JAR" --create --file "$NEOFORGE_JAR" -C "$ROOT/build/stage-neoforge" .

echo ""
echo "Built:"
ls -la "$FABRIC_JAR" "$NEOFORGE_JAR"
