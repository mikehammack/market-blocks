# Market Blocks 1.0.6 — Forge 1.12.2 port

A backport of Market Blocks 1.0.6 to Minecraft 1.12.2 / Forge 14.23.5.2860,
built without Gradle (manual `javac` toolchain). This module is fully
self-contained: `common/`, `fabric/`, `neoforge/`, and the root `build.sh`
were not touched.

- Mod ID: `marketblocks` · Version: `1.0.6`
- Build: `./build.sh` → `build/libs/marketblocks-1.0.6-forge1122.jar`
- Java 8 bytecode (`javac --release 8`), SRG (obfuscated) member names in
  the shipped jar, as a real 1.12.2 Forge mod requires.

## Feature parity with 1.0.6 (26.x)

| Feature | Status |
|---|---|
| Market Stall: ownership, 27 stock slots, per-slot prices | ✅ |
| Owner GUI (stocking + price buttons + withdraw) / buyer GUI | ✅ |
| Server-authoritative purchases (price/stock/balance re-validated per click) | ✅ |
| Earnings + withdrawal to virtual balance | ✅ |
| Offline pending stock delivery on login | ✅ |
| Safe stock/earnings recovery on break | ✅ |
| Owner-or-operator break rule | ✅ |
| Per-player stall cap (5), operator bypass | ✅ |
| Admin Market: infinite config-driven buy/sell, hot reload | ✅ |
| GUI price configuration (Creative only), max 45 visible entries | ✅ |
| Admin place/break/configure gated on **Creative game mode**, not op level | ✅ |
| Survival players get the buy/sell shop GUI | ✅ |
| Virtual UUID-keyed currency, $100 starting balance, no coin item | ✅ |
| `/balance`, `/pay <player> <amount>`, `/market reload` (op) | ✅ |
| Market Stall crafting recipe (red wool / chest / any planks) | ✅ |
| Both blocks drop themselves | ✅ |
| Creative purchases controlled by config (`allowCreativePurchases`) | ✅ |

## 1.12.2 differences from the 26.x codebase

- **No mixins.** The 26.x break-protection mixin is a Forge
  `BlockEvent.BreakEvent` handler (`event/BreakProtectionHandler.java`).
- **Registry events** for blocks/items; `GameRegistry.registerTileEntity`
  for the stall tile entity.
- **Tile entity** extends `TileEntity` with NBT persistence and
  `getUpdatePacket`/`getUpdateTag` for client sync. Its internal
  `InventoryBasic` is never exposed as a block inventory, so hoppers
  cannot touch stall stock.
- **Persistence** uses `WorldSavedData` (`marketblocks_economy`) in the
  overworld's `MapStorage` instead of the 26.x data-component/player-data
  approach.
- **Commands** extend `CommandBase`; `/market reload` requires permission
  level 2.
- **GUIs** are `Container`s rendered by the vanilla `GuiChest`
  (3-row buyer, 6-row owner/admin). Slot contents sync through vanilla
  container packets — no custom network messages.
- **Config** uses Forge `Configuration` (`config/marketblocks.cfg`) with
  the same defaults and documented semantics as 26.x.
- **True Creative check**: `player.interactionManager.getGameType() ==
  GameType.CREATIVE`, mirroring the 1.0.6 fix (op permission is not enough).
- **Assets** use 1.12.2 paths (`textures/blocks/`, `models/block|item`,
  `en_us.lang`, `mcmod.info`, `pack.mcmeta` format 3).
- **Recipe** is a `forge:ore_shaped` JSON using the `plankWood` ore dict
  name for the plank ingredient.

## OreDictionary instead of tags (important)

Minecraft 1.12.2 has no item tags. Where 26.x uses `#`-prefixed **tags** in
price keys, this port treats a `#`-prefixed key as an **OreDictionary**
name:

- Default config entry `#logWood=4` (was `#minecraft:logs` on 26.x) —
  any log registered as `logWood` trades at that price.
