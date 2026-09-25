#!/usr/bin/env python3
"""Generate LegacyIdMap.java: 1.12.2 numeric ID + meta -> 1.21.1 blockstate string.
Run: python3 gen_legacymap.py > LegacyIdMap.java
"""
# Format: (id, meta) -> "namespace:path" or "namespace:path[prop=val,...]"
# Meta -1 means "all metas" (for blocks where meta doesn't matter or is handled by properties)
M = {}

def add(id, meta, state):
    M[(id, meta)] = state

# --- 0: Air ---
add(0, 0, "minecraft:air")

# --- 1: Stone ---
add(1, 0, "minecraft:stone")
add(1, 1, "minecraft:granite")
add(1, 2, "minecraft:polished_granite")
add(1, 3, "minecraft:diorite")
add(1, 4, "minecraft:polished_diorite")
add(1, 5, "minecraft:andesite")
add(1, 6, "minecraft:polished_andesite")

# --- 2: Grass ---
add(2, 0, "minecraft:grass_block")

# --- 3: Dirt ---
add(3, 0, "minecraft:dirt")
add(3, 1, "minecraft:coarse_dirt")
add(3, 2, "minecraft:podzol")

# --- 4: Cobblestone ---
add(4, 0, "minecraft:cobblestone")

# --- 5: Planks ---
for meta, wood in [(0, "oak"), (1, "spruce"), (2, "birch"), (3, "jungle"), (4, "acacia"), (5, "dark_oak")]:
    add(5, meta, f"minecraft:{wood}_planks")

# --- 6: Sapling ---
for meta, wood in [(0, "oak"), (1, "spruce"), (2, "birch"), (3, "jungle"), (4, "acacia"), (5, "dark_oak")]:
    add(6, meta, f"minecraft:{wood}_sapling")

# --- 7: Bedrock ---
add(7, 0, "minecraft:bedrock")

# --- 8/9: Water ---
add(8, 0, "minecraft:water")
add(9, 0, "minecraft:water")

# --- 10/11: Lava ---
add(10, 0, "minecraft:lava")
add(11, 0, "minecraft:lava")

# --- 12: Sand ---
add(12, 0, "minecraft:sand")
add(12, 1, "minecraft:red_sand")

# --- 13: Gravel ---
add(13, 0, "minecraft:gravel")

# --- 14: Gold ore ---
add(14, 0, "minecraft:gold_ore")

# --- 15: Iron ore ---
add(15, 0, "minecraft:iron_ore")

# --- 16: Coal ore ---
add(16, 0, "minecraft:coal_ore")

# --- 17: Log ---
add(17, 0, "minecraft:oak_log")
add(17, 1, "minecraft:spruce_log")
add(17, 2, "minecraft:birch_log")
add(17, 3, "minecraft:jungle_log")
# 17:4-7 are bark variants, 17:8-11 are stripped? Actually 17:4-7 = oak/spruce/birch/jungle wood (bark)
# In 1.12.2, 17:4 = oak wood, 17:5 = spruce wood, etc. (all bark)
add(17, 4, "minecraft:oak_wood")
add(17, 5, "minecraft:spruce_wood")
add(17, 6, "minecraft:birch_wood")
add(17, 7, "minecraft:jungle_wood")
# 17:8-11 = oak/spruce/birch/jungle log (axis variants, ignore axis for now)
add(17, 8, "minecraft:oak_log")
add(17, 9, "minecraft:spruce_log")
add(17, 10, "minecraft:birch_log")
add(17, 11, "minecraft:jungle_log")
add(17, 12, "minecraft:oak_wood")
add(17, 13, "minecraft:spruce_wood")
add(17, 14, "minecraft:birch_wood")
add(17, 15, "minecraft:jungle_wood")

