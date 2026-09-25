# MC 1.21.1 manual-javac toolchain (Forge + Fabric)

Everything needed to compile Minecraft 1.21.1 mods with plain `javac`
(no Gradle). Built 2026-09-17.

## Exact versions

| Component | Version |
|---|---|
| Minecraft | 1.21.1 (Mojang mappings `client_mappings.txt`, 2024-08-08 build) |
| Java | 21 (`javac --release 21`) |
| Forge (MDK-less universal) | **1.21.1-52.1.16** |
| MCPConfig (joined TSRG) | 1.21.1-20240808.132146 (`joined-1.21.1.tsrg`) |
| Fabric Loader (requested 0.16.x line) | **0.16.14** |
| Fabric API | **0.116.17+1.21.1** |
| Intermediary | 1.21.1 (2024-08-08) |
| SpecialSource | 1.11.6 (Forge SRG remap only) |
| tiny-remapper | 0.14.1 (Fabric remap only) |

## The one thing that will bite you

Fabric's `intermediary-1.21.1.tiny` header says its namespaces are
`official intermediary`, **but the first column is really the obfuscated
(production) names** — `dfy -> net/minecraft/class_2248` (Block). Verified
against the yarn 1.21.1 tiny, which shows the same layout. Every Fabric
remap below accounts for this: "official" in a Fabric mapping file means obf.

## Namespaces

- **Mojang official** (`net/minecraft/world/level/block/Block`): what you
  write mods in, what both loaders run.
- **Obfuscated** (`dfy`): production jar names. Never in mod code.
- **Intermediary** (`net/minecraft/class_2248`): Fabric runtime names.
  Only the game+loader see these; your shipped Fabric jar must be remapped
  official -> obf -> intermediary (two steps, see below).
- **MCPConfig SRG** (`net/minecraft/src/C_1706_`): diagnostic/historical.
  **Forge 1.21.1 does NOT run SRG** — its universal jar references official
  Mojang names throughout (494 classes checked, 0 SRG refs), and its
  `ModFileParser` requires `META-INF/mods.toml` (mcmod.info is dead).

## Layout

```
libs/
  minecraft-1.21.1-client-official.jar   # your compile target (obf->official via client_mappings)
  minecraft-1.21.1-server-inner.jar      # extracted from the bundled server jar
  mc-deps/                               # 56 Linux-applicable Mojang libraries
  forge-1.21.1-52.1.16-universal.jar
  forge-deps/                            # 36 Forge userdev libraries
  fabric-loader-0.16.14.jar
  fabric-api-0.116.17+1.21.1.jar         # as published (intermediary, Jar-in-Jar)
  fabric-api-0.116.17+1.21.1-official.jar# COMPILE ONLY: flat, official-named (never ship)
  intermediary-1.21.1.jar                # mappings artifact ONLY (not a game jar)
  minecraft-1.21.1-client-intermediary.jar # game remapped obf->intermediary (remap classpath)
  intermediary-1.21.1.tiny               # NOTE: col1 = obfuscated, not official
  official_to_obf.tiny / obf_to_official.tiny   # generated from client_mappings.txt
  obf_to_official.srg                    # SpecialSource format, from client_mappings
  official_to_srg.srg                    # official -> MCPConfig SRG (diagnostic only)
  mc-classpath-linux.txt / forge-compile-classpath.txt / fabric-compile-classpath.txt
  artifact-manifest.txt                  # SHA-256 of every artifact (absolute paths)
  forge-dependencies.txt                 # 36 Forge userdev coordinates
  minecraft-1.21.1-version.json          # piston-meta version JSON
  SpecialSource-1.11.6.jar / tiny-remapper-0.14.1.jar  # remappers + their deps
  tools/                                 # copies of the scripts below (deliverables)
tools/
  fetch_1211.py        # re-download everything (pins versions above)
  proguard2srg.py      # client_mappings.txt -> obf_to_official.srg
  gen_official_srg.py  # obf_to_official.srg + joined TSRG -> official_to_srg.srg
  gen_obf_tiny.py      # client_mappings.txt -> official_to_obf.tiny + obf_to_official.tiny
  remap_client.sh      # rebuild minecraft-1.21.1-client-official.jar
  remap_fabric_api.sh  # rebuild fabric-api-*-official.jar (two-step, per-module)
  remap_mod_fabric.sh  # remap a mod jar: official -> obf -> intermediary
```

