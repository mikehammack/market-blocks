#!/usr/bin/env bash
# Remap a Fabric mod jar from Mojang official names to intermediary names.
#
# Two steps (Fabric's intermediary-1.21.1.tiny labels its first column
# "official" but it is really the obfuscated namespace):
#   1. official -> obf          (official_to_obf.tiny,      official -> obf)
#   2. obf -> intermediary      (intermediary-1.21.1.tiny,   "official" -> intermediary;
#                                note the quotes: col1 is labeled "official" but holds obf names)
# Step 2 is exactly what the Fabric loader does to the game jar, so the mod
# and the game agree on every name, including members intermediary omits
# (those pass through as obfuscated names on both sides).
#
# tiny-remapper only remaps members whose owner class it can see, so the
# Minecraft jar is passed as classpath (official-named for step 1,
# obfuscated for step 2).
#
# Usage: remap_mod_fabric.sh <input-official.jar> <output-intermediary.jar>
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Works from mc1211/tools/ (.. = mc1211) and from mc1211/libs/tools/ (.. = libs)
if [ -d "$HERE/../libs" ]; then LIBS="$HERE/../libs"; else LIBS="$(dirname "$HERE")"; fi
[ $# -eq 2 ] || { echo "usage: $0 <input-official.jar> <output-intermediary.jar>" >&2; exit 1; }
IN="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
mkdir -p "$(dirname "$2")"
OUT="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
JAVA="$HOME/.jdks/jdk-25.0.4.1+1/bin/java"
CP="tiny-remapper-0.14.1.jar:net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar:org/ow2/asm/asm/9.9.1/asm-9.9.1.jar:org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar:org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar:org/ow2/asm/asm-util/9.9.1/asm-util-9.9.1.jar:com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
cd "$LIBS"
TMP="$(mktemp /tmp/mod-obf-XXXXXX.jar)"; rm -f "$TMP"
"$JAVA" -cp "$CP" net.fabricmc.tinyremapper.Main \
  "$IN" "$TMP" official_to_obf.tiny official obf \
  minecraft-1.21.1-client-official.jar
"$JAVA" -cp "$CP" net.fabricmc.tinyremapper.Main \
  "$TMP" "$OUT" intermediary-1.21.1.tiny official intermediary \
  minecraft-1.21.1-client.jar
rm -f "$TMP"
echo "wrote $OUT"