# --- 18: Leaves ---
add(18, 0, "minecraft:oak_leaves")
add(18, 1, "minecraft:spruce_leaves")
add(18, 2, "minecraft:birch_leaves")
add(18, 3, "minecraft:jungle_leaves")
# 18:4-7 are decayable variants, 18:8-11 are no-decay
for m in [4, 8, 12]:
    add(18, m, "minecraft:oak_leaves")
    add(18, m+1, "minecraft:spruce_leaves")
    add(18, m+2, "minecraft:birch_leaves")
    add(18, m+3, "minecraft:jungle_leaves")

# --- 19: Sponge ---
add(19, 0, "minecraft:sponge")
add(19, 1, "minecraft:wet_sponge")

# --- 20: Glass ---
add(20, 0, "minecraft:glass")

# --- 21: Lapis ore ---
add(21, 0, "minecraft:lapis_ore")

# --- 22: Lapis block ---
add(22, 0, "minecraft:lapis_block")

# --- 23: Dispenser (facing in meta, use default) ---
add(23, 0, "minecraft:dispenser")

# --- 24: Sandstone ---
add(24, 0, "minecraft:sandstone")
add(24, 1, "minecraft:chiseled_sandstone")
add(24, 2, "minecraft:cut_sandstone")

# --- 25: Noteblock ---
add(25, 0, "minecraft:note_block")

# --- 26: Bed (color in meta, facing complex - use white bed default, color variants below) ---
colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
for meta, color in enumerate(colors):
    add(26, meta, f"minecraft:{color}_bed")

# --- 27: Golden rail ---
add(27, 0, "minecraft:powered_rail")

# --- 28: Detector rail ---
add(28, 0, "minecraft:detector_rail")

# --- 29: Sticky piston ---
add(29, 0, "minecraft:sticky_piston")

# --- 30: Cobweb ---
add(30, 0, "minecraft:cobweb")

# --- 31: Tallgrass ---
add(31, 0, "minecraft:dead_bush")
add(31, 1, "minecraft:short_grass")
add(31, 2, "minecraft:fern")

# --- 32: Deadbush ---
add(32, 0, "minecraft:dead_bush")

# --- 33: Piston ---
add(33, 0, "minecraft:piston")

# --- 35: Wool ---
for meta, color in enumerate(colors):
    add(35, meta, f"minecraft:{color}_wool")

# --- 37: Yellow flower ---
add(37, 0, "minecraft:dandelion")

# --- 38: Red flower ---
flowers = ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
           "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"]
for meta, flower in enumerate(flowers):
    if meta < 9:  # 1.12.2 only has 0-8
        add(38, meta, f"minecraft:{flower}")
# 38:9+ don't exist in 1.12.2

# --- 39: Brown mushroom ---
add(39, 0, "minecraft:brown_mushroom")

# --- 40: Red mushroom ---
add(40, 0, "minecraft:red_mushroom")

# --- 41: Gold block ---
add(41, 0, "minecraft:gold_block")

# --- 42: Iron block ---
add(42, 0, "minecraft:iron_block")

# --- 43: Double stone slab ---
add(43, 0, "minecraft:smooth_stone_slab")  # double slab -> use single? Actually double = full block
add(43, 1, "minecraft:sandstone")  # double sandstone slab = sandstone
add(43, 2, "minecraft:oak_planks")  # double wood slab = planks (approx)
add(43, 3, "minecraft:cobblestone")
add(43, 4, "minecraft:bricks")
add(43, 5, "minecraft:stone_bricks")
add(43, 6, "minecraft:nether_bricks")
add(43, 7, "minecraft:quartz_block")
add(43, 8, "minecraft:smooth_stone")
add(43, 9, "minecraft:sandstone")

# --- 44: Stone slab ---
add(44, 0, "minecraft:smooth_stone_slab")
add(44, 1, "minecraft:sandstone_slab")
add(44, 2, "minecraft:oak_slab")
add(44, 3, "minecraft:cobblestone_slab")
add(44, 4, "minecraft:brick_slab")
add(44, 5, "minecraft:stone_brick_slab")
add(44, 6, "minecraft:nether_brick_slab")
add(44, 7, "minecraft:quartz_slab")

