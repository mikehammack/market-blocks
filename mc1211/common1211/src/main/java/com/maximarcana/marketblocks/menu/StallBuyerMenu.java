package com.maximarcana.marketblocks.menu;

import java.util.ArrayList;
import java.util.List;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Buyer GUI for a market stall. Slots are display-only; every click is
 * re-validated server-side (price, stock, balance, creative mode) before
 * anything moves. Rendered client-side by the vanilla chest screen (GENERIC_9x3).
 */
public class StallBuyerMenu extends AbstractContainerMenu {
    private static final int LISTING_SLOTS = MarketStallBlockEntity.STOCK_SLOTS; // 27
    private static final int PLAYER_INV_START = LISTING_SLOTS;

    private final MarketStallBlockEntity stall;
    private final ContainerLevelAccess access;
    private final SimpleContainer display = new SimpleContainer(LISTING_SLOTS);

    public StallBuyerMenu(int containerId, Inventory playerInventory, MarketStallBlockEntity stall) {
        super(MenuType.GENERIC_9x3, containerId);
        this.stall = stall;
        this.access = ContainerLevelAccess.create(stall.getLevel(), stall.getBlockPos());

        for (int i = 0; i < LISTING_SLOTS; i++) {
            addSlot(MenuUtil.ghost(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        MenuUtil.addStandardInventorySlots(this::addSlot, playerInventory, 8, 86);
        rebuildDisplay();
    }

    private void rebuildDisplay() {
        MarketConfig cfg = MarketConfig.get();
        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack stock = stall.getStock().getItem(i);
            Long price = stall.getPrice(i);
            if (stock.isEmpty() || price == null) {
                display.setItem(i, ItemStack.EMPTY);
                continue;
            }
            ItemStack shown = stock.copyWithCount(1);
            shown.set(DataComponents.CUSTOM_NAME, stock.getHoverName());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Price: " + MenuUtil.money(price) + " each")
                .withStyle(ChatFormatting.GOLD));
            lore.add(Component.literal("Stock: " + stock.getCount()).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Left-click: buy 1").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Right-click: buy a stack").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Shift-click: buy as many as possible")
                .withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Seller keeps "
                + MenuUtil.money(Math.round(price * (1.0 - cfg.playerStallTaxPercent / 100.0)))
                + " per item after " + cfg.playerStallTaxPercent + "% tax")
                .withStyle(ChatFormatting.DARK_GRAY));
            shown.set(DataComponents.LORE, new ItemLore(lore));
            display.setItem(i, shown);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (slotId >= 0 && slotId < LISTING_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                // Left = buy 1, right = buy a stack.
                buy(serverPlayer, slotId, button == 0 ? 1 : 64);
            } else if (clickType == ClickType.QUICK_MOVE) {
                buy(serverPlayer, slotId, Integer.MAX_VALUE);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Server-authoritative purchase. Price and stock are re-read from the
     * block entity on every click; the client never supplies them.
     */
    private void buy(ServerPlayer player, int slot, int wantQty) {
        MarketConfig cfg = MarketConfig.get();

        if (player.isCreative() && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }

        ItemStack stock = stall.getStock().getItem(slot);
        Long price = stall.getPrice(slot);
        if (stock.isEmpty() || price == null) {
            rebuildDisplay();
            broadcastChanges();
            return;
        }

        MarketEconomy economy = MarketEconomy.get(player.level().getServer());
        long balance = economy.getBalance(player.getUUID());

        int qty = Math.min(wantQty, stock.getCount());
        if (price > 0) {
            qty = (int) Math.min(qty, balance / price);
        }
        if (qty <= 0) {
            MenuUtil.msgActionBar(player,
                "Not enough " + cfg.currencyName.toLowerCase() + " (need " + MenuUtil.money(price) + ").");
            rebuildDisplay();
            broadcastChanges();
            return;
        }
        qty = Math.min(qty, MenuUtil.maxFit(player.getInventory(), stock));
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "No room in your inventory.");
            return;
        }

        long total = price * qty;
        // Re-check everything atomically before committing.
        ItemStack fresh = stall.getStock().getItem(slot);
        Long freshPrice = stall.getPrice(slot);
        if (fresh.isEmpty() || freshPrice == null || !freshPrice.equals(price)
            || fresh.getCount() < qty || economy.getBalance(player.getUUID()) < total) {
            rebuildDisplay();
            broadcastChanges();
            MenuUtil.msgActionBar(player, "Sale changed, try again.");
            return;
        }

        economy.addBalance(player.getUUID(), -total);
        stall.getStock().removeItem(slot, qty);
        long tax = Math.round(total * cfg.playerStallTaxPercent / 100.0);
        stall.addEarnings(total - tax);
        stall.clearPricesForEmptySlots();

        ItemStack give = stock.copyWithCount(qty);
        int given = MenuUtil.giveOrDrop(player, give);

        rebuildDisplay();
        broadcastChanges();
        MenuUtil.msg(player, "Bought " + given + "x " + stock.getHoverName().getString()
            + " for " + MenuUtil.money(total) + ".");
        MarketBlocks.LOGGER.debug("{} bought {}x {} from {}'s stall for {}",
            player.getScoreboardName(), given, stock.getHoverName().getString(),
            stall.getOwnerName(), total);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking is handled in clicked(); nothing moves by default.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, MarketContent.STALL_BLOCK);
    }
}
