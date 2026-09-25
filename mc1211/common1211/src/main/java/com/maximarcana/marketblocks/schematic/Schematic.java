package com.maximarcana.marketblocks.schematic;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A parsed schematic, format-agnostic.
 *
 * Both Schematica .schematic (numeric IDs, via LegacyIdMap) and Sponge .schem
 * (named palette) are converted to BlockStates at parse time. Unmappable
 * blocks become null (air) and are counted in {@link #unknownCount}.
 */
public final class Schematic {
    public final String name;
    public final int width;
    public final int height;
    public final int length;
    /**
     * BlockState per cell, null for air/unmappable.
     * Index order: (y * length + z) * width + x.
     */
    public final BlockState[] states;
    /** Tile entity tags with relative x/y/z (stored as "x", "y", "z" ints). */
    public final List<CompoundTag> tileEntities;
    /** Cells whose block could not be resolved (skipped at paste). */
    public final int unknownCount;

    public Schematic(String name, int width, int height, int length,
            BlockState[] states, List<CompoundTag> tileEntities, int unknownCount) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.states = states;
        this.tileEntities = tileEntities;
        this.unknownCount = unknownCount;
    }

    public int volume() {
        return width * height * length;
    }

    public static int index(int x, int y, int z, int width, int length) {
        return (y * length + z) * width + x;
    }

    /** Non-air cell count. */
    public int solidCount() {
        int n = 0;
        for (BlockState s : states) {
            if (s != null && !s.isAir()) {
                n++;
            }
        }
        return n;
    }
}