## Forge mod: compile (no remap needed)

```bash
JAVAC=$HOME/.jdks/jdk-25.0.4.1+1/bin/javac
CP="libs/minecraft-1.21.1-client-official.jar:libs/forge-1.21.1-52.1.16-universal.jar:$(ls libs/mc-deps/*.jar libs/forge-deps/*.jar | tr '\n' ':')"
$JAVAC --release 21 -cp "$CP" -d out $(find src -name '*.java')
# resources: META-INF/mods.toml  (NOT mcmod.info)
```

Ship the classes as-is (official names). There is no reobfuscation step for
Forge 1.21.1.

## Fabric mod: compile, then remap official -> intermediary

```bash
JAVAC=$HOME/.jdks/jdk-25.0.4.1+1/bin/javac
CP="libs/minecraft-1.21.1-client-official.jar:libs/fabric-api-0.116.17+1.21.1-official.jar:libs/fabric-loader-0.16.14.jar:$(ls libs/mc-deps/*.jar | tr '\n' ':')"
$JAVAC --release 21 -cp "$CP" -d out-official $(find src -name '*.java')
$HOME/.jdks/jdk-25.0.4.1+1/bin/jar --create --file mod-official.jar -C out-official .
# reobf, two steps (col1 of the intermediary file is obfuscated).
# tiny-remapper only remaps members whose owner class is visible, so the
# Minecraft jar is passed as classpath each step (official names step 1,
# obfuscated names step 2) -- without this, member refs silently stay put.
TR="libs/tiny-remapper-0.14.1.jar:libs/net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar:..."
JAVA=$HOME/.jdks/jdk-25.0.4.1+1/bin/java
$JAVA -cp "$TR" net.fabricmc.tinyremapper.Main mod-official.jar mod-obf.jar \
  libs/official_to_obf.tiny official obf \
  libs/minecraft-1.21.1-client-official.jar
$JAVA -cp "$TR" net.fabricmc.tinyremapper.Main mod-obf.jar mod-intermediary.jar \
  libs/intermediary-1.21.1.tiny official intermediary \
  libs/minecraft-1.21.1-client.jar
```

(`tools/remap_mod_fabric.sh` does exactly this: `remap_mod_fabric.sh in.jar out.jar`.)
(`fabric.mod.json` + `fabric-api` dependency go in the shipped jar as usual.)

## Files

- `mc-classpath-linux.txt` — 56 Mojang libraries (Linux rules from the
  version JSON; the other 41 entries are other-OS natives/rules, skipped
  deliberately, mirroring launcher semantics)
- `forge-compile-classpath.txt` — client + Forge universal + mc-deps + forge-deps (94)
- `fabric-compile-classpath.txt` — client + official Fabric API + loader + mc-deps (59)
- `artifact-manifest.txt` — SHA-256 of all 113 artifacts (absolute paths)
- `forge-dependencies.txt` — 36 Forge userdev library coordinates
- `minecraft-1.21.1-version.json` — the piston-meta version JSON (97 libs)

## Verification (2026-09-17)

- `minecraft-1.21.1-client-official.jar`: 8,269 classes remapped, contains
  `net/minecraft/world/level/block/Block.class`.
- Forge universal: 494 classes reference official vanilla names, 0 reference
  `net/minecraft/src/*`; `ModFileParser` errors on missing `mods.toml`.
- Fabric API official copy: nested modules remapped per-module with the
  Minecraft jars as member-resolution classpath; spot-checked official
  `Block`/`ItemStack` refs present, 0 `class_NNNN`/obfuscated refs remain.
- Probe mods compiled with `javac --release 21`:
  - Forge probe ships as-is (official names: `Blocks.DIAMOND_BLOCK`,
    `ItemStack.getItem` — matches the Forge runtime).
  - Fabric probe reobf'd via `tools/remap_mod_fabric.sh`: `Blocks` ->
    `class_2246`, `DIAMOND_BLOCK` -> `field_10201`, `Items.DIAMOND` ->
    `field_8477`, `ItemStack.getItem` -> `class_1799.method_7909`
    (all cross-checked against `intermediary-1.21.1.tiny` and yarn).
- `official_to_srg.srg`: Block -> `C_1706_` (67 methods, 24 fields),
  ItemStack -> `C_1391_` (141 methods, 19 fields).

## Market Blocks v1.0.7 port (common1211 / fabric1211 / forge1211)

