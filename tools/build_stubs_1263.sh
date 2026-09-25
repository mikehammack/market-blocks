#!/usr/bin/env bash
# Compile the 1.26.3 compile-only API stubs (tools/stubs_1263/src) and package
# them exactly the way the root build.sh expects:
#   libs/stubs/                        classes dir (Dist, etc.; on the NeoForge CP)
#   libs/sponge-mixin.jar              mixin annotations (on the common CP via BASE_CP)
#   libs/fabric-api-0.160.5+26.3.jar   fabric-api stubs (on the Fabric CP)
#   libs/fabric-modules/META-INF/jars/ (kept as an empty dir; build.sh globs it)
#
# These are compile-only and are never packaged into the mod jars.
#
# Usage: ./tools/build_stubs_1263.sh   (run from repo root, after tools/fetch_1263.py)
set -euo pipefail
cd "$(dirname "$0")/.."

JDK="$HOME/.jdks/jdk-25.0.4.1+1/bin"
JAVAC="$JDK/javac"
JAR="$JDK/jar"

for f in libs/minecraft-26.3-client.jar; do
    [ -s "$f" ] || { echo "Missing $f -- run ./tools/fetch_1263.py first." >&2; exit 1; }
done

CP="libs/minecraft-26.3-client.jar:$(find libs/mc-deps -name '*.jar' ! -name '*natives*' | sort | tr '\n' ':')"

echo "==> Compiling stubs"
rm -rf libs/stubs && mkdir -p libs/stubs build
find tools/stubs_1263/src -name '*.java' | sort > build/stub-sources.txt
"$JAVAC" -encoding UTF-8 --release 25 -nowarn -cp "$CP" -d libs/stubs @build/stub-sources.txt
echo "  stub classes: $(find libs/stubs -name '*.class' | wc -l)"

echo "==> Verifying stub kinds against the real APIs"
python3 tools/verify_stubs_1263.py libs/stubs

echo "==> Packaging sponge-mixin.jar (compile-only)"
rm -f libs/sponge-mixin.jar
"$JAR" --create --file libs/sponge-mixin.jar -C libs/stubs org/spongepowered

echo "==> Packaging fabric-api-0.160.5+26.3.jar (compile-only)"
rm -f libs/fabric-api-0.160.5+26.3.jar
"$JAR" --create --file libs/fabric-api-0.160.5+26.3.jar -C libs/stubs net/fabricmc

mkdir -p libs/fabric-modules/META-INF/jars

echo "==> Done. Run ./build.sh [version] to build the 1.26.3 jars."