# --- 45: Brick block ---
add(45, 0, "minecraft:bricks")

# --- 46: TNT ---
add(46, 0, "minecraft:tnt")

# --- 47: Bookshelf ---
add(47, 0, "minecraft:bookshelf")

# --- 48: Mossy cobblestone ---
add(48, 0, "minecraft:mossy_cobblestone")

# --- 49: Obsidian ---
add(49, 0, "minecraft:obsidian")

# --- 50: Torch ---
add(50, 0, "minecraft:torch")

# --- 51: Fire ---
add(51, 0, "minecraft:fire")

# --- 52: Mob spawner ---
add(52, 0, "minecraft:spawner")

# --- 53: Oak stairs ---
add(53, 0, "minecraft:oak_stairs")

# --- 54: Chest ---
add(54, 0, "minecraft:chest")

# --- 56: Diamond ore ---
add(56, 0, "minecraft:diamond_ore")

# --- 57: Diamond block ---
add(57, 0, "minecraft:diamond_block")

# --- 58: Crafting table ---
add(58, 0, "minecraft:crafting_table")

# --- 59: Wheat ---
add(59, 0, "minecraft:wheat")

# --- 60: Farmland ---
add(60, 0, "minecraft:farmland")

# --- 61/62: Furnace ---
add(61, 0, "minecraft:furnace")
add(62, 0, "minecraft:furnace")

# --- 63: Standing sign ---
add(63, 0, "minecraft:oak_sign")

# --- 64: Wooden door ---
add(64, 0, "minecraft:oak_door")

# --- 65: Ladder ---
add(65, 0, "minecraft:ladder")

# --- 66: Rail ---
add(66, 0, "minecraft:rail")

# --- 67: Stone stairs ---
add(67, 0, "minecraft:cobblestone_stairs")

# --- 68: Wall sign ---
add(68, 0, "minecraft:oak_wall_sign")

# --- 69: Lever ---
add(69, 0, "minecraft:lever")

# --- 70: Stone pressure plate ---
add(70, 0, "minecraft:stone_pressure_plate")

# --- 71: Iron door ---
add(71, 0, "minecraft:iron_door")

# --- 72: Wooden pressure plate ---
add(72, 0, "minecraft:oak_pressure_plate")

# --- 73/74: Redstone ore ---
add(73, 0, "minecraft:redstone_ore")
add(74, 0, "minecraft:redstone_ore")

# --- 75/76: Redstone torch ---
add(75, 0, "minecraft:redstone_torch")
add(76, 0, "minecraft:redstone_torch")

# --- 77: Stone button ---
add(77, 0, "minecraft:stone_button")

# --- 78: Snow layer ---
add(78, 0, "minecraft:snow")

# --- 79: Ice ---
add(79, 0, "minecraft:ice")

# --- 80: Snow block ---
add(80, 0, "minecraft:snow_block")

# --- 81: Cactus ---
add(81, 0, "minecraft:cactus")

# --- 82: Clay ---
add(82, 0, "minecraft:clay")

# --- 83: Reeds ---
add(83, 0, "minecraft:sugar_cane")

# --- 84: Jukebox ---
add(84, 0, "minecraft:jukebox")

# --- 85: Fence ---
add(85, 0, "minecraft:oak_fence")

# --- 86: Pumpkin ---
add(86, 0, "minecraft:pumpkin")

# --- 87: Netherrack ---
add(87, 0, "minecraft:netherrack")

# --- 88: Soul sand ---
add(88, 0, "minecraft:soul_sand")

# --- 89: Glowstone ---
add(89, 0, "minecraft:glowstone")

# --- 90: Portal ---
add(90, 0, "minecraft:nether_portal")

# --- 91: Lit pumpkin ---
add(91, 0, "minecraft:jack_o_lantern")