`./build.sh` compiles both loaders with plain `javac` and packages:

- `build/libs/marketblocks-1.0.7-fabric1211.jar` (remapped official -> obf -> intermediary)
- `build/libs/marketblocks-1.0.7-forge1211.jar` (official names, as Forge 1.21.1 runs)

**1.21.1 API deltas vs the 26.x (NeoForge) source it was ported from:**
- `Identifier` -> `ResourceLocation`; `ClickType` is now `ClickType` (same
  name, but the 26.x `ContainerInput` enum is gone); gray pane uses
  `Items.GRAY_STAINED_GLASS_PANE` instead of a dyed pane; permission checks
  use `hasPermissions(2)`; creative checks read the actual
  `ServerPlayer.getGameMode()`; 26.x `Properties.setId(...)` does not exist.
- Tag lookup: `BuiltInRegistries.ITEM.getTag(...)`.
- NBT persistence: `CompoundTag` + `HolderLookup.Provider` (the 26.x
  `ValueInput`/`ValueOutput` API does not exist). `MarketEconomy` is a
  `SavedData` using `SavedData.Factory` + `DimensionDataStorage.computeIfAbsent`
  with manual NBT (no `SavedDataType` registry in 1.21.1).
- 26.x `affectNeighborsAfterRemoval` recovery -> 1.21.1 `onRemove`.
- `BlockEntityType`: its `BlockEntitySupplier` is package-private in 1.21.1,
  so `BlockEntityType.Builder.of(lambda, ...)` does not compile from mod
  code. `MarketContent.createStallBlockEntityType()` subclasses
  `BlockEntityType` directly and overrides the public `create` method
  (constructor supplier arg is unused).

**Forge 1.21.1 specifics (verified against the universal jar, 2026-09-17):**
- No `DeferredRegister.Blocks`/`.Items`/`createBlocks`/`createItems` — the
  generic `DeferredRegister.create(ForgeRegistries.X, modid)` is used.
- No `ModelRegistryEvent` / `ModelLoader` — removed in 1.21.x. Item models in
  `models/item/*.json` resolve automatically (same as Fabric); nothing to
  register.
- `BlockEvent.BreakEvent` (break protection) uses `getLevel()` ->
  `LevelAccessor`, `getPlayer()`, `getPos()` from the parent `BlockEvent`.

**Corrections applied 2026-09-17** (toolchain mappings were subtly wrong):
- `libs/tools/gen_obf_tiny.py` + `proguard2srg.py` failed to convert
  unchanged dotted class names (`net.minecraft.server.MinecraftServer`) to
  slash-form, leaving ~1,100 malformed Tiny/SRG entries. Fixed, regenerated
  `obf_to_official.tiny` / `official_to_obf.tiny` / `obf_to_official.srg`, and
  rebuilt `minecraft-1.21.1-client-official.jar` (verified
  `MinecraftServer.overworld()` now has its official name).
- TODO: `gen_obf_tiny.py` still has the old `tools/../libs` relative path
  hardcoded (was temporarily patched, then restored); `libs/tools/remap_client.sh`
  has the same relative-path issue from its own working directory.
- TODO: the Fabric API official compile copy
  (`fabric-api-0.116.17+1.21.1-official.jar`) was generated with the flawed
  mappings and should be regenerated; `artifact-manifest.txt` hashes are
  stale after the rebuild.

**Asset validation (2026-09-17):** recipe, loot tables, advancement, and
blockstate/model JSONs structurally validated against the 1.21.1 schema
(`minecraft:crafting_shaped` with `category`, block loot tables,
recipe advancement); `cube_top` parent confirmed present in the 1.21.1
client jar. Full codec validation via game bootstrap is not possible in
this sandbox (bootstrap needs network for `Util.fetchChoiceType`).

Both jars are Java 21 bytecode (major 65). Real-client smoke test is still
required — see `SMOKE-TEST-CHECKLIST.md`.

## 1.21.1 Forge lesson (2026-09-17, from Mike's playtest)
- The Forge jar MUST contain a root-level `pack.mcmeta` (pack_format 34 for
  1.21.1). Without it, Forge logs "failed to load a valid ResourcePackInfo"
  at startup AND silently loads zero assets from the jar: the mod works
  (registries/code are fine) but every texture/model is missing, in
  inventory and in the world. One file fixes both symptoms.
- Fabric is lenient about the missing pack.mcmeta; Forge is not.
