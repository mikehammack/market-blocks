package com.maximarcana.marketblocks.schematic;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Parses Schematica .schematic (numeric IDs) and Sponge .schem (named palette).
 * Returns null and appends the reason to {@code error} on failure.
 */
public final class SchematicParser {
    private SchematicParser() {}

    public static Schematic parse(File file, StringBuilder error) {
        String name = file.getName();
        String lower = name.toLowerCase();
        boolean sponge = lower.endsWith(".schem");
        if (lower.endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        } else if (sponge) {
            name = name.substring(0, name.length() - ".schem".length());
        }

        CompoundTag root;
        try (InputStream in = new FileInputStream(file)) {
            root = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            error.append("Unreadable NBT: ").append(e.getMessage());
            return null;
        }
        if (root == null) {
            error.append("Empty NBT root.");
            return null;
        }

        if (sponge) {
            return parseSponge(name, root, error);
        } else {
            return parseSchematica(name, root, error);
        }
    }

    // ------------------------------------------------------------------
    // Schematica .schematic: numeric IDs + metadata, mapped via LegacyIdMap
    // ------------------------------------------------------------------

    private static Schematic parseSchematica(String name, CompoundTag root, StringBuilder error) {
        int w = root.getShort("Width");
        int h = root.getShort("Height");
        int l = root.getShort("Length");
        if (w <= 0 || h <= 0 || l <= 0 || w > 256 || h > 256 || l > 256) {
            error.append("Bad dimensions ").append(w).append('x').append(h).append('x').append(l);
            return null;
        }
        int volume = w * h * l;
        byte[] blocks = root.getByteArray("Blocks");
        byte[] data = root.getByteArray("Data");
        if (blocks.length != volume || data.length != volume) {
            error.append("Blocks/Data arrays don't match dimensions (got ")
                .append(blocks.length).append('/').append(data.length)
                .append(", expected ").append(volume).append(')');
            return null;
        }
        byte[] addBlocks = null;
        if (root.contains("AddBlocks")) {
            byte[] raw = root.getByteArray("AddBlocks");
            if (raw.length * 2 >= volume) {
                addBlocks = raw;
            }
        }

        BlockState[] states = new BlockState[volume];
        int unknown = 0;
        for (int i = 0; i < volume; i++) {
            int id = blocks[i] & 0xFF;
            if (addBlocks != null) {
                // Schematica convention: even index -> HIGH nibble, odd -> LOW.
                int shift = ((i & 1) == 0) ? 4 : 0;
                int nibble = (addBlocks[i >> 1] >> shift) & 0xF;
                id |= nibble << 8;
            }
            if (id == 0) {
                continue; // air
            }
            int meta = data[i] & 0xFF;
            BlockState state = legacyState(id, meta);
            if (state == null || state.isAir()) {
                unknown++;
            } else {
                states[i] = state;
            }
        }

        List<CompoundTag> tes = readTileEntities(root);
        return new Schematic(name, w, h, l, states, tes, unknown);
    }

    /** 1.12.2 numeric (id, meta) -> 1.21.1 BlockState via the flattening map. */
    private static BlockState legacyState(int id, int meta) {
        if (id > 255) {
            return null; // modded IDs are meaningless across versions
        }
        String stateStr = LegacyIdMap.get(id, meta);
        if (stateStr == null) {
            // Try meta 0 as fallback for blocks where we only mapped the base.
            stateStr = LegacyIdMap.get(id, 0);
        }
        if (stateStr == null) {
            return null;
        }
        return parseStateString(stateStr);
    }

    // ------------------------------------------------------------------
    // Sponge .schem v2/v3: named palette + varint block data
    // ------------------------------------------------------------------

