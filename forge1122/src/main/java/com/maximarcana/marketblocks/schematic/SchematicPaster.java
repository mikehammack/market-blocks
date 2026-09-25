package com.maximarcana.marketblocks.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Pastes a parsed schematic into the world, server-side.
 *
 * Unknown block ids (mods missing or id shifts) are skipped and counted,
 * never fatal. Tile entities are restored after their blocks. Only loaded
 * chunks are touched, so pasting never forces chunk generation.
 */
public final class SchematicPaster {
    private SchematicPaster() {
    }

    public static final class Result {
        public int placed;
        public int skippedUnknown;
        public int skippedUnloaded;
        public int tileEntities;
    }

    /**
     * Pastes with the schematic's minimum corner at {@code minCorner}.
     * Runs on the server thread only.
     */
    public static Result paste(World world, Schematic schematic, BlockPos minCorner) {
        Result r = new Result();
        int w = schematic.width;
        int h = schematic.height;
        int l = schematic.length;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int idx = Schematic.index(x, y, z, w, l);
                    int id = schematic.blockId(idx);
                    if (id == 0) {
                        continue;
                    }
                    BlockPos pos = minCorner.add(x, y, z);
                    if (pos.getY() < 0 || pos.getY() > 255 || !world.isBlockLoaded(pos)) {
                        r.skippedUnloaded++;
                        continue;
                    }
                    Block block = Block.getBlockById(id);
                    if (block == null || block == Blocks.AIR) {
                        r.skippedUnknown++;
                        continue;
                    }
                    IBlockState state;
                    try {
                        state = block.getStateFromMeta(schematic.meta(idx));
                    } catch (Exception e) {
                        r.skippedUnknown++;
                        continue;
                    }
                    world.setBlockState(pos, state, 2);
                    r.placed++;
                }
            }
        }
        // Tile entities second, so their blocks (and TEs) already exist.
        for (int i = 0; i < schematic.tileEntities.tagCount(); i++) {
            NBTTagCompound tag = schematic.tileEntities.getCompoundTagAt(i).copy();
            BlockPos pos = minCorner.add(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
            if (pos.getY() < 0 || pos.getY() > 255 || !world.isBlockLoaded(pos)) {
                continue;
            }
            IBlockState state = world.getBlockState(pos);
            if (!state.getBlock().hasTileEntity()) {
                continue;
            }
            tag.setInteger("x", pos.getX());
            tag.setInteger("y", pos.getY());
            tag.setInteger("z", pos.getZ());
            try {
                TileEntity existing = world.getTileEntity(pos);
                if (existing != null) {
                    existing.readFromNBT(tag);
                } else {
                    TileEntity te = TileEntity.create(world, tag);
                    if (te != null) {
                        world.setTileEntity(pos, te);
                    }
                }
                r.tileEntities++;
            } catch (Exception e) {
                // A broken TE tag must not abort the paste.
            }
        }
        return r;
    }
}
