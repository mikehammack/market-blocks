package com.maximarcana.marketblocks.schematic;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Parses Schematica-format .schematic files (gzip NBT, numeric block ids). */
public final class SchematicParser {
    private SchematicParser() {
    }

    /** Parses the file, or returns null and appends the reason to {@code error}. */
    public static Schematic parse(File file, StringBuilder error) {
        String name = file.getName();
        if (name.toLowerCase().endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        }
        NBTTagCompound root;
        try (InputStream in = new FileInputStream(file)) {
            root = CompressedStreamTools.readCompressed(in);
        } catch (Exception e) {
            error.append("Unreadable NBT: ").append(e.getMessage());
            return null;
        }
        if (root == null) {
            error.append("Empty NBT root.");
            return null;
        }
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
        if (root.hasKey("AddBlocks")) {
            byte[] raw = root.getByteArray("AddBlocks");
            if (raw.length * 2 >= volume) {
                addBlocks = raw;
            }
        }
        NBTTagList tileEntities = root.hasKey("TileEntities")
            ? root.getTagList("TileEntities", 10)
            : new NBTTagList();
        // Entities are skipped in v1 (structure-only pasting).
        return new Schematic(name, w, h, l, blocks, addBlocks, data, tileEntities);
    }
}
