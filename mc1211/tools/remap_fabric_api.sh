#!/usr/bin/env bash
# Build a COMPILE-ONLY official-named copy of Fabric API 0.116.17+1.21.1.
# The published fabric-api jar is Jar-in-Jar (50 nested module jars under
# META-INF/jars/, all intermediary-named); tiny-remapper does not recurse
# into nested jars, so each module jar is remapped individually and the
# results are merged into one flat compile-only jar.
#
# Fabric's intermediary-1.21.1.tiny labels its first column "official" but it
# is really the obfuscated namespace, so each module goes through two steps:
#   1. intermediary -> obf   (intermediary-1.21.1.tiny, from=intermediary, to=official)
#   2. obf -> official      (obf_to_official.tiny,     from=obf,          to=official)
# Step 1 is exactly the operation the Fabric loader performs on the game jar,
# so unmapped names pass through identically in both.
#
# tiny-remapper only remaps members whose owner class it can see, so the
# Minecraft jars are passed as classpath (intermediary-named client for
# step 1, obfuscated client for step 2). Note: intermediary-1.21.1.jar is
# only the mappings artifact, NOT a game jar -- the intermediary-named game
# jar is built once via:
#   tinyremapper.Main minecraft-1.21.1-client.jar \
#     minecraft-1.21.1-client-intermediary.jar \
#     intermediary-1.21.1.tiny official intermediary
#
# BatchRemap (tools/BatchRemap.java) runs all modules in one JVM per step
# instead of one JVM per module (100 launches -> 2).
#
# Usage: remap_fabric_api.sh
# Produces: libs/fabric-api-0.116.17+1.21.1-official.jar  (never shipped)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Works from mc1211/tools/ (.. = mc1211) and from mc1211/libs/tools/ (.. = libs)
if [ -d "$HERE/../libs" ]; then LIBS="$HERE/../libs"; else LIBS="$(dirname "$HERE")"; fi
OUT="$LIBS/fabric-api-0.116.17+1.21.1-official.jar"
JAVA="$HOME/.jdks/jdk-25.0.4.1+1/bin/java"
CP="$LIBS/tools:tiny-remapper-0.14.1.jar:net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar:org/ow2/asm/asm/9.9.1/asm-9.9.1.jar:org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar:org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar:org/ow2/asm/asm-util/9.9.1/asm-util-9.9.1.jar:com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"

WORK="$(mktemp -d /tmp/fapi-remap-XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
cd "$LIBS"

unzip -o -q fabric-api-0.116.17+1.21.1.jar 'META-INF/jars/*.jar' -d "$WORK/nested"
mkdir -p "$WORK/merged"

MODULES=("$WORK"/nested/META-INF/jars/*.jar)

# Step 1: all modules intermediary -> obf, merged into one jar
"$JAVA" -cp "$CP" BatchRemap \
  intermediary-1.21.1.tiny intermediary official \
  minecraft-1.21.1-client-intermediary.jar \
  "$WORK/step1.jar" \
  "${MODULES[@]}"
# Step 2: obf -> official
"$JAVA" -cp "$CP" BatchRemap \
  obf_to_official.tiny obf official \
  minecraft-1.21.1-client.jar \
  "$WORK/step2.jar" \
  "$WORK/step1.jar"

# Unpack, drop signatures, repack as the flat compile-only jar
unzip -q "$WORK/step2.jar" -d "$WORK/merged" -x 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA'
rm -f "$OUT"
( cd "$WORK/merged" && "$HOME/.jdks/jdk-25.0.4.1+1/bin/jar" --create --file "$OUT" . )
echo "wrote $OUT ($(du -h "$OUT" | cut -f1))"
