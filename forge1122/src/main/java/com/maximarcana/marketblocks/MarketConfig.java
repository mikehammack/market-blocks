package com.maximarcana.marketblocks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Forge Configuration-backed config for the 1.12.2 port.
 *
 * Price entries live in a string list, one per line:
 *   "minecraft:diamond=100/60"   (buy 100, sell 60)
 *   "#logWood=4"                 (buy 4, sell derived from defaultSellRatio)
 *
 * IMPORTANT 1.12.2 DIFFERENCE: there are no item tags in 1.12.2. A key
 * starting with '#' is an OreDictionary name (e.g. "#logWood",
 * "#ingotIron", "#plankWood"), NOT a modern tag like "#minecraft:logs".
 */
public final class MarketConfig {
    private static MarketConfig INSTANCE;
    private static File configFile;
    private static File gameDir;

    public String currencyName = "Credits";
    public String currencySymbol = "$";
    public long startingBalance = 100;
    public double defaultSellRatio = 0.6;
    public double playerStallTaxPercent = 0.0;
    public int maxStallsPerPlayer = 5;
    public boolean allowCreativePurchases = false;
    public final List<PriceEntry> prices = new ArrayList<>();

    // -- Schematic shop -------------------------------------------------
    /** Server-side folder holding .schematic files. Absolute, or relative to the game dir. */
    public String schematicFolder = "config/marketblocks/schematics";
    /** Refuse to paste schematics larger than this many blocks (grief protection). */
    public int schematicMaxVolume = 32768;
    /** Horizontal distance from the Schematic Market block to each build slot. */
    public int schematicSlotRadius = 6;
    /** Price used when an operator lists a schematic from the GUI. */
    public long schematicDefaultPrice = 100;
    public final List<SchematicEntry> schematicEntries = new ArrayList<>();

    /** "LightAttackShip.schematic" -> price. */
    public static final class SchematicEntry {
        public final String file;
        public final long price;

        public SchematicEntry(String file, long price) {
            this.file = file;
            this.price = price;
        }

        @Override
        public String toString() {
            return file + "=" + price;
        }
    }

    /** "minecraft:diamond" or "#logWood" -> buy price + optional explicit sell price. */
    public static final class PriceEntry {
        public final String key;
        public final boolean isOreDict;
        public final long buy;
        public final Long sell;

        public PriceEntry(String key, boolean isOreDict, long buy, Long sell) {
            this.key = key;
            this.isOreDict = isOreDict;
            this.buy = buy;
            this.sell = sell;
        }

        @Override
        public String toString() {
            return key + "=" + buy + (sell != null ? "/" + sell : "");
        }
    }

    private MarketConfig() {
    }

    public static MarketConfig get() {
        return INSTANCE;
    }

