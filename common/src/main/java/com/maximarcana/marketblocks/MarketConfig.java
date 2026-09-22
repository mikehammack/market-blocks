package com.maximarcana.marketblocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Self-documenting TOML-like config. Parsed into a temp model, fully validated,
 * and only swapped live when every entry passes. GUI edits from the admin
 * market update the file surgically (one line per price entry) so admin
 * comments and formatting are preserved.
 */
public final class MarketConfig {
    private static MarketConfig INSTANCE;
    private static Path configFile;

    public String currencyName = "Credits";
    public String currencySymbol = "$";
    public long startingBalance = 100;
    public double defaultSellRatio = 0.6;
    public double playerStallTaxPercent = 0.0;
    public int maxStallsPerPlayer = 5;
    public boolean allowCreativePurchases = false;
    public final List<PriceEntry> prices = new ArrayList<>();

    /** "minecraft:diamond" or "#minecraft:logs" -> buy price + optional explicit sell price. */
    public record PriceEntry(String key, boolean isTag, long buy, Long sell) {
    }

    private MarketConfig() {
    }

    public static MarketConfig get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public static synchronized void load(Path file) {
        configFile = file;
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultFileText(), StandardCharsets.UTF_8);
                MarketBlocks.LOGGER.info("Created default config at {}", file);
            }
            INSTANCE = parse(Files.readString(file, StandardCharsets.UTF_8), true);
            MarketBlocks.LOGGER.info("Loaded Market Blocks config ({} price entries)", INSTANCE.prices.size());
        } catch (Exception e) {
            MarketBlocks.LOGGER.error("Failed to load config, using safe defaults", e);
            INSTANCE = new MarketConfig();
        }
    }

    /** Hot reload. Returns null on success, or a human-readable error (old config kept). */
    public static synchronized String reload() {
        try {
            MarketConfig parsed = parse(Files.readString(configFile, StandardCharsets.UTF_8), true);
            INSTANCE = parsed;
            MarketBlocks.LOGGER.info("Reloaded Market Blocks config ({} price entries)", parsed.prices.size());
            return null;
        } catch (Exception e) {
            MarketBlocks.LOGGER.error("Config reload failed, keeping previous config", e);
            return e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Price resolution
    // ------------------------------------------------------------------

    /** Buy price for a stack's item, or -1 if not priced. Exact IDs beat tags; file order breaks ties. */
    public long resolveBuy(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return -1;
        }
        String idStr = id.toString();
        for (PriceEntry e : prices) {
            if (!e.isTag && e.key.equals(idStr)) {
                return e.buy;
            }
        }
        for (PriceEntry e : prices) {
            if (e.isTag && stack.is(tagKey(e.key.substring(1)))) {
                return e.buy;
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
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String idStr = id.toString();
        for (PriceEntry e : prices) {
            if (!e.isTag && e.key.equals(idStr)) {
                return e.sell;
            }
        }
        for (PriceEntry e : prices) {
            if (e.isTag && stack.is(tagKey(e.key.substring(1)))) {
                return e.sell;
            }
        }
        return null;
    }

    private static TagKey<Item> tagKey(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }

    // ------------------------------------------------------------------
    // GUI-driven edits (surgical file updates)
    // ------------------------------------------------------------------

    /** Insert or replace a price entry, both in memory and in the file. */
    public synchronized void setPrice(String key, boolean isTag, long buy, Long sell) {
        String fullKey = (isTag ? "#" : "") + key;
        prices.removeIf(e -> e.key.equals(fullKey));
        prices.add(new PriceEntry(fullKey, isTag, buy, sell));
        rewritePriceLine(fullKey, buy, sell);
    }

    public synchronized void removePrice(String key, boolean isTag) {
        String fullKey = (isTag ? "#" : "") + key;
        prices.removeIf(e -> e.key.equals(fullKey));
        deletePriceLine(fullKey);
    }

    public synchronized PriceEntry findEntry(String key, boolean isTag) {
        String fullKey = (isTag ? "#" : "") + key;
        for (PriceEntry e : prices) {
            if (e.key.equals(fullKey)) {
                return e;
            }
        }
        return null;
    }

    private static String priceLine(String fullKey, long buy, Long sell) {
        return sell == null
            ? "\"" + fullKey + "\" = { buy = " + buy + " }"
            : "\"" + fullKey + "\" = { buy = " + buy + ", sell = " + sell + " }";
    }

    private void rewritePriceLine(String fullKey, long buy, Long sell) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile, StandardCharsets.UTF_8));
            Pattern entryStart = Pattern.compile("^\\s*\"" + Pattern.quote(fullKey) + "\"\\s*=");
            boolean inPrices = false;
            int insertAt = -1;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.equals("[prices]")) {
                    inPrices = true;
                    continue;
                }
                if (inPrices && t.startsWith("[") && t.endsWith("]")) {
                    insertAt = i;
                    break;
                }
                if (inPrices && entryStart.matcher(lines.get(i)).find()) {
                    lines.set(i, priceLine(fullKey, buy, sell));
                    Files.write(configFile, lines, StandardCharsets.UTF_8);
                    return;
                }
            }
            String line = priceLine(fullKey, buy, sell);
            if (inPrices) {
                lines.add(insertAt < 0 ? lines.size() : insertAt, line);
            } else {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                    lines.add("");
                }
                lines.add("[prices]");
                lines.add(line);
            }
            Files.write(configFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            MarketBlocks.LOGGER.error("Failed to write price entry to config file", e);
        }
    }

    private void deletePriceLine(String fullKey) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile, StandardCharsets.UTF_8));
            Pattern entryStart = Pattern.compile("^\\s*\"" + Pattern.quote(fullKey) + "\"\\s*=");
            boolean inPrices = false;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.equals("[prices]")) {
                    inPrices = true;
                    continue;
                }
                if (inPrices && t.startsWith("[") && t.endsWith("]")) {
                    break;
                }
                if (inPrices && entryStart.matcher(lines.get(i)).find()) {
                    lines.remove(i);
                    Files.write(configFile, lines, StandardCharsets.UTF_8);
                    return;
                }
            }
        } catch (IOException e) {
            MarketBlocks.LOGGER.error("Failed to remove price entry from config file", e);
        }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z0-9_]+)\\s*=\\s*(.+)$");
    private static final Pattern QUOTED = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"$");
    private static final Pattern PRICE_ENTRY =
        Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"\\s*=\\s*\\{\\s*buy\\s*=\\s*(\\d+)\\s*(?:,\\s*sell\\s*=\\s*(\\d+)\\s*)?\\}$");

    private static MarketConfig parse(String text, boolean strict) {
        MarketConfig cfg = new MarketConfig();
        boolean inPrices = false;
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("[prices]")) {
                inPrices = true;
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                throw new IllegalArgumentException("Unknown section on line " + lineNo + ": " + line);
            }
            if (inPrices) {
                cfg.prices.add(parsePriceEntry(line, lineNo, strict));
            } else {
                Matcher m = KEY_VALUE.matcher(line);
                if (!m.matches()) {
                    throw new IllegalArgumentException("Cannot parse line " + lineNo + ": " + raw.trim());
                }
                applyScalar(cfg, m.group(1), m.group(2).trim(), lineNo);
            }
        }
        return cfg;
    }

    private static PriceEntry parsePriceEntry(String line, int lineNo, boolean strict) {
        Matcher m = PRICE_ENTRY.matcher(line);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad price entry on line " + lineNo
                + " (expected \"id\" = { buy = N } or \"id\" = { buy = N, sell = M }): " + line);
        }
        String key = m.group(1);
        boolean isTag = key.startsWith("#");
        String idPart = isTag ? key.substring(1) : key;
        Identifier id;
        try {
            id = Identifier.parse(idPart);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad item/tag id on line " + lineNo + ": " + key);
        }
        if (!isTag && strict) {
            if (BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
                throw new IllegalArgumentException("Unknown item id on line " + lineNo + ": " + key);
            }
        }
        long buy = Long.parseLong(m.group(2));
        Long sell = m.group(3) == null ? null : Long.parseLong(m.group(3));
        if (buy < 1) {
            throw new IllegalArgumentException("Buy price must be >= 1 on line " + lineNo);
        }
        if (sell != null && sell < 1) {
            throw new IllegalArgumentException("Sell price must be >= 1 on line " + lineNo);
        }
        return new PriceEntry(key, isTag, buy, sell);
    }

    private static void applyScalar(MarketConfig cfg, String key, String value, int lineNo) {
        switch (key) {
            case "currencyName" -> cfg.currencyName = parseString(value, lineNo, key);
            case "currencySymbol" -> cfg.currencySymbol = parseString(value, lineNo, key);
            case "startingBalance" -> cfg.startingBalance = parseLong(value, lineNo, key, 0, Long.MAX_VALUE);
            case "defaultSellRatio" -> cfg.defaultSellRatio = parseDouble(value, lineNo, key, 0.0, 1.0);
            case "playerStallTaxPercent" -> cfg.playerStallTaxPercent = parseDouble(value, lineNo, key, 0.0, 100.0);
            case "maxStallsPerPlayer" -> cfg.maxStallsPerPlayer = (int) parseLong(value, lineNo, key, 1, 1000);
            case "allowCreativePurchases" -> cfg.allowCreativePurchases = parseBool(value, lineNo, key);
            default -> throw new IllegalArgumentException("Unknown setting on line " + lineNo + ": " + key);
        }
        if (cfg.currencyName.isBlank()) {
            throw new IllegalArgumentException("currencyName must not be blank");
        }
        if (cfg.currencySymbol.isBlank()) {
            throw new IllegalArgumentException("currencySymbol must not be blank");
        }
    }

    private static String parseString(String value, int lineNo, String key) {
        Matcher m = QUOTED.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException(key + " must be a quoted string on line " + lineNo);
        }
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long parseLong(String value, int lineNo, String key, long min, long max) {
        try {
            long v = Long.parseLong(value);
            if (v < min || v > max) {
                throw new IllegalArgumentException(key + " out of range on line " + lineNo);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number on line " + lineNo);
        }
    }

    private static double parseDouble(String value, int lineNo, String key, double min, double max) {
        try {
            double v = Double.parseDouble(value);
            if (v < min || v > max) {
                throw new IllegalArgumentException(key + " out of range on line " + lineNo);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number on line " + lineNo);
        }
    }

    private static boolean parseBool(String value, int lineNo, String key) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.equals("true")) {
            return true;
        }
        if (v.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false on line " + lineNo);
    }

    /** Strips a # comment, ignoring # inside quoted strings. */
    private static String stripComment(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (c == '#' && !inQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String defaultFileText() {
        return """
            # ============================================================
            # Market Blocks configuration
            # A dead-simple server market: two blocks, sane configs, and a
            # working economy in five minutes.
            #
            # Reload in-game at any time with:  /market reload   (operators)
            # A broken entry fails loudly and the previous config stays live.
            # ============================================================

            # --- Currency identity: shown in GUIs and commands ---
            # Display name, e.g. "Credits", "Coins", "Bucks".
            currencyName = "Credits"
            # Compact symbol shown before amounts, e.g. "$".
            currencySymbol = "$"
            # Granted once to each player the first time they are seen.
            startingBalance = 100

            # --- Friendly-server defaults ---
            # Used when a price entry sets only a buy price: sell = buy * ratio.
            # 0.6 means an item bought for 10 sells back for 6. Range: 0.0 - 1.0.
            defaultSellRatio = 0.6
            # Tax on player-to-player stall sales, in percent. The taxed cut
            # simply vanishes (a sink). 0.0 = no tax. Range: 0.0 - 100.0.
            playerStallTaxPercent = 0.0
            # Anti-spam cap: how many market stalls one player may own at once.
            maxStallsPerPlayer = 5
            # If false (recommended), creative-mode players cannot buy from
            # stalls or the admin market. Set true to allow it.
            allowCreativePurchases = false

            # --- Price list for the Admin Market (infinite buy/sell shop) ---
            # Map an item ID or item tag to a buy price and sell price.
            # Tag support prices a whole category in one line, e.g. "#minecraft:logs".
            # Format:  "item-or-#tag" = { buy = <price>, sell = <price> }
            # sell may be omitted to derive it from defaultSellRatio above.
            # Operators can also edit these in-game through the Admin Market GUI.
            [prices]
            "minecraft:diamond" = { buy = 100, sell = 60 }
            "#minecraft:logs" = { buy = 4 }
            "minecraft:iron_ingot" = { buy = 20 }
            "minecraft:bread" = { buy = 6 }
            """;
    }
}
