package com.maximarcana.marketblocks.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.maximarcana.marketblocks.MarketConfig;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Server-persisted economy: virtual per-player balances, pending item
 * deliveries (stall stock whose owner was offline at break time), and
 * per-player stall ownership counts. Everything mutates on the server
 * thread, so each transaction is atomic by construction.
 *
 * Stored once per world in the overworld's MapStorage ("data/marketblocks_economy.dat").
 */
public class MarketEconomy extends WorldSavedData {
    public static final String DATA_NAME = "marketblocks_economy";

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<UUID, Integer> stallCounts = new HashMap<>();

    public MarketEconomy() {
        super(DATA_NAME);
    }

    public MarketEconomy(String name) {
        super(name);
    }

    public static MarketEconomy get(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        MarketEconomy eco = (MarketEconomy) storage.getOrLoadData(MarketEconomy.class, DATA_NAME);
        if (eco == null) {
            eco = new MarketEconomy();
            storage.setData(DATA_NAME, eco);
        }
        return eco;
    }

    // ------------------------------------------------------------------
    // Balances
    // ------------------------------------------------------------------

    public long getBalance(UUID player) {
        Long bal = balances.get(player);
        if (bal == null) {
            bal = MarketConfig.get().startingBalance;
            balances.put(player, bal);
            markDirty();
        }
        return bal;
    }

    /**
     * Adds delta (may be negative). Returns false and changes nothing if the
     * result would go negative.
     */
    public boolean addBalance(UUID player, long delta) {
        long next = getBalance(player) + delta;
        if (next < 0) {
            return false;
        }
        balances.put(player, next);
        markDirty();
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
        markDirty();
        return true;
    }

    // ------------------------------------------------------------------
    // Stall ownership counts
    // ------------------------------------------------------------------

    public int getStallCount(UUID player) {
        Integer n = stallCounts.get(player);
        return n == null ? 0 : n;
    }

    public void incrementStallCount(UUID player) {
        stallCounts.put(player, getStallCount(player) + 1);
        markDirty();
    }

    public void decrementStallCount(UUID player) {
        int next = Math.max(0, getStallCount(player) - 1);
        if (next == 0) {
            stallCounts.remove(player);
        } else {
            stallCounts.put(player, next);
        }
        markDirty();
    }

    // ------------------------------------------------------------------
    // Pending deliveries (offline-owner stock recovery)
    // ------------------------------------------------------------------

    public void addPendingDelivery(UUID player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        List<ItemStack> list = pendingDeliveries.get(player);
        if (list == null) {
            list = new ArrayList<>();
            pendingDeliveries.put(player, list);
        }
        list.add(stack.copy());
        markDirty();
    }

    public boolean hasPendingDeliveries(UUID player) {
        List<ItemStack> list = pendingDeliveries.get(player);
        return list != null && !list.isEmpty();
    }

    /** Gives pending items to a joining player; leftovers drop at their feet. */
    public static void deliverPending(EntityPlayerMP player) {
        MarketEconomy economy = get(player.getServer());
        List<ItemStack> list = economy.pendingDeliveries.remove(player.getUniqueID());
        if (list == null || list.isEmpty()) {
            return;
        }
        economy.markDirty();
        int delivered = 0;
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                continue;
            }
            if (player.inventory.addItemStackToInventory(stack.copy())) {
                delivered += stack.getCount();
            } else {
                player.dropItem(stack.copy(), false);
            }
        }
        player.sendMessage(new TextComponentString(
                "Recovered " + delivered + " item(s) from a broken market stall."));
    }

    // ------------------------------------------------------------------
    // NBT persistence
    // ------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        balances.clear();
        NBTTagList balList = nbt.getTagList("balances", 10);
        for (int i = 0; i < balList.tagCount(); i++) {
            NBTTagCompound t = balList.getCompoundTagAt(i);
            balances.put(UUID.fromString(t.getString("uuid")), t.getLong("balance"));
        }
        stallCounts.clear();
        NBTTagList countList = nbt.getTagList("stall_counts", 10);
        for (int i = 0; i < countList.tagCount(); i++) {
            NBTTagCompound t = countList.getCompoundTagAt(i);
            stallCounts.put(UUID.fromString(t.getString("uuid")), t.getInteger("count"));
        }
        pendingDeliveries.clear();
        NBTTagList pendList = nbt.getTagList("pending", 10);
        for (int i = 0; i < pendList.tagCount(); i++) {
            NBTTagCompound t = pendList.getCompoundTagAt(i);
            UUID owner = UUID.fromString(t.getString("owner"));
            List<ItemStack> stacks = new ArrayList<>();
            NBTTagList items = t.getTagList("items", 10);
            for (int j = 0; j < items.tagCount(); j++) {
                stacks.add(new ItemStack(items.getCompoundTagAt(j)));
            }
            pendingDeliveries.put(owner, stacks);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList balList = new NBTTagList();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("uuid", e.getKey().toString());
            t.setLong("balance", e.getValue());
            balList.appendTag(t);
        }
        compound.setTag("balances", balList);
        NBTTagList countList = new NBTTagList();
        for (Map.Entry<UUID, Integer> e : stallCounts.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("uuid", e.getKey().toString());
            t.setInteger("count", e.getValue());
            countList.appendTag(t);
        }
        compound.setTag("stall_counts", countList);
        NBTTagList pendList = new NBTTagList();
        for (Map.Entry<UUID, List<ItemStack>> e : pendingDeliveries.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("owner", e.getKey().toString());
            NBTTagList items = new NBTTagList();
            for (ItemStack stack : e.getValue()) {
                items.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            t.setTag("items", items);
            pendList.appendTag(t);
        }
        compound.setTag("pending", pendList);
        return compound;
    }
}
