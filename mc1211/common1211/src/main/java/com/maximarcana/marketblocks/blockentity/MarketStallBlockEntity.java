package com.maximarcana.marketblocks.blockentity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.maximarcana.marketblocks.MarketContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Market stall state: the owner's stock (never exposed to hoppers — the
 * block entity itself is not a Container, only the internal SimpleContainer
 * is), per-slot prices, uncollected earnings, and the owner's identity.
 */
public class MarketStallBlockEntity extends BlockEntity {
    public static final int STOCK_SLOTS = 27;

    private final SimpleContainer stock = new SimpleContainer(STOCK_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            MarketStallBlockEntity.this.setChanged();
        }
    };
    /** Stock slot index -> price in currency units. Absent = not for sale. */
    private final Map<Integer, Long> prices = new HashMap<>();
    private long earnings;
    private UUID owner;
    private String ownerName = "";

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(MarketContent.STALL_BLOCK_ENTITY_TYPE, pos, state);
    }

    public SimpleContainer getStock() {
        return stock;
    }

    public Long getPrice(int slot) {
        return prices.get(slot);
    }

    public void setPrice(int slot, long price) {
        if (price < 1) {
            prices.remove(slot);
        } else {
            prices.put(slot, Math.min(price, 10_000_000L));
        }
        setChanged();
    }

    public void clearPricesForEmptySlots() {
        for (int i = 0; i < STOCK_SLOTS; i++) {
            if (stock.getItem(i).isEmpty()) {
                prices.remove(i);
            }
        }
    }

    public long getEarnings() {
        return earnings;
    }

    public void addEarnings(long amount) {
        earnings += amount;
        setChanged();
    }

    /** Withdraws everything; returns the amount withdrawn. */
    public long withdrawEarnings() {
        long amount = earnings;
        earnings = 0;
        setChanged();
        return amount;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag stockTag = new ListTag();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            ItemStack stack = stock.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("slot", i);
                slotTag.put("item", stack.save(registries));
                stockTag.add(slotTag);
            }
        }
        tag.put("stock", stockTag);
        CompoundTag pricesTag = new CompoundTag();
        for (var e : prices.entrySet()) {
            pricesTag.putLong("price_" + e.getKey(), e.getValue());
        }
        tag.put("prices", pricesTag);
        tag.putLong("earnings", earnings);
        if (owner != null) {
            tag.putString("owner", owner.toString());
            tag.putString("owner_name", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stock.clearContent();
        prices.clear();
        for (Tag t : tag.getList("stock", Tag.TAG_COMPOUND)) {
            CompoundTag slotTag = (CompoundTag) t;
            int slot = slotTag.getInt("slot");
            if (slot >= 0 && slot < STOCK_SLOTS) {
                ItemStack stack = ItemStack.parseOptional(registries, slotTag.getCompound("item"));
                if (!stack.isEmpty()) {
                    stock.setItem(slot, stack);
                }
            }
        }
        CompoundTag pricesTag = tag.getCompound("prices");
        for (String key : pricesTag.getAllKeys()) {
            if (key.startsWith("price_")) {
                try {
                    int slot = Integer.parseInt(key.substring("price_".length()));
                    long price = pricesTag.getLong(key);
                    if (slot >= 0 && slot < STOCK_SLOTS && price >= 1) {
                        prices.put(slot, price);
                    }
                } catch (NumberFormatException ignored) {
                    // Corrupt key: skip rather than fail the whole load.
                }
            }
        }
        earnings = tag.getLong("earnings");
        if (tag.contains("owner")) {
            try {
                owner = UUID.fromString(tag.getString("owner"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        ownerName = tag.getString("owner_name");
    }
}
