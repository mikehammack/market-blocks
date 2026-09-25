#!/usr/bin/env bash
# MarketBlocks - build every release target with one command.
#
# Usage: ./build-all.sh <version>   (e.g. ./build-all.sh 1.0.7)
#
# Targets, built sequentially (oldest Minecraft first):
#   [1/3] Forge 1.12.2          (forge1122/build.sh -> 1 jar)
#   [2/3] Forge + Fabric 1.21.1 (mc1211/build.sh   -> 2 jars)
#   [3/3] Fabric + NeoForge 1.26.3 (build.sh       -> 2 jars)
#
# Output:
#   dist/<version>/marketblocks-<version>-<loader><mcver>.jar   (5 jars)
#   dist/<version>/CHANGELOG-<version>.md                      (fill-in template)
#   dist/<version>/logs/                                       (per-target build logs)
#
# CurseForge uploads stay manual: this script prepares, you post.
set -euo pipefail

VERSION="${1:-}"
if [[ ! "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]]; then
    echo "Usage: ./build-all.sh <version>   (e.g. ./build-all.sh 1.0.7)" >&2
    exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
DIST="$ROOT/dist/$VERSION"
LOGDIR="$DIST/logs"
mkdir -p "$DIST" "$LOGDIR"

# --- toolchain preflight ----------------------------------------------------
# 1.12.2 and 1.21.1 fetch their own deps inside their build scripts.
# 1.26.3 needs its libs/ assembled once per machine:
#   ./tools/fetch_1263.py && ./tools/build_stubs_1263.sh
missing_1263=0
for f in libs/minecraft-26.3-client.jar libs/sponge-mixin.jar \
         libs/fabric-api-0.160.5+26.3.jar libs/fabric-loader-0.19.5.jar \
         libs/neoforge-26.3.0.3-beta-universal.jar \
         libs/fancymodloader-loader-12.0.0.jar libs/neoforge-bus-8.0.5.jar; do
    if [[ ! -s "$ROOT/$f" ]]; then
        echo "MISSING: $f" >&2
        missing_1263=1
    fi
done
if [[ $missing_1263 -ne 0 ]]; then
    echo "" >&2
    echo "The 1.26.3 toolchain is not complete. Assemble it once per machine:" >&2
    echo "  cd $ROOT && ./tools/fetch_1263.py && ./tools/build_stubs_1263.sh" >&2
    exit 1
fi

TOTAL_START=$SECONDS
TARGET_NUM=0
TARGET_TOTAL=3

# collect <source jar relative to ROOT> <dist filename>
collect() {
    local src="$ROOT/$1" dest="$DIST/$2"
    if [[ ! -f "$src" ]]; then
        echo "ERROR: expected jar missing after build: $1" >&2
        exit 1
    fi
    cp "$src" "$dest"
    echo "  collected: $2 ($(du -h "$dest" | cut -f1))"
}

# run_target <label> <script relative to ROOT>
run_target() {
    local label="$1" script="$2"
    TARGET_NUM=$((TARGET_NUM + 1))
    local log="$LOGDIR/$(echo "$label" | tr -cs 'A-Za-z0-9' '_').log"
    echo ""
    echo "=================================================================="
    echo "[$TARGET_NUM/$TARGET_TOTAL] $label"
    echo "  script: $script $VERSION"
    echo "  log:    $log"
    echo "=================================================================="
    local start=$SECONDS
    # pipefail is set: a failing build aborts this script.
    bash "$ROOT/$script" "$VERSION" 2>&1 | tee "$log"
    echo "  target finished in $((SECONDS - start))s"
}

run_target "Forge 1.12.2" "forge1122/build.sh"
collect "forge1122/build/libs/marketblocks-${VERSION}-forge1122.jar" \
        "marketblocks-${VERSION}-forge1122.jar"

run_target "Forge and Fabric 1.21.1" "mc1211/build.sh"
collect "mc1211/build/libs/marketblocks-${VERSION}-forge1211.jar" \
        "marketblocks-${VERSION}-forge1211.jar"
collect "mc1211/build/libs/marketblocks-${VERSION}-fabric1211.jar" \
        "marketblocks-${VERSION}-fabric1211.jar"

run_target "Fabric and NeoForge 1.26.3" "build.sh"
collect "build/libs/marketblocks-${VERSION}-fabric.jar" \
        "marketblocks-${VERSION}-fabric1263.jar"
collect "build/libs/marketblocks-${VERSION}-neoforge.jar" \
        "marketblocks-${VERSION}-neoforge1263.jar"

# --- changelog template ---------------------------------------------------
{
    echo "# MarketBlocks $VERSION - $(date +%Y-%m-%d)"
    echo ""
    echo "## Files"
    echo ""
    echo "| File | Minecraft | Loader | Size |"
    echo "|------|-----------|--------|------|"
    for jar in "$DIST"/marketblocks-*.jar; do
        name="$(basename "$jar")"
        size="$(du -h "$jar" | cut -f1)"
        case "$name" in
            *-forge1122.jar)    mc="1.12.2"; loader="Forge" ;;
            *-forge1211.jar)    mc="1.21.1"; loader="Forge" ;;
            *-fabric1211.jar)   mc="1.21.1"; loader="Fabric" ;;
            *-fabric1263.jar)   mc="1.26.3"; loader="Fabric" ;;
            *-neoforge1263.jar) mc="1.26.3"; loader="NeoForge" ;;
            *)                  mc="?";      loader="?" ;;
        esac
        echo "| $name | $mc | $loader | $size |"
    done
    echo ""
    echo "## Changes"
    echo ""
    echo "- (fill in)"
    echo ""
} > "$DIST/CHANGELOG-$VERSION.md"

echo ""
echo "=================================================================="
echo "All targets built in $((SECONDS - TOTAL_START))s."
echo "Release package: dist/$VERSION/"
ls -la "$DIST" | awk '{print "  " $9 "  " $5}'
echo "Next: fill in CHANGELOG-$VERSION.md, then upload the 5 jars to CurseForge."
