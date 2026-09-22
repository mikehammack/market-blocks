package com.maximarcana.marketblocks.menu;

import java.util.List;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Owner management GUI for a market stall. Stock slots are real (drag items
 * in/out); the control row sets per-item prices and withdraws earnings.
 * Rendered client-side by the vanilla chest screen (GENERIC_9x6).
 */
public class StallOwnerMenu extends AbstractContainerMenu {
    private static final int STOCK_SLOTS = MarketStallBlockEntity.STOCK_SLOTS; // 27
    private static final int CONTROL_SLOTS = 27; // one full row + filler rows
    private static final int INFO_SLOT = 27;
    private static final int DEC10_SLOT = 28;
    private static final int DEC1_SLOT = 29;
    private static final int INC1_SLOT = 30;
    private static final int INC10_SLOT = 31;
    private static final int WITHDRAW_SLOT = 32;
    private static final int HELP_SLOT = 33;
    private static final int PLAYER_INV_START = STOCK_SLOTS + CONTROL_SLOTS; // 54

    private final MarketStallBlockEntity stall;
    private final ContainerLevelAccess access;
    private final SimpleContainer controls = new SimpleContainer(CONTROL_SLOTS);
    private int selectedSlot = -1;

    public StallOwnerMenu(int containerId, Inventory playerInventory, MarketStallBlockEntity stall) {
        super(MenuType.GENERIC_9x6, containerId);
        this.stall = stall;
        this.access = ContainerLevelAccess.create(stall.getLevel(), stall.getBlockPos());

        for (int i = 0; i < STOCK_SLOTS; i++) {
            addSlot(new Slot(stall.getStock(), i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        for (int i = 0; i < CONTROL_SLOTS; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 72 + (i / 9) * 18;
            addSlot(MenuUtil.ghost(controls, i, x, y));
        }
        MenuUtil.addStandardInventorySlots(this::addSlot, playerInventory, 8, 140);

        for (int i = 0; i < STOCK_SLOTS; i++) {
            if (!stall.getStock().getItem(i).isEmpty()) {
                selectedSlot = i;
                break;
            }
        }
        if (selectedSlot < 0) {
            selectedSlot = 0;
        }
        rebuildControls();
    }

    // ------------------------------------------------------------------
    // Control row
    // ------------------------------------------------------------------

    private void rebuildControls() {
        ItemStack selected = stall.getStock().getItem(selectedSlot);
        Long price = stall.getPrice(selectedSlot);

        if (selected.isEmpty()) {
            controls.setItem(INFO_SLOT - STOCK_SLOTS,
                MenuUtil.display(Items.PAPER, "No item selected", ChatFormatting.GRAY,
                    "Left-click a stocked item above,",
                    "then set its price with the buttons."));
        } else {
            ItemStack info = selected.copyWithCount(1);
            info.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Selected: ").withStyle(ChatFormatting.YELLOW)
                    .append(selected.getHoverName()));
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.literal(price == null ? "Not for sale"
                : "Price: " + MenuUtil.money(price) + " each")
                .withStyle(price == null ? ChatFormatting.RED : ChatFormatting.GREEN));
            lore.add(Component.literal("Left-click a button: apply shown amount")
                .withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Right-click a button: apply the opposite")
                .withStyle(ChatFormatting.GRAY));
            info.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));
            controls.setItem(INFO_SLOT - STOCK_SLOTS, info);
        }

        controls.setItem(DEC10_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.REDSTONE_BLOCK, "-10", ChatFormatting.RED,
                "Left-click: price - 10", "Right-click: price + 10"));
        controls.setItem(DEC1_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.REDSTONE, "-1", ChatFormatting.RED,
                "Left-click: price - 1", "Right-click: price + 1"));
        controls.setItem(INC1_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.GOLD_NUGGET, "+1", ChatFormatting.GREEN,
                "Left-click: price + 1", "Right-click: price - 1"));
        controls.setItem(INC10_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.GOLD_INGOT, "+10", ChatFormatting.GREEN,
                "Left-click: price + 10", "Right-click: price - 10"));
        controls.setItem(WITHDRAW_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.EMERALD, "Withdraw earnings", ChatFormatting.GREEN,
                "Uncollected: " + MenuUtil.money(stall.getEarnings()),
                "Click to move it to your balance."));
        controls.setItem(HELP_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.BOOK, "How it works", ChatFormatting.YELLOW,
                "Stock items in the top rows.",
                "Select one, set its price, and",
                "other players can buy from it.",
                "Only you (or an operator)",
                "can break this stall."));
        ItemStack filler = MenuUtil.display(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY);
        for (int i = HELP_SLOT + 1; i < STOCK_SLOTS + CONTROL_SLOTS; i++) {
            controls.setItem(i - STOCK_SLOTS, filler);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (slotId >= 0 && slotId < STOCK_SLOTS && clickType == ClickType.PICKUP) {
            super.clicked(slotId, button, clickType, player);
            selectedSlot = slotId;
            stall.clearPricesForEmptySlots();
            rebuildControls();
            broadcastChanges();
            return;
        }
        if (clickType == ClickType.PICKUP) {
            if (slotId == DEC10_SLOT || slotId == DEC1_SLOT
                || slotId == INC1_SLOT || slotId == INC10_SLOT) {
                long delta = switch (slotId) {
                    case DEC10_SLOT -> -10;
                    case DEC1_SLOT -> -1;
                    case INC1_SLOT -> 1;
                    default -> 10;
                };
                if (button == 1) {
                    delta = -delta;
                }
                adjustPrice(serverPlayer, delta);
                return;
            }
            if (slotId == WITHDRAW_SLOT) {
                withdraw(serverPlayer);
                return;
            }
            if (slotId == INFO_SLOT || slotId == HELP_SLOT) {
                return;
            }
            if (slotId >= STOCK_SLOTS && slotId < PLAYER_INV_START) {
                return; // filler
            }
        }
        super.clicked(slotId, button, clickType, player);
        if (slotId >= 0 && slotId < STOCK_SLOTS) {
            stall.clearPricesForEmptySlots();
            rebuildControls();
            broadcastChanges();
        }
    }

    private void adjustPrice(ServerPlayer player, long delta) {
        ItemStack selected = stall.getStock().getItem(selectedSlot);
        if (selected.isEmpty()) {
            MenuUtil.msgActionBar(player, "Select a stocked item first.");
            return;
        }
        Long current = stall.getPrice(selectedSlot);
        long next;
        if (current == null) {
            next = delta > 0 ? delta : 1;
        } else {
            next = Math.max(1, current + delta);
        }
        stall.setPrice(selectedSlot, next);
        rebuildControls();
        broadcastChanges();
        MenuUtil.msgActionBar(player,
            selected.getHoverName().getString() + " now sells for " + MenuUtil.money(next) + " each.");
    }

    private void withdraw(ServerPlayer player) {
        long amount = stall.withdrawEarnings();
        if (amount <= 0) {
            MenuUtil.msgActionBar(player, "No earnings to withdraw.");
            return;
        }
        MarketEconomy.get(player.level().getServer()).addBalance(player.getUUID(), amount);
        rebuildControls();
        broadcastChanges();
        MenuUtil.msg(player, "Withdrew " + MenuUtil.money(amount) + " to your balance.");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < STOCK_SLOTS) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_INV_START) {
            return ItemStack.EMPTY; // ghost controls
        } else {
            if (!this.moveItemStackTo(stack, 0, STOCK_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        stall.clearPricesForEmptySlots();
        rebuildControls();
        broadcastChanges();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, MarketContent.STALL_BLOCK);
    }
}
