package com.maximarcana.marketblocks.blockentity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * Market stall state: the owner's stock (an internal InventoryBasic, never
 * exposed as a block inventory so hoppers cannot touch it), per-slot prices,
 * uncollected earnings, and the owner's identity.
 */
public class MarketStallTileEntity extends TileEntity {
    public static final int STOCK_SLOTS = 27;

    private final InventoryBasic stock = new InventoryBasic("stall_stock", false, STOCK_SLOTS) {
        @Override
        public void markDirty() {
            super.markDirty();
            MarketStallTileEntity.this.markDirty();
        }
    };
    /** Stock slot index -> price in currency units. Absent = not for sale. */
    private final Map<Integer, Long> prices = new HashMap<>();
    private long earnings;
    private UUID owner;
    private String ownerName = "";

    public InventoryBasic getStock() {
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
        markDirty();
    }

    public void clearPricesForEmptySlots() {
        for (int i = 0; i < STOCK_SLOTS; i++) {
            if (stock.getStackInSlot(i).isEmpty()) {
                prices.remove(i);
            }
        }
    }

    public long getEarnings() {
        return earnings;
    }

    public void addEarnings(long amount) {
        earnings += amount;
        markDirty();
    }

    /** Withdraws everything; returns the amount withdrawn. */
    public long withdrawEarnings() {
        long amount = earnings;
        earnings = 0;
        markDirty();
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
        markDirty();
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                stack.writeToNBT(tag);
                items.appendTag(tag);
            }
        }
        compound.setTag("Items", items);
        NBTTagList priceList = new NBTTagList();
        for (Map.Entry<Integer, Long> e : prices.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Slot", e.getKey().byteValue());
            tag.setLong("Price", e.getValue());
            priceList.appendTag(tag);
        }
        compound.setTag("Prices", priceList);
        compound.setLong("Earnings", earnings);
        if (owner != null) {
            compound.setString("Owner", owner.toString());
            compound.setString("OwnerName", ownerName);
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        for (int i = 0; i < STOCK_SLOTS; i++) {
            stock.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        prices.clear();
        NBTTagList items = compound.getTagList("Items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound tag = items.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < STOCK_SLOTS) {
                stock.setInventorySlotContents(slot, new ItemStack(tag));
            }
        }
        NBTTagList priceList = compound.getTagList("Prices", 10);
        for (int i = 0; i < priceList.tagCount(); i++) {
            NBTTagCompound tag = priceList.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 0xFF;
            long price = tag.getLong("Price");
            if (slot >= 0 && slot < STOCK_SLOTS && price >= 1) {
                prices.put(slot, price);
            }
        }
        earnings = compound.getLong("Earnings");
        if (compound.hasKey("Owner")) {
            try {
                owner = UUID.fromString(compound.getString("Owner"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        } else {
            owner = null;
        }
        ownerName = compound.getString("OwnerName");
    }

    // ------------------------------------------------------------------
    // Client sync: the buyer's GUI is built from the client-side tile
    // entity copy, so stock/prices/earnings must reach the client.
    // ------------------------------------------------------------------

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }
}