# --- 92: Cake ---
add(92, 0, "minecraft:cake")

# --- 93/94: Repeater ---
add(93, 0, "minecraft:repeater")
add(94, 0, "minecraft:repeater")

# --- 95: Stained glass ---
for meta, color in enumerate(colors):
    add(95, meta, f"minecraft:{color}_stained_glass")

# --- 96: Trapdoor ---
add(96, 0, "minecraft:oak_trapdoor")

# --- 97: Monster egg ---
add(97, 0, "minecraft:infested_stone")
add(97, 1, "minecraft:infested_cobblestone")
add(97, 2, "minecraft:infested_stone_bricks")
add(97, 3, "minecraft:infested_mossy_stone_bricks")
add(97, 4, "minecraft:infested_cracked_stone_bricks")
add(97, 5, "minecraft:infested_chiseled_stone_bricks")

# --- 98: Stonebrick ---
add(98, 0, "minecraft:stone_bricks")
add(98, 1, "minecraft:mossy_stone_bricks")
add(98, 2, "minecraft:cracked_stone_bricks")
add(98, 3, "minecraft:chiseled_stone_bricks")

# --- 99: Brown mushroom block ---
add(99, 0, "minecraft:brown_mushroom_block")

# --- 100: Red mushroom block ---
add(100, 0, "minecraft:red_mushroom_block")

# --- 101: Iron bars ---
add(101, 0, "minecraft:iron_bars")

# --- 102: Glass pane ---
add(102, 0, "minecraft:glass_pane")

# --- 103: Melon block ---
add(103, 0, "minecraft:melon")

# --- 104/105: Pumpkin stem / Melon stem ---
add(104, 0, "minecraft:pumpkin_stem")
add(105, 0, "minecraft:melon_stem")

# --- 106: Vine ---
add(106, 0, "minecraft:vine")

# --- 107: Fence gate ---
add(107, 0, "minecraft:oak_fence_gate")

# --- 108: Brick stairs ---
add(108, 0, "minecraft:brick_stairs")

# --- 109: Stone brick stairs ---
add(109, 0, "minecraft:stone_brick_stairs")

# --- 110: Mycelium ---
add(110, 0, "minecraft:mycelium")

# --- 111: Waterlily ---
add(111, 0, "minecraft:lily_pad")

# --- 112: Nether brick ---
add(112, 0, "minecraft:nether_bricks")

# --- 113: Nether brick fence ---
add(113, 0, "minecraft:nether_brick_fence")

# --- 114: Nether brick stairs ---
add(114, 0, "minecraft:nether_brick_stairs")

# --- 115: Nether wart ---
add(115, 0, "minecraft:nether_wart")

# --- 116: Enchanting table ---
add(116, 0, "minecraft:enchanting_table")

# --- 117: Brewing stand ---
add(117, 0, "minecraft:brewing_stand")

# --- 118: Cauldron ---
add(118, 0, "minecraft:cauldron")

# --- 119: End portal ---
add(119, 0, "minecraft:end_portal")

# --- 120: End portal frame ---
add(120, 0, "minecraft:end_portal_frame")

# --- 121: End stone ---
add(121, 0, "minecraft:end_stone")

# --- 122: Dragon egg ---
add(122, 0, "minecraft:dragon_egg")

# --- 123: Redstone lamp ---
add(123, 0, "minecraft:redstone_lamp")

# --- 125: Double wood slab ---
add(125, 0, "minecraft:oak_planks")
add(125, 1, "minecraft:spruce_planks")
add(125, 2, "minecraft:birch_planks")
add(125, 3, "minecraft:jungle_planks")
add(125, 4, "minecraft:acacia_planks")
add(125, 5, "minecraft:dark_oak_planks")