- Exact item IDs take precedence over OreDictionary entries; file order
  breaks ties within each group.
- This is documented in the config file's `prices` category comment and in
  the in-game help text.

## Build notes (why it looks the way it does)

1.12.2 mods must ship with SRG (obfuscated) member names, but writing
against SRG names is unmaintainable. The build therefore:

1. Downloads Forge 1.12.2 universal, the MCP config (SRG class map), the
   MCP 20180814 snapshot (SRG→readable names), Mojang jars, and
   SpecialSource.
2. Generates joined-format SRG↔MCP mappings (`tools/gen_mappings.py`).
   A source-rewriting approach was rejected: 36 MCP names map to multiple
   SRG names, which is unsafe at the source level.
3. Remaps the vanilla and Forge jars **SRG→MCP** (the Forge jar needs an
   extra obfuscated-class→SRG-class pass first, since the universal jar
   references vanilla classes by obfuscated names like `nf`).
4. Makes `Block`/`Item` extend `IForgeRegistryEntry$Impl` in a
   **compile-only** copy of the vanilla jar
   (`tools/AddForgeInterfaces.java`) — this replicates exactly what Forge's
   runtime binary patches do (see the 1.12.x `Block.java.patch`:
   `public class Block extends IForgeRegistryEntry.Impl<Block>`); the jar is
   never shipped. This step is load-bearing, not cosmetic: the erased
   runtime descriptor of `setRegistryName` is
   `(LResourceLocation;)LIForgeRegistryEntry;`. An earlier version of this
   tool instead added hand-written stub methods with a covariant
   `(LResourceLocation;)LBlock;` descriptor; javac emitted that descriptor
   into the mod (including into compiler-generated bridge methods in Block
   subclasses), and the game crashed on load with `NoSuchMethodError`
   because no such method exists at runtime. There is no correct stub
   descriptor -- only the real hierarchy compiles and runs. As
   belt-and-braces, `MarketContent.create()` also invokes
   `setRegistryName` through `IForgeRegistryEntry`-typed references.
5. Compiles the mod with `javac --release 8` against those jars.
6. Reobfuscates the mod jar **MCP→SRG** with SpecialSource's live
   inheritance lookup so overridden methods (`func_180639_a`, …) land on
   the correct SRG names. Verified: no MCP member names leak into the
   shipped jar (one false positive: Forge's own `BlockEvent.getWorld()`).

7. Asset fixes from in-client testing (Sep 17 2026): the port's
   blockstates originally used `"variants": {"": ...}`. In 1.12.2,
   `StateMapperBase` maps a property-less block to the variant string
   `"normal"` (verified in the client's bytecode: empty property string
   -> append `"normal"`), so the `""` variant never matched and both
   blocks rendered as the missing texture. Blockstates now use
   `"normal"`. (The `minecraft:block/cube_top` model parent was
   suspected but is fine -- it exists in 1.12.2 and the full model +
   texture chain validates with the game's own `ModelBlock` parser.)
   The Market Stall recipe used `minecraft:red_wool`, which does not
   exist as an item in 1.12.2 (wool colors weren't flattened until
   1.13); it now uses `{ "item": "minecraft:wool", "data": 14 }`, which
   Forge 1.12.2's `CraftingHelper` supports.

## What could NOT be verified in this environment

There is no runnable 1.12.2 client or server here, so the following are
verified by bytecode/API inspection only and need the in-client checklist
(`SMOKE-TEST-CHECKLIST.md`):

- Real-client startup (mod loading, registry events, model/texture
  rendering, recipe loading).
- GUI synchronization between server containers and the vanilla chest
  screen (slot layout, `SPacketWindowItems`, click handling).
- Multiplayer transactions and race behavior (buy vs. owner edits).
- `WorldSavedData` persistence across restarts; pending-delivery login flow.
- The Forge `Configuration` GUI/file round-trip, including the 45-entry GUI
  editing path.
- Creative-mode detection via `interactionManager` on a real player.
- OreDictionary resolution with other mods' items present.
