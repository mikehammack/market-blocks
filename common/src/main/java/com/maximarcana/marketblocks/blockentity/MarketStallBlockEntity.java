package com.maximarcana.marketblocks.blockentity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.maximarcana.marketblocks.MarketContent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < STOCK_SLOTS; i++) {
            ItemStack stack = stock.getItem(i);
            if (!stack.isEmpty()) {
                output.store("slot_" + i, ItemStack.CODEC, stack);
            }
        }
        for (var e : prices.entrySet()) {
            output.putLong("price_" + e.getKey(), e.getValue());
        }
        output.putLong("earnings", earnings);
        if (owner != null) {
            output.putString("owner", owner.toString());
            output.putString("owner_name", ownerName);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stock.clearContent();
        prices.clear();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            final int slot = i;
            input.read("slot_" + slot, ItemStack.CODEC).ifPresent(s -> {
                if (!s.isEmpty()) {
                    stock.setItem(slot, s);
                }
            });
            input.getLong("price_" + slot).ifPresent(p -> {
                if (p >= 1) {
                    prices.put(slot, p);
                }
            });
        }
        earnings = input.getLongOr("earnings", 0);
        input.getString("owner").ifPresent(s -> {
            try {
                owner = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        });
        ownerName = input.getStringOr("owner_name", "");
    }
}
