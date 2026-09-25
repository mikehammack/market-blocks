package com.maximarcana.marketblocks.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pastes a parsed schematic into the world, server-side.
 *
 * Unmappable blocks were already filtered at parse time (null states).
 * Tile entities are restored after their blocks. Only loaded chunks are
 * touched, so pasting never forces chunk generation.
 */
public final class SchematicPaster {
    private SchematicPaster() {
    }

    public static final class Result {
        public int placed;
        public int skippedUnloaded;
        public int skippedUnknown;
        public int tileEntities;
    }

    /**
     * Pastes with the schematic's minimum corner at {@code minCorner}.
     * Runs on the server thread only.
     */
    public static Result paste(ServerLevel level, Schematic schematic, BlockPos minCorner) {
        Result r = new Result();
        int w = schematic.width;
        int h = schematic.height;
        int l = schematic.length;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int idx = Schematic.index(x, y, z, w, l);
                    BlockState state = schematic.states[idx];
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    BlockPos pos = minCorner.offset(x, y, z);
                    if (pos.getY() < minY || pos.getY() >= maxY || !level.isLoaded(pos)) {
                        r.skippedUnloaded++;
                        continue;
                    }
                    // setBlock with flag 2 = no neighbor updates during bulk paste,
                    // then the block entity pass handles TEs.
                    level.setBlock(pos, state, 2);
                    r.placed++;
                }
            }
        }
        // Tile entities second, so their blocks (and BEs) already exist.
        for (CompoundTag tag : schematic.tileEntities) {
            CompoundTag te = tag.copy();
            int rx = te.getInt("x");
            int ry = te.getInt("y");
            int rz = te.getInt("z");
            BlockPos pos = minCorner.offset(rx, ry, rz);
            if (pos.getY() < minY || pos.getY() >= maxY || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.hasBlockEntity()) {
                continue;
            }
            te.putInt("x", pos.getX());
            te.putInt("y", pos.getY());
            te.putInt("z", pos.getZ());
            // Sponge uses "Id" (string); vanilla uses "id". Normalize.
            if (te.contains("Id") && !te.contains("id")) {
                te.putString("id", te.getString("Id"));
            }
            try {
                BlockEntity existing = level.getBlockEntity(pos);
                if (existing != null) {
                    existing.loadWithComponents(te, level.registryAccess());
                } else {
                    BlockEntity be = BlockEntity.loadStatic(pos, state, te, level.registryAccess());
                    if (be != null) {
                        level.setBlockEntity(be);
                    }
                }
                r.tileEntities++;
            } catch (Exception e) {
                // A broken BE tag must not abort the paste.
                r.skippedUnknown++;
            }
        }
        return r;
    }
}
