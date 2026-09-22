package com.maximarcana.marketblocks.menu;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.maximarcana.marketblocks.MarketConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/** Shared GUI helpers: ghost (display/button) slots, priced display stacks, money formatting. */
public final class MenuUtil {
    private MenuUtil() {
    }

    /** A slot the player can neither take from nor place into; clicks are handled by the menu. */
    public static Slot ghost(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    /** Display stack with a custom name and lore lines. */
    public static ItemStack display(ItemLike item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(new ArrayList<>(lore)));
        }
        return stack;
    }

    public static ItemStack display(ItemLike item, String name, ChatFormatting nameColor,
            String... loreLines) {
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return display(item, Component.literal(name).withStyle(nameColor), lore);
    }

    /** "$1,250" style formatting using the configured currency symbol. */
    public static String money(long amount) {
        return MarketConfig.get().currencySymbol
            + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    public static void msg(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text), false);
    }

    public static void msgActionBar(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text), true);
    }

    /** How many more of `stack` fit into the player's main inventory. */
    public static int maxFit(net.minecraft.world.entity.player.Inventory inv, ItemStack stack) {
        int fit = 0;
        int max = stack.getMaxStackSize();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                fit += max;
            } else if (ItemStack.isSameItemSameComponents(s, stack)) {
                fit += Math.max(0, max - s.getCount());
            }
        }
        return fit;
    }

    /** Removes up to `count` matching items from the player's inventory; returns amount removed. */
    public static int removeMatching(net.minecraft.world.entity.player.Inventory inv, ItemStack template,
            int count) {
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (removed >= count) {
                break;
            }
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) {
                int take = Math.min(count - removed, s.getCount());
                s.shrink(take);
                removed += take;
            }
        }
        return removed;
    }

    /** Counts matching items in the player's inventory. */
    public static int countMatching(net.minecraft.world.entity.player.Inventory inv, ItemStack template) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Gives items, dropping any overflow at the player's feet. Returns amount that fit. */
    public static int giveOrDrop(ServerPlayer player, ItemStack stack) {
        int fit = maxFit(player.getInventory(), stack);
        int toGive = Math.min(fit, stack.getCount());
        if (toGive > 0) {
            ItemStack give = stack.copyWithCount(toGive);
            int leftover = toGive;
            for (int i = 0; i < player.getInventory().getContainerSize() && leftover > 0; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.isEmpty()) {
                    int put = Math.min(leftover, give.getMaxStackSize());
                    player.getInventory().setItem(i, give.copyWithCount(put));
                    leftover -= put;
                } else if (ItemStack.isSameItemSameComponents(s, give)
                    && s.getCount() < s.getMaxStackSize()) {
                    int put = Math.min(leftover, s.getMaxStackSize() - s.getCount());
                    s.grow(put);
                    leftover -= put;
                }
            }
        }
        int dropped = stack.getCount() - toGive;
        if (dropped > 0) {
            player.drop(stack.copyWithCount(dropped), false, Prediction.SERVER_ONLY);
        }
        return toGive;
    }
}