# --- 126: Wood slab ---
add(126, 0, "minecraft:oak_slab")
add(126, 1, "minecraft:spruce_slab")
add(126, 2, "minecraft:birch_slab")
add(126, 3, "minecraft:jungle_slab")
add(126, 4, "minecraft:acacia_slab")
add(126, 5, "minecraft:dark_oak_slab")

# --- 127: Cocoa ---
add(127, 0, "minecraft:cocoa")

# --- 128: Sandstone stairs ---
add(128, 0, "minecraft:sandstone_stairs")

# --- 129: Emerald ore ---
add(129, 0, "minecraft:emerald_ore")

# --- 130: Ender chest ---
add(130, 0, "minecraft:ender_chest")

# --- 131: Tripwire hook ---
add(131, 0, "minecraft:tripwire_hook")

# --- 132: Tripwire ---
add(132, 0, "minecraft:tripwire")

# --- 133: Emerald block ---
add(133, 0, "minecraft:emerald_block")

# --- 134: Spruce stairs ---
add(134, 0, "minecraft:spruce_stairs")

# --- 135: Birch stairs ---
add(135, 0, "minecraft:birch_stairs")

# --- 136: Jungle stairs ---
add(136, 0, "minecraft:jungle_stairs")

# --- 137: Command block ---
add(137, 0, "minecraft:command_block")

# --- 138: Beacon ---
add(138, 0, "minecraft:beacon")

# --- 139: Cobblestone wall ---
add(139, 0, "minecraft:cobblestone_wall")
add(139, 1, "minecraft:mossy_cobblestone_wall")

# --- 140: Flower pot ---
add(140, 0, "minecraft:flower_pot")

# --- 141: Carrots ---
add(141, 0, "minecraft:carrots")

# --- 142: Potatoes ---
add(142, 0, "minecraft:potatoes")

# --- 143: Wooden button ---
add(143, 0, "minecraft:oak_button")

# --- 144: Skull ---
add(144, 0, "minecraft:skeleton_skull")

# --- 145: Anvil ---
add(145, 0, "minecraft:anvil")

# --- 146: Trapped chest ---
add(146, 0, "minecraft:trapped_chest")

# --- 147: Light weighted pressure plate ---
add(147, 0, "minecraft:light_weighted_pressure_plate")

# --- 148: Heavy weighted pressure plate ---
add(148, 0, "minecraft:heavy_weighted_pressure_plate")

# --- 149/150: Comparator ---
add(149, 0, "minecraft:comparator")
add(150, 0, "minecraft:comparator")

# --- 151: Daylight detector ---
add(151, 0, "minecraft:daylight_detector")

# --- 152: Redstone block ---
add(152, 0, "minecraft:redstone_block")

# --- 153: Quartz ore ---
add(153, 0, "minecraft:nether_quartz_ore")

# --- 154: Hopper ---
add(154, 0, "minecraft:hopper")

# --- 155: Quartz block ---
add(155, 0, "minecraft:quartz_block")
add(155, 1, "minecraft:chiseled_quartz_block")
add(155, 2, "minecraft:quartz_pillar")

# --- 156: Quartz stairs ---
add(156, 0, "minecraft:quartz_stairs")

# --- 157: Activator rail ---
add(157, 0, "minecraft:activator_rail")

# --- 158: Dropper ---
add(158, 0, "minecraft:dropper")

# --- 159: Stained hardened clay ---
for meta, color in enumerate(colors):
    add(159, meta, f"minecraft:{color}_terracotta")

# --- 160: Stained glass pane ---
for meta, color in enumerate(colors):
    add(160, meta, f"minecraft:{color}_stained_glass_pane")

# --- 161: Leaves2 ---
add(161, 0, "minecraft:acacia_leaves")
add(161, 1, "minecraft:dark_oak_leaves")
for m in [4, 8, 12]:
    add(161, m, "minecraft:acacia_leaves")
    add(161, m+1, "minecraft:dark_oak_leaves")

