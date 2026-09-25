# Market Blocks

An in-game economy mod for casual Minecraft servers: player-run **Market
Stalls** plus an op-run **Admin Market**, backed by a virtual currency (no
physical coin item in v1), tag-based pricing, a hot-reloadable config, and three
commands with atomic server-side transactions.

- Mod ID: `marketblocks` · Version: `1.0.7`
- License: MIT — © 2026 Maxim Arcana
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/market-blocks

## How it works

- **Market Stall** — players stock 27 slots, set per-slot prices, and earn a
  virtual balance from buyers. Owners manage stock, prices, and withdrawals from
  an owner GUI; buyers purchase from a buyer GUI. In v1.0.6+ the stall can only
  be placed, broken, or configured in Creative mode (26.x) — op-level gating on
  1.12.2.
- **Admin Market** — an op-run infinite shop for server economies.
- **Schematic Market** (new in 1.0.7) — players browse an op-managed price list
  and pay to have structures pasted near the block. Reads `.schematic`
  (Schematica) and `.schem` (Sponge) files from a server-side folder set in
  config; ops list, reprice, and delist entries from an in-game configure mode.
- **Economy defaults** — 0% stall tax and a 60% sell ratio, so a fresh server has
  a working market out of the box. Prices can be keyed to item tags (e.g.
  `#logWood=4`, or `#minecraft:planks` on 26.x).
- All purchases are validated server-side per click (price, stock, and balance
  re-checked atomically).

## Source layout

| Tree | Targets | Build |
|---|---|---|
| `common/`, `fabric/`, `neoforge/` (root `build.sh`) | Minecraft 26.3, Fabric + NeoForge | `./build.sh [version]` |
| `mc1211/` | Minecraft 1.21.1, Fabric + Forge | `mc1211/build.sh` |
| `forge1122/` | Minecraft 1.12.2, Forge 14.23.5.2860 | `forge1122/build.sh` |

Each tree is self-contained with its own README and build script (see
`mc1211/README.md` and `forge1122/README.md`). The 1.12.2 port keeps full
feature parity with 1.0.7 using Forge events and GUIs instead of mixins.

## Building (26.3)

No Gradle required — the root project builds with a plain `javac` toolchain:

```bash
./build.sh [version]
# → build/libs/marketblocks-<version>-fabric.jar
# → build/libs/marketblocks-<version>-neoforge.jar
```

Requires Java 25. `build.sh` expects a `libs/` directory next to it containing
the compile-only toolchain jars (not committed — too large and version-specific):
the official Mojang 26.3 client jar, its `mc-deps/` libraries, Fabric
loader/API/modules, NeoForge + FancyModLoader jars, `sponge-mixin.jar`, and
compile-only API `stubs/` (never packaged).

Set up `libs/` from scratch with the checked-in toolchain scripts:

```bash
python3 tools/fetch_1263.py      # downloads the MC 26.3 client, deps, Fabric loader 0.19.5, NeoForge 26.3.0.3-beta
bash tools/build_stubs_1263.sh   # compiles the compile-only Mixin/Fabric-API/NeoForge API stubs (verifies them against the real APIs)
bash build.sh 1.0.7              # builds the Fabric + NeoForge jars
```

Note: 26.3 ships unobfuscated (official Mojang names, no intermediary published
by Fabric), so the mod compiles and ships in official names — no remapping step.
(1.21.1 is still obfuscated; its separate toolchain in `mc1211/tools/` handles
the official → obfuscated → intermediary remap.)
