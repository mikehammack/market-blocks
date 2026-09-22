package com.maximarcana.marketblocks.menu;

import java.text.NumberFormat;
import java.util.Locale;

import com.maximarcana.marketblocks.MarketConfig;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/** Shared GUI helpers: ghost (display/button) slots, priced display stacks, money formatting. */
public final class MenuUtil {
    private MenuUtil() {
    }

    /** A slot the player can neither take from nor place into; clicks are handled by the container. */
    public static Slot ghost(IInventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean canTakeStack(net.minecraft.entity.player.EntityPlayer player) {
                return false;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        };
    }

    /** Display stack with a custom name and lore lines. */
    public static ItemStack display(Item item, String name, TextFormatting nameColor,
            String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.setStackDisplayName(nameColor + name);
        if (loreLines.length > 0) {
            NBTTagCompound display = stack.getOrCreateSubCompound("display");
            NBTTagList lore = new NBTTagList();
            for (String line : loreLines) {
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + line));
            }
            display.setTag("Lore", lore);
        }
        return stack;
    }

    /** "$1,250" style formatting using the configured currency symbol. */
    public static String money(long amount) {
        return MarketConfig.get().currencySymbol
            + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    public static void msg(EntityPlayerMP player, String text) {
        player.sendMessage(new TextComponentString(text));
    }

    public static void msgActionBar(EntityPlayerMP player, String text) {
        player.sendStatusMessage(new TextComponentString(text), true);
    }

    private static boolean matches(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem()
            && a.getMetadata() == b.getMetadata()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** Main-inventory slot count (excludes armor and offhand). */
    private static final int MAIN_SLOTS = 36;

    /** How many more of `stack` fit into the player's main inventory. */
    public static int maxFit(InventoryPlayer inv, ItemStack stack) {
        int fit = 0;
        int max = stack.getMaxStackSize();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) {
                fit += max;
            } else if (matches(s, stack)) {
                fit += Math.max(0, max - s.getCount());
            }
        }
        return fit;
    }

    /** Removes up to `count` matching items from the player's inventory; returns amount removed. */
    public static int removeMatching(InventoryPlayer inv, ItemStack template, int count) {
        int removed = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (removed >= count) {
                break;
            }
            if (!s.isEmpty() && matches(s, template)) {
                int take = Math.min(count - removed, s.getCount());
                s.shrink(take);
                removed += take;
                if (s.isEmpty()) {
                    inv.setInventorySlotContents(i, ItemStack.EMPTY);
                }
            }
        }
        return removed;
    }

    /** Counts matching items in the player's inventory. */
    public static int countMatching(InventoryPlayer inv, ItemStack template) {
        int total = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && matches(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Gives items, dropping any overflow at the player's feet. Returns amount that fit. */
    public static int giveOrDrop(EntityPlayerMP player, ItemStack stack) {
        InventoryPlayer inv = player.inventory;
        int fit = maxFit(inv, stack);
        int toGive = Math.min(fit, stack.getCount());
        int leftover = toGive;
        for (int i = 0; i < inv.getSizeInventory() && leftover > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) {
                int put = Math.min(leftover, stack.getMaxStackSize());
                ItemStack placed = stack.copy();
                placed.setCount(put);
                inv.setInventorySlotContents(i, placed);
                leftover -= put;
            } else if (matches(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int put = Math.min(leftover, s.getMaxStackSize() - s.getCount());
                s.grow(put);
                leftover -= put;
            }
        }
        int dropped = stack.getCount() - toGive;
        if (dropped > 0) {
            ItemStack drop = stack.copy();
            drop.setCount(dropped);
            player.dropItem(drop, false);
        }
        return toGive;
    }
}
