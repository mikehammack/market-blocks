package com.maximarcana.marketblocks.schematic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.maximarcana.marketblocks.MarketConfig;

/**
 * Server-side schematic folder access: file scanning and a parse cache
 * keyed by path + last-modified, so GUIs can show dimensions without
 * re-reading NBT every click.
 */
public final class SchematicLibrary {
    private SchematicLibrary() {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private static final class Cached {
        final long lastModified;
        final Schematic schematic;
        final String error;

        Cached(long lastModified, Schematic schematic, String error) {
            this.lastModified = lastModified;
            this.schematic = schematic;
            this.error = error;
        }
    }

    /** Resolved schematic folder; created on first use. */
    public static File folder() {
        MarketConfig cfg = MarketConfig.get();
        String raw = cfg == null ? "config/marketblocks/schematics" : cfg.schematicFolder;
        File dir = new File(raw);
        if (!dir.isAbsolute() && MarketConfig.gameDir() != null) {
            dir = new File(MarketConfig.gameDir(), raw);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** All .schematic files in the folder, sorted by name. */
    public static List<File> scanFiles() {
        File dir = folder();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".schematic"));
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return Arrays.asList(files);
    }

    /** Parsed schematic or null; use {@link #lastError(File)} for the reason. */
    public static Schematic parse(File file) {
        String key = file.getAbsolutePath();
        long modified = file.lastModified();
        Cached cached = CACHE.get(key);
        if (cached != null && cached.lastModified == modified) {
            return cached.schematic;
        }
        StringBuilder error = new StringBuilder();
        Schematic parsed = SchematicParser.parse(file, error);
        CACHE.put(key, new Cached(modified, parsed,
            parsed == null ? error.toString() : null));
        // Bound the cache; folders are small but files may churn.
        if (CACHE.size() > 64) {
            CACHE.clear();
        }
        return parsed;
    }

    public static String lastError(File file) {
        Cached cached = CACHE.get(file.getAbsolutePath());
        return cached == null ? "not parsed" : cached.error;
    }

    public static void invalidate() {
        CACHE.clear();
    }
}
