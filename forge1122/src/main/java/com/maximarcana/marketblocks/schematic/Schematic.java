package com.maximarcana.marketblocks.schematic;

import net.minecraft.nbt.NBTTagList;

/**
 * A parsed Schematica-format schematic (.schematic, gzip NBT).
 *
 * Block ids are the numeric runtime ids of the server that saved the file
 * (1.12.2 has no flattening), so schematics are server-local: they paste
 * reliably on the server whose mods produced them. Vanilla ids are fixed
 * across worlds; modded ids are stable for a fixed mod list.
 */
public final class Schematic {
    public final String name;
    public final int width;
    public final int height;
    public final int length;
    /** Raw block ids, low byte. Index order: (y * length + z) * width + x. */
    public final byte[] blocks;
    /** Optional high nibbles for ids > 255; null when absent. */
    public final byte[] addBlocks;
    /** Per-block metadata, same index order as {@link #blocks}. */
    public final byte[] data;
    /** Tile entity tags with relative x/y/z ints. */
    public final NBTTagList tileEntities;

    public Schematic(String name, int width, int height, int length,
            byte[] blocks, byte[] addBlocks, byte[] data, NBTTagList tileEntities) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.addBlocks = addBlocks;
        this.data = data;
        this.tileEntities = tileEntities;
    }

    public int volume() {
        return width * height * length;
    }

    /** Full numeric block id at the cell index, combining the AddBlocks nibbles.
     *  Schematica convention (verified against Schematica's SchematicAlpha source):
     *  even index -> HIGH nibble, odd index -> LOW nibble of AddBlocks[index/2]. */
    public int blockId(int index) {
        int id = blocks[index] & 0xFF;
        if (addBlocks != null) {
            int shift = ((index & 1) == 0) ? 4 : 0;
            int nibble = (addBlocks[index >> 1] >> shift) & 0xF;
            id |= nibble << 8;
        }
        return id;
    }

    public int meta(int index) {
        return data[index] & 0xFF;
    }

    public static int index(int x, int y, int z, int width, int length) {
        return (y * length + z) * width + x;
    }

    /** Non-air cell count (air = id 0). */
    public int solidCount() {
        int n = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (blockId(i) != 0) {
                n++;
            }
        }
        return n;
    }
}
