package com.maximarcana.marketblocks.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-persisted economy: virtual per-player balances, pending item
 * deliveries (stall stock whose owner was offline at break time), and
 * per-player stall ownership counts. Everything mutates on the server
 * thread, so each transaction is atomic by construction.
 */
public class MarketEconomy extends SavedData {
    // 1.21.1: no SavedDataType yet; storage is keyed by a plain String passed
    // to computeIfAbsent alongside a SavedData.Factory. The DataFixTypes
    // entry must be non-null: DimensionDataStorage calls type.update(...)
    // unconditionally when loading existing data from disk.
    // SAVED_DATA_COMMAND_STORAGE is the closest semantic fit (arbitrary
    // per-namespace NBT), and fixers short-circuit anyway because we always
    // save with the current DataVersion.
    private static final SavedData.Factory<MarketEconomy> FACTORY = new SavedData.Factory<>(
        MarketEconomy::new, MarketEconomy::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<UUID, Integer> stallCounts = new HashMap<>();

    public MarketEconomy() {
    }

    public static MarketEconomy get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(FACTORY, "marketblocks_economy");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag bal = new CompoundTag();
        for (var e : balances.entrySet()) {
            bal.putLong(e.getKey().toString(), e.getValue());
        }
        tag.put("balances", bal);
        CompoundTag counts = new CompoundTag();
        for (var e : stallCounts.entrySet()) {
            counts.putInt(e.getKey().toString(), e.getValue());
        }
        tag.put("stall_counts", counts);
        CompoundTag pending = new CompoundTag();
        for (var e : pendingDeliveries.entrySet()) {
            ListTag list = new ListTag();
            for (ItemStack stack : e.getValue()) {
                list.add(stack.save(registries));
            }
            pending.put(e.getKey().toString(), list);
        }
        tag.put("pending_deliveries", pending);
        return tag;
    }

    private static MarketEconomy load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketEconomy economy = new MarketEconomy();
        CompoundTag bal = tag.getCompound("balances");
        for (String key : bal.getAllKeys()) {
            try {
                economy.balances.put(UUID.fromString(key), bal.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Corrupt key: skip rather than fail the whole load.
            }
        }
        CompoundTag counts = tag.getCompound("stall_counts");
        for (String key : counts.getAllKeys()) {
            try {
                economy.stallCounts.put(UUID.fromString(key), counts.getInt(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag pending = tag.getCompound("pending_deliveries");
        for (String key : pending.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            List<ItemStack> list = new ArrayList<>();
            for (Tag t : pending.getList(key, Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) t);
                if (!stack.isEmpty()) {
                    list.add(stack);
                }
            }
            if (!list.isEmpty()) {
                economy.pendingDeliveries.put(uuid, list);
            }
        }
        return economy;
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
                player.drop(stack.copy(), false);
            }
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Recovered " + delivered + " item(s) from a broken market stall."), false);
        MarketBlocks.LOGGER.info("Delivered {} pending stall item(s) to {}", delivered,
            player.getScoreboardName());
    }
}