    private static Schematic parseSponge(String name, CompoundTag root, StringBuilder error) {
        int version = root.getInt("Version");
        if (version != 2 && version != 3) {
            error.append("Unsupported .schem version ").append(version).append(" (need 2 or 3)");
            return null;
        }
        int w = root.getShort("Width");
        int h = root.getShort("Height");
        int l = root.getShort("Length");
        if (w <= 0 || h <= 0 || l <= 0 || w > 512 || h > 512 || l > 512) {
            error.append("Bad dimensions ").append(w).append('x').append(h).append('x').append(l);
            return null;
        }
        int volume = w * h * l;

        // Palette: { "minecraft:stone": 0, ... } -> invert to id -> state string
        CompoundTag paletteTag = root.getCompound("Palette");
        String[] palette = new String[root.getInt("PaletteMax")];
        for (String key : paletteTag.getAllKeys()) {
            int pid = paletteTag.getInt(key);
            if (pid >= 0 && pid < palette.length) {
                palette[pid] = key;
            }
        }

        // BlockData: varint array, palette indices in (y * length + z) * width + x order
        byte[] blockData = root.getByteArray("BlockData");
        BlockState[] states = new BlockState[volume];
        int unknown = 0;
        int pos = 0;
        int idx = 0;
        while (idx < volume && pos < blockData.length) {
            int pid = 0;
            int shift = 0;
            while (true) {
                int b = blockData[pos++] & 0xFF;
                pid |= (b & 0x7F) << shift;
                shift += 7;
                if ((b & 0x80) == 0) break;
                if (pos >= blockData.length) break;
            }
            BlockState state = null;
            if (pid >= 0 && pid < palette.length && palette[pid] != null) {
                state = parseStateString(palette[pid]);
            }
            if (state == null || state.isAir()) {
                if (pid != 0 || !"minecraft:air".equals(palette[0])) {
                    // Only count as unknown if it wasn't air
                    String ps = (pid >= 0 && pid < palette.length) ? palette[pid] : null;
                    if (ps != null && !"minecraft:air".equals(ps) && !"minecraft:cave_air".equals(ps)
                            && !"minecraft:void_air".equals(ps)) {
                        unknown++;
                    }
                }
            } else {
                states[idx] = state;
            }
            idx++;
        }

        // TileEntities: list with "Pos" int[3] (absolute in schematic space) and "Id".
        List<CompoundTag> tes = new ArrayList<>();
        if (root.contains("TileEntities")) {
            ListTag teList = root.getList("TileEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < teList.size(); i++) {
                CompoundTag te = teList.getCompound(i).copy();
                // Normalize position keys to x/y/z for the paster.
                if (te.contains("Pos")) {
                    int[] p = te.getIntArray("Pos");
                    if (p.length >= 3) {
                        te.putInt("x", p[0]);
                        te.putInt("y", p[1]);
                        te.putInt("z", p[2]);
                    }
                }
                tes.add(te);
            }
        }

        return new Schematic(name, w, h, l, states, tes, unknown);
    }

    private static List<CompoundTag> readTileEntities(CompoundTag root) {
        List<CompoundTag> out = new ArrayList<>();
        if (!root.contains("TileEntities")) {
            return out;
        }
        ListTag teList = root.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < teList.size(); i++) {
            out.add(teList.getCompound(i).copy());
        }
        return out;
    }

    /** Parse "minecraft:stone" or "minecraft:oak_stairs[facing=north,half=bottom]". */
    static BlockState parseStateString(String s) {
        String blockId = s;
        String props = null;
        int lb = s.indexOf('[');
        if (lb >= 0) {
            blockId = s.substring(0, lb);
            int rb = s.indexOf(']', lb);
            props = s.substring(lb + 1, rb >= 0 ? rb : s.length());
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
            return null; // unknown block (missing mod?)
        }
        BlockState state = block.defaultBlockState();
        if (props != null) {
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                if (eq < 0) continue;
                String k = kv.substring(0, eq).trim();
                String v = kv.substring(eq + 1).trim();
                var prop = block.getStateDefinition().getProperty(k);
                if (prop != null) {
                    state = setProp(state, prop, v);
                    if (state == null) return block.defaultBlockState();
                }
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setProp(BlockState state, net.minecraft.world.level.block.state.properties.Property prop, String val) {
        try {
            Comparable c = (Comparable) prop.getValue(val).orElse(null);
            if (c == null) return null;
            return state.setValue(prop, c);
        } catch (Exception e) {
            return null;
        }
    }
}
