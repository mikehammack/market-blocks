package com.maximarcana.marketblocks.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-persisted economy: virtual per-player balances, pending item
 * deliveries (stall stock whose owner was offline at break time), and
 * per-player stall ownership counts. Everything mutates on the server
 * thread, so each transaction is atomic by construction.
 */
public class MarketEconomy extends SavedData {
    private static final Codec<Map<UUID, Long>> BALANCE_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);
    private static final Codec<Map<UUID, Integer>> COUNT_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);
    private static final Codec<Map<UUID, List<ItemStack>>> PENDING_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf());

    private static final Codec<MarketEconomy> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BALANCE_CODEC.fieldOf("balances").forGetter(e -> e.balances),
            PENDING_CODEC.fieldOf("pending_deliveries").forGetter(e -> e.pendingDeliveries),
            COUNT_CODEC.fieldOf("stall_counts").forGetter(e -> e.stallCounts)
        ).apply(instance, MarketEconomy::new));

    public static final SavedDataType<MarketEconomy> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(MarketBlocks.MOD_ID, "economy"),
        MarketEconomy::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<UUID, Integer> stallCounts = new HashMap<>();

    // Codec constructor.
    private MarketEconomy(Map<UUID, Long> balances, Map<UUID, List<ItemStack>> pending,
            Map<UUID, Integer> counts) {
        this.balances.putAll(balances);
        for (var e : pending.entrySet()) {
            this.pendingDeliveries.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        this.stallCounts.putAll(counts);
    }

    public MarketEconomy() {
    }

    public static MarketEconomy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ------------------------------------------------------------------
    // Balances
    // ------------------------------------------------------------------

    public long getBalance(UUID player) {
        return balances.computeIfAbsent(player, u -> {
            setDirty();
            return MarketConfig.get().startingBalance;
        });
    }

    /**
     * Adds delta (may be negative). Returns false and changes nothing if the
     * result would go negative.
     */
    public boolean addBalance(UUID player, long delta) {
        long current = getBalance(player);
        long next = current + delta;
        if (next < 0) {
            return false;
        }
        balances.put(player, next);
        setDirty();
        return true;
    }

    /** Atomic transfer. Returns false if amount <= 0 or sender lacks funds. */
    public boolean transfer(UUID from, UUID to, long amount) {
        if (amount <= 0 || from.equals(to)) {
            return false;
        }
        long fromBalance = getBalance(from);
        if (fromBalance < amount) {
            return false;
        }
        balances.put(from, fromBalance - amount);
        balances.put(to, getBalance(to) + amount);
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------
    // Stall ownership counts
    // ------------------------------------------------------------------

    public int getStallCount(UUID player) {
        return stallCounts.getOrDefault(player, 0);
    }

    public void incrementStallCount(UUID player) {
        stallCounts.put(player, getStallCount(player) + 1);
        setDirty();
    }

    public void decrementStallCount(UUID player) {
        int next = Math.max(0, getStallCount(player) - 1);
        if (next == 0) {
            stallCounts.remove(player);
        } else {
            stallCounts.put(player, next);
        }
        setDirty();
    }

    // ------------------------------------------------------------------
    // Pending deliveries (offline-owner stock recovery)
    // ------------------------------------------------------------------

    public void addPendingDelivery(UUID player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        pendingDeliveries.computeIfAbsent(player, u -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public boolean hasPendingDeliveries(UUID player) {
        List<ItemStack> list = pendingDeliveries.get(player);
        return list != null && !list.isEmpty();
    }

    /** Gives pending items to a joining player; leftovers drop at their feet. */
    public static void deliverPending(ServerPlayer player) {
        MarketEconomy economy = get(player.level().getServer());
        List<ItemStack> list = economy.pendingDeliveries.remove(player.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        economy.setDirty();
        int delivered = 0;
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                continue;
            }
            if (player.getInventory().add(stack.copy())) {
                delivered += stack.getCount();
            } else {
                player.drop(stack.copy(), false, Prediction.SERVER_ONLY);
            }
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Recovered " + delivered + " item(s) from a broken market stall."), false);
        MarketBlocks.LOGGER.info("Delivered {} pending stall item(s) to {}", delivered,
            player.getScoreboardName());
    }
}