    /** Game directory (parent of config/); used to resolve relative paths. */
    public static File gameDir() {
        return gameDir;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public static synchronized void load(File file) {
        configFile = file;
        File parent = file.getAbsoluteFile().getParentFile();
        gameDir = parent != null && parent.getName().equalsIgnoreCase("config")
            ? parent.getParentFile() : parent;
        MarketConfig parsed = parseFile(file, true);
        INSTANCE = parsed != null ? parsed : new MarketConfig();
    }

    /** Hot reload. Returns true on success; the old config is kept on failure. */
    public static synchronized boolean reload() {
        MarketConfig parsed = parseFile(configFile, false);
        if (parsed == null) {
            return false;
        }
        INSTANCE = parsed;
        return true;
    }

    private static MarketConfig parseFile(File file, boolean writeDefaults) {
        try {
            Configuration cfg = new Configuration(file);
            cfg.load();
            MarketConfig c = new MarketConfig();

            cfg.addCustomCategoryComment("currency",
                    "Currency identity: shown in GUIs and commands.");
            c.currencyName = cfg.get("currency", "currencyName", "Credits",
                    "Display name, e.g. \"Credits\", \"Coins\", \"Bucks\".").getString();
            c.currencySymbol = cfg.get("currency", "currencySymbol", "$",
                    "Compact symbol shown before amounts, e.g. \"$\".").getString();
            c.startingBalance = cfg.get("currency", "startingBalance", 100,
                    "Granted once to each player the first time they are seen.").getInt();

            cfg.addCustomCategoryComment("economy",
                    "Friendly-server defaults. Reload in-game with:  /market reload   (operators)");
            c.defaultSellRatio = cfg.get("economy", "defaultSellRatio", 0.6,
                    "Used when a price entry sets only a buy price: sell = buy * ratio. "
                            + "0.6 means an item bought for 10 sells back for 6. Range: 0.0 - 1.0.",
                    0.0, 1.0).getDouble();
            c.playerStallTaxPercent = cfg.get("economy", "playerStallTaxPercent", 0.0,
                    "Tax on player-to-player stall sales, in percent. The taxed cut simply "
                            + "vanishes (a sink). 0.0 = no tax. Range: 0.0 - 100.0.",
                    0.0, 100.0).getDouble();
            c.maxStallsPerPlayer = cfg.get("economy", "maxStallsPerPlayer", 5,
                    "Anti-spam cap: how many market stalls one player may own at once.").getInt();
            c.allowCreativePurchases = cfg.get("economy", "allowCreativePurchases", false,
                    "If false (recommended), creative-mode players cannot buy from stalls or "
                            + "the admin market. Set true to allow it.").getBoolean();

            cfg.addCustomCategoryComment("prices",
                    "Price list for the Admin Market (infinite buy/sell shop).\n"
                            + "One entry per line:  \"item-id=buy\"  or  \"item-id=buy/sell\".\n"
                            + "A key starting with '#' is an OREDICTIONARY name (1.12.2 has no item tags),\n"
                            + "e.g. \"#logWood\", \"#ingotIron\", \"#plankWood\" - NOT a modern tag like \"#minecraft:logs\".\n"
                            + "sell may be omitted to derive it from defaultSellRatio above.\n"
                            + "Operators can also edit these in-game through the Admin Market GUI.");
            String[] defaults = {
                    "minecraft:diamond=100/60",
                    "#logWood=4",
                    "minecraft:iron_ingot=20",
                    "minecraft:bread=6",
            };
            Property prop = cfg.get("prices", "entries", defaults);
            List<PriceEntry> parsed = new ArrayList<>();
            for (String line : prop.getStringList()) {
                PriceEntry e = parseEntry(line);
                if (e == null) {
                    throw new IllegalArgumentException("Bad price entry: \"" + line + "\"");
                }
                parsed.add(e);
            }
            c.prices.addAll(parsed);

            cfg.addCustomCategoryComment("schematics",
                    "Schematic shop (Schematic Market block).\n"
                            + "Drop Schematica-format .schematic files into the folder below;\n"
                            + "operators list them at a price from the block's configure GUI\n"
                            + "(or here, one per line:  \"Name.schematic=price\").\n"
                            + "Players buy a listing and the structure is pasted at one of\n"
                            + "8 build slots around the block, base sitting at the block's height.\n"
                            + "Block ids are the numeric runtime ids of THIS server (1.12.2 has no\n"
                            + "flattening): schematics paste reliably on the server that saved them.");
            c.schematicFolder = cfg.get("schematics", "folder", "config/marketblocks/schematics",
                    "Folder holding .schematic files. Absolute path, or relative to the game directory.").getString();
            c.schematicMaxVolume = cfg.get("schematics", "maxVolume", 32768,
                    "Largest schematic that may be pasted, in blocks. Grief protection.",
                    64, 16_777_216).getInt();
            c.schematicSlotRadius = cfg.get("schematics", "slotRadius", 6,
                    "Horizontal distance in blocks from the Schematic Market to each build slot.",
                    2, 64).getInt();
            c.schematicDefaultPrice = cfg.get("schematics", "defaultPrice", 100,
                    "Price used when an operator lists a schematic from the in-game GUI.").getInt();
            cfg.addCustomCategoryComment("schematic_prices",
                    "Schematic listings for the Schematic Market, one per line:\n"
                            + "  \"LightAttackShip.schematic=5000\"\n"
                            + "Only files present in the folder above can be bought; missing files\n"
                            + "are shown dimmed in the configure GUI, never deleted from this list.");
            List<SchematicEntry> schematics = new ArrayList<>();
            for (String line : cfg.get("schematic_prices", "entries", new String[0]).getStringList()) {
                SchematicEntry e = parseSchematicEntry(line);
                if (e == null) {
                    throw new IllegalArgumentException("Bad schematic entry: \"" + line + "\"");
                }
                schematics.add(e);
            }
            c.schematicEntries.addAll(schematics);

            if (cfg.hasChanged()) {
                cfg.save();
            }
            return c;
        } catch (Exception e) {
            System.err.println("[MarketBlocks] Failed to load config, keeping previous: " + e.getMessage());
            return null;
        }
    }

    private static PriceEntry parseEntry(String line) {        String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        int eq = s.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = s.substring(0, eq).trim();
        boolean isOreDict = key.startsWith("#");
        String idPart = isOreDict ? key.substring(1) : key;
        if (idPart.isEmpty()) {
            return null;
        }
        if (!isOreDict && !idPart.contains(":")) {
            idPart = "minecraft:" + idPart;
            key = idPart;
        }
        String[] parts = s.substring(eq + 1).split("/");
        if (parts.length < 1 || parts.length > 2) {
            return null;
        }
        long buy;
        Long sell = null;
        try {
            buy = Long.parseLong(parts[0].trim());
            if (parts.length == 2) {
                sell = Long.parseLong(parts[1].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (buy < 0 || (sell != null && sell < 0)) {
            return null;
        }
        // Validate the item id exists (ore dict names are free-form).
        if (!isOreDict && ForgeRegistries.ITEMS.getValue(new ResourceLocation(idPart)) == null) {
            return null;
        }
        return new PriceEntry(key, isOreDict, buy, sell);
    }

    /** "Name.schematic=5000". The file must live in the schematic folder to be buyable. */
    private static SchematicEntry parseSchematicEntry(String line) {
        String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        int eq = s.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String file = s.substring(0, eq).trim();
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..")) {
            return null;
        }
        if (!file.toLowerCase(java.util.Locale.ROOT).endsWith(".schematic")) {
            file = file + ".schematic";
        }
        long price;
        try {
            price = Long.parseLong(s.substring(eq + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (price < 0) {
            return null;
        }
        return new SchematicEntry(file, price);
    }

    // ------------------------------------------------------------------
    // Price resolution
    // ------------------------------------------------------------------

    /** Buy price for a stack's item, or -1 if not priced. Exact IDs beat ore dict; file order breaks ties. */
    public long resolveBuy(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return -1;
        }
        String idStr = id.toString();
        for (PriceEntry e : prices) {
            if (!e.isOreDict && e.key.equals(idStr)) {
                return e.buy;
            }
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        for (PriceEntry e : prices) {
            if (!e.isOreDict) {
                continue;
            }
            String oreName = e.key.substring(1);
            for (int oid : oreIds) {
                if (OreDictionary.getOreName(oid).equals(oreName)) {
                    return e.buy;
                }
            }
        }
        return -1;
    }

    /** Effective sell price, or -1 if the item cannot be sold. */
    public long resolveSell(ItemStack stack) {
        long buy = resolveBuy(stack);
        if (buy < 0) {
            return -1;
        }
        Long explicit = resolveSellExplicit(stack);
        if (explicit != null) {
            return explicit;
        }
        return Math.max(1, Math.round(buy * defaultSellRatio));
    }

    private Long resolveSellExplicit(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String idStr = id == null ? "" : id.toString();
        for (PriceEntry e : prices) {
            if (!e.isOreDict && e.key.equals(idStr)) {
                return e.sell;
            }
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        for (PriceEntry e : prices) {
            if (!e.isOreDict) {
                continue;
            }
            String oreName = e.key.substring(1);
            for (int oid : oreIds) {
                if (OreDictionary.getOreName(oid).equals(oreName)) {
                    return e.sell;
                }
            }
        }
        return null;
    }

    /** All configured entries in file order (for the admin GUI, max 45 shown). */
    public List<PriceEntry> entries() {
        return prices;
    }

    // ------------------------------------------------------------------
    // GUI-driven edits (operator, via Admin Market configure GUI)
    // ------------------------------------------------------------------

    /**
     * Set a price entry from the admin GUI and persist it. If the key already
     * exists its line is replaced; otherwise it is appended. A null buy
     * removes the entry.
     */
    public static synchronized void setPrice(String key, Long buy, Long sell) {        MarketConfig c = INSTANCE;
        if (c == null || configFile == null) {
            return;
        }
        c.prices.removeIf(e -> e.key.equals(key));
        if (buy != null) {
            boolean isOreDict = key.startsWith("#");
            c.prices.add(new PriceEntry(key, isOreDict, buy, sell));
        }
        try {
            Configuration cfg = new Configuration(configFile);
            cfg.load();
            String[] lines = new String[c.prices.size()];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = c.prices.get(i).toString();
            }
            cfg.get("prices", "entries", new String[0]).set(lines);
            cfg.save();
        } catch (Exception e) {
            System.err.println("[MarketBlocks] Failed to save config after GUI edit: " + e.getMessage());
        }
    }

    /**
     * Set a schematic listing from the GUI and persist it. If the file is
     * already listed its price is replaced; otherwise it is appended.
     * A null price removes the listing.
     */
    public static synchronized void setSchematicPrice(String file, Long price) {
        MarketConfig c = INSTANCE;
        if (c == null || configFile == null) {
            return;
        }
        c.schematicEntries.removeIf(e -> e.file.equalsIgnoreCase(file));
        if (price != null) {
            c.schematicEntries.add(new SchematicEntry(file, price));
        }
        try {
            Configuration cfg = new Configuration(configFile);
            cfg.load();
            String[] lines = new String[c.schematicEntries.size()];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = c.schematicEntries.get(i).toString();
            }
            cfg.get("schematic_prices", "entries", new String[0]).set(lines);
            cfg.save();
        } catch (Exception e) {
            System.err.println("[MarketBlocks] Failed to save config after GUI edit: " + e.getMessage());
        }
    }

    /** Live price for a listed schematic file, or null when not listed. */
    public Long schematicPrice(String file) {
        for (SchematicEntry e : schematicEntries) {
            if (e.file.equalsIgnoreCase(file)) {
                return e.price;
            }
        }
        return null;
    }
}