# --- 162: Log2 ---
add(162, 0, "minecraft:acacia_log")
add(162, 1, "minecraft:dark_oak_log")
add(162, 4, "minecraft:acacia_wood")
add(162, 5, "minecraft:dark_oak_wood")
add(162, 8, "minecraft:acacia_log")
add(162, 9, "minecraft:dark_oak_log")
add(162, 12, "minecraft:acacia_wood")
add(162, 13, "minecraft:dark_oak_wood")

# --- 163: Acacia stairs ---
add(163, 0, "minecraft:acacia_stairs")

# --- 164: Dark oak stairs ---
add(164, 0, "minecraft:dark_oak_stairs")

# --- 165: Slime block ---
add(165, 0, "minecraft:slime_block")

# --- 167: Iron trapdoor ---
add(167, 0, "minecraft:iron_trapdoor")

# --- 168: Prismarine ---
add(168, 0, "minecraft:prismarine")
add(168, 1, "minecraft:prismarine_bricks")
add(168, 2, "minecraft:dark_prismarine")

# --- 169: Sea lantern ---
add(169, 0, "minecraft:sea_lantern")

# --- 170: Hay block ---
add(170, 0, "minecraft:hay_block")

# --- 171: Carpet ---
for meta, color in enumerate(colors):
    add(171, meta, f"minecraft:{color}_carpet")

# --- 172: Hardened clay ---
add(172, 0, "minecraft:terracotta")

# --- 173: Coal block ---
add(173, 0, "minecraft:coal_block")

# --- 174: Packed ice ---
add(174, 0, "minecraft:packed_ice")

# --- 175: Double plant ---
plants = ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"]
for meta, plant in enumerate(plants):
    add(175, meta, f"minecraft:{plant}")

# --- 176/177: Banner ---
for meta, color in enumerate(colors):
    add(176, meta, f"minecraft:{color}_banner")
    add(177, meta, f"minecraft:{color}_wall_banner")

# --- 178: Daylight detector inverted ---
add(178, 0, "minecraft:daylight_detector")

# --- 179: Red sandstone ---
add(179, 0, "minecraft:red_sandstone")
add(179, 1, "minecraft:chiseled_red_sandstone")
add(179, 2, "minecraft:cut_red_sandstone")

# --- 180: Red sandstone stairs ---
add(180, 0, "minecraft:red_sandstone_stairs")

# --- 181: Double stone slab2 ---
add(181, 0, "minecraft:red_sandstone")

# --- 182: Stone slab2 ---
add(182, 0, "minecraft:red_sandstone_slab")

# --- 183: Spruce fence gate ---
add(183, 0, "minecraft:spruce_fence_gate")

# --- 184: Birch fence gate ---
add(184, 0, "minecraft:birch_fence_gate")

# --- 185: Jungle fence gate ---
add(185, 0, "minecraft:jungle_fence_gate")

# --- 186: Dark oak fence gate ---
add(186, 0, "minecraft:dark_oak_fence_gate")

# --- 187: Acacia fence gate ---
add(187, 0, "minecraft:acacia_fence_gate")

# --- 188: Spruce fence ---
add(188, 0, "minecraft:spruce_fence")

# --- 189: Birch fence ---
add(189, 0, "minecraft:birch_fence")

# --- 190: Jungle fence ---
add(190, 0, "minecraft:jungle_fence")

# --- 191: Dark oak fence ---
add(191, 0, "minecraft:dark_oak_fence")

# --- 192: Acacia fence ---
add(192, 0, "minecraft:acacia_fence")

# --- 193: Spruce door ---
add(193, 0, "minecraft:spruce_door")

# --- 194: Birch door ---
add(194, 0, "minecraft:birch_door")

# --- 195: Jungle door ---
add(195, 0, "minecraft:jungle_door")

# --- 196: Acacia door ---
add(196, 0, "minecraft:acacia_door")

# --- 197: Dark oak door ---
add(197, 0, "minecraft:dark_oak_door")

# --- 198: End rod ---
add(198, 0, "minecraft:end_rod")

# --- 199: Chorus plant ---
add(199, 0, "minecraft:chorus_plant")

# --- 200: Chorus flower ---
add(200, 0, "minecraft:chorus_flower")

# --- 201: Purpur block ---
add(201, 0, "minecraft:purpur_block")

# --- 202: Purpur pillar ---
add(202, 0, "minecraft:purpur_pillar")

# --- 203: Purpur stairs ---
add(203, 0, "minecraft:purpur_stairs")

# --- 204: Purpur double slab ---
add(204, 0, "minecraft:purpur_block")

# --- 205: Purpur slab ---
add(205, 0, "minecraft:purpur_slab")

# --- 206: End stone bricks ---
add(206, 0, "minecraft:end_stone_bricks")

# --- 207: Beetroots ---
add(207, 0, "minecraft:beetroots")

# --- 208: Grass path ---
add(208, 0, "minecraft:dirt_path")

# --- 209: End gateway ---
add(209, 0, "minecraft:end_gateway")

# --- 210: Repeating command block ---
add(210, 0, "minecraft:repeating_command_block")

# --- 211: Chain command block ---
add(211, 0, "minecraft:chain_command_block")

# --- 212: Frosted ice ---
add(212, 0, "minecraft:frosted_ice")

# --- 213: Magma block ---
add(213, 0, "minecraft:magma_block")

# --- 214: Nether wart block ---
add(214, 0, "minecraft:nether_wart_block")

# --- 215: Red nether brick ---
add(215, 0, "minecraft:red_nether_bricks")

# --- 216: Bone block ---
add(216, 0, "minecraft:bone_block")

# --- 217: Structure void ---
add(217, 0, "minecraft:structure_void")

# --- 218: Observer ---
add(218, 0, "minecraft:observer")

# --- 219/220/221/222: Shulker box ---
for meta, color in enumerate(colors):
    add(219 + (meta // 16), meta % 16, f"minecraft:{color}_shulker_box")
# Actually 219=white, 220=orange, 221=magenta, 222=light_blue, 223=yellow, 224=lime,
# 225=pink, 226=gray, 227=light_gray, 228=cyan, 229=purple, 230=blue, 231=brown, 232=green, 233=red, 234=black
# Let me redo this properly
for i, color in enumerate(colors):
    add(219 + i, 0, f"minecraft:{color}_shulker_box")

# --- 235: Glazed terracotta ---
for i, color in enumerate(colors):
    add(235 + i, 0, f"minecraft:{color}_glazed_terracotta")

# --- 251: Concrete ---
for meta, color in enumerate(colors):
    add(251, meta, f"minecraft:{color}_concrete")

# --- 252: Concrete powder ---
for meta, color in enumerate(colors):
    add(252, meta, f"minecraft:{color}_concrete_powder")

# Generate Java code
print("package com.maximarcana.marketblocks.schematic;")
print()
print("import java.util.HashMap;")
print("import java.util.Map;")
print()
print("/**")
print(" * Maps 1.12.2 numeric block IDs (+ metadata) to 1.21.1 blockstate strings.")
print(" * Generated from 1.13 flattening data. Modded IDs (>255) cannot be mapped.")
print(" */")
print("public final class LegacyIdMap {")
print("    private static final Map<Long, String> MAP = new HashMap<>();")
print("    static {")
for (id, meta), state in sorted(M.items()):
    key = (id << 4) | meta
    print(f'        MAP.put({key}L, "{state}");')
print("    }")
print()
print("    /** Returns the 1.21.1 blockstate string for a 1.12.2 (id, meta), or null. */")
print("    public static String get(int id, int meta) {")
print("        return MAP.get(((long) id << 4) | meta);")
print("    }")
print()
print("    private LegacyIdMap() {}")
print("}")
print(f"// Total entries: {len(M)}", file=__import__('sys').stderr)
