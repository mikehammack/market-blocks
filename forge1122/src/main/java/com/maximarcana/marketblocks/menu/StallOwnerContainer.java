package com.maximarcana.marketblocks.menu;

import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Owner management GUI for a market stall. Stock slots are real (drag items
 * in/out); the control row sets per-item prices and withdraws earnings.
 * Rendered client-side by the vanilla chest screen (6 rows).
 */
public class StallOwnerContainer extends Container {
    private static final int STOCK_SLOTS = MarketStallTileEntity.STOCK_SLOTS; // 27
    private static final int CONTROL_SLOTS = 27;
    private static final int INFO_SLOT = 27;
    private static final int DEC10_SLOT = 28;
    private static final int DEC1_SLOT = 29;
    private static final int INC1_SLOT = 30;
    private static final int INC10_SLOT = 31;
    private static final int WITHDRAW_SLOT = 32;
    private static final int HELP_SLOT = 33;
    private static final int PLAYER_INV_START = STOCK_SLOTS + CONTROL_SLOTS; // 54

    private final MarketStallTileEntity stall; // null on the client
    private final InventoryBasic controls = new InventoryBasic("controls", false, CONTROL_SLOTS);
    private final World world;
    private final BlockPos pos;
    private int selectedSlot = -1;

    /** Server-side constructor. */
    public StallOwnerContainer(InventoryPlayer playerInv, MarketStallTileEntity stall) {
        this.stall = stall;
        this.world = stall.getWorld();
        this.pos = stall.getPos();
        build(playerInv, stall.getStock());
        if (!world.isRemote) {
            for (int i = 0; i < STOCK_SLOTS; i++) {
                if (!stall.getStock().getStackInSlot(i).isEmpty()) {
                    selectedSlot = i;
                    break;
                }
            }
            if (selectedSlot < 0) {
                selectedSlot = 0;
            }
            rebuildControls();
        }
    }

    /** Client-side constructor (dummy inventories; the server syncs contents). */
    public StallOwnerContainer(InventoryPlayer playerInv, World world, BlockPos pos) {
        this.stall = null;
        this.world = world;
        this.pos = pos;
        build(playerInv, new InventoryBasic("stock", false, STOCK_SLOTS));
    }

    private void build(InventoryPlayer playerInv, IInventory stock) {
        for (int i = 0; i < STOCK_SLOTS; i++) {
            addSlotToContainer(new Slot(stock, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        for (int i = 0; i < CONTROL_SLOTS; i++) {
            addSlotToContainer(MenuUtil.ghost(controls, i, 8 + (i % 9) * 18, 72 + (i / 9) * 18));
        }
        addPlayerInventory(playerInv, 8, 140);
    }

    private void addPlayerInventory(InventoryPlayer playerInv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, x + col * 18, y + 58));
        }
    }

    // ------------------------------------------------------------------
    // Control row
    // ------------------------------------------------------------------

    private void rebuildControls() {
        if (stall == null) {
            return;
        }
        ItemStack selected = stall.getStock().getStackInSlot(selectedSlot);
        Long price = stall.getPrice(selectedSlot);

        if (selected.isEmpty()) {
            controls.setInventorySlotContents(INFO_SLOT - STOCK_SLOTS,
                MenuUtil.display(Items.PAPER, "No item selected", TextFormatting.GRAY,
                    "Left-click a stocked item above,",
                    "then set its price with the buttons."));
        } else {
            ItemStack info = selected.copy();
            info.setCount(1);
            info.setStackDisplayName(TextFormatting.YELLOW + "Selected: "
                + selected.getDisplayName());
            setLore(info,
                (price == null ? TextFormatting.RED + "Not for sale"
                    : TextFormatting.GREEN + "Price: " + MenuUtil.money(price) + " each"),
                TextFormatting.GRAY + "Left-click a button: apply shown amount",
                TextFormatting.GRAY + "Right-click a button: apply the opposite");
            controls.setInventorySlotContents(INFO_SLOT - STOCK_SLOTS, info);
        }

        controls.setInventorySlotContents(DEC10_SLOT - STOCK_SLOTS,
            MenuUtil.display(Item.getItemFromBlock(Blocks.REDSTONE_BLOCK), "-10", TextFormatting.RED,
                "Left-click: price - 10", "Right-click: price + 10"));
        controls.setInventorySlotContents(DEC1_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.REDSTONE, "-1", TextFormatting.RED,
                "Left-click: price - 1", "Right-click: price + 1"));
        controls.setInventorySlotContents(INC1_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.GOLD_NUGGET, "+1", TextFormatting.GREEN,
                "Left-click: price + 1", "Right-click: price - 1"));
        controls.setInventorySlotContents(INC10_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.GOLD_INGOT, "+10", TextFormatting.GREEN,
                "Left-click: price + 10", "Right-click: price - 10"));
        controls.setInventorySlotContents(WITHDRAW_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.EMERALD, "Withdraw earnings", TextFormatting.GREEN,
                "Uncollected: " + MenuUtil.money(stall.getEarnings()),
                "Click to move it to your balance."));
        controls.setInventorySlotContents(HELP_SLOT - STOCK_SLOTS,
            MenuUtil.display(Items.BOOK, "How it works", TextFormatting.YELLOW,
                "Stock items in the top rows.",
                "Select one, set its price, and",
                "other players can buy from it.",
                "Only you (or an operator)",
                "can break this stall."));
        ItemStack filler = new ItemStack(
            net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 1,
            EnumDyeColor.GRAY.getMetadata());
        filler.setStackDisplayName(TextFormatting.GRAY + " ");
        for (int i = HELP_SLOT + 1; i < STOCK_SLOTS + CONTROL_SLOTS; i++) {
            controls.setInventorySlotContents(i - STOCK_SLOTS, filler.copy());
        }
    }

    private static void setLore(ItemStack stack, String... lines) {
        net.minecraft.nbt.NBTTagCompound display = stack.getOrCreateSubCompound("display");
        net.minecraft.nbt.NBTTagList lore = new net.minecraft.nbt.NBTTagList();
        for (String line : lines) {
            lore.appendTag(new net.minecraft.nbt.NBTTagString(line));
        }
        display.setTag("Lore", lore);
    }

    // ------------------------------------------------------------------
    // Interaction (server-authoritative)
    // ------------------------------------------------------------------

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || stall == null) {
            return super.slotClick(slotId, dragType, clickType, player);
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        if (slotId >= 0 && slotId < STOCK_SLOTS && clickType == ClickType.PICKUP) {
            ItemStack result = super.slotClick(slotId, dragType, clickType, player);
            selectedSlot = slotId;
            stall.clearPricesForEmptySlots();
            rebuildControls();
            detectAndSendChanges();
            return result;
        }
        if (clickType == ClickType.PICKUP) {
            if (slotId == DEC10_SLOT || slotId == DEC1_SLOT
                || slotId == INC1_SLOT || slotId == INC10_SLOT) {
                long delta = slotId == DEC10_SLOT ? -10 : slotId == DEC1_SLOT ? -1
                    : slotId == INC1_SLOT ? 1 : 10;
                if (dragType == 1) {
                    delta = -delta;
                }
                adjustPrice(serverPlayer, delta);
                return ItemStack.EMPTY;
            }
            if (slotId == WITHDRAW_SLOT) {
                withdraw(serverPlayer);
                return ItemStack.EMPTY;
            }
            if (slotId == INFO_SLOT || slotId == HELP_SLOT) {
                return ItemStack.EMPTY;
            }
            if (slotId >= STOCK_SLOTS && slotId < PLAYER_INV_START) {
                return ItemStack.EMPTY; // filler
            }
        }
        ItemStack result = super.slotClick(slotId, dragType, clickType, player);
        if (slotId >= 0 && slotId < STOCK_SLOTS) {
            stall.clearPricesForEmptySlots();
            rebuildControls();
            detectAndSendChanges();
        }
        return result;
    }

    private void adjustPrice(EntityPlayerMP player, long delta) {
        ItemStack selected = stall.getStock().getStackInSlot(selectedSlot);
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
        detectAndSendChanges();
        MenuUtil.msgActionBar(player,
            selected.getDisplayName() + " now sells for " + MenuUtil.money(next) + " each.");
    }

    private void withdraw(EntityPlayerMP player) {
        long amount = stall.withdrawEarnings();
        if (amount <= 0) {
            MenuUtil.msgActionBar(player, "No earnings to withdraw.");
            return;
        }
        MarketEconomy.get(player.getServer()).addBalance(player.getUniqueID(), amount);
        rebuildControls();
        detectAndSendChanges();
        MenuUtil.msg(player, "Withdrew " + MenuUtil.money(amount) + " to your balance.");
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < STOCK_SLOTS) {
            if (!this.mergeItemStack(stack, PLAYER_INV_START, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_INV_START) {
            return ItemStack.EMPTY; // ghost controls
        } else {
            if (!this.mergeItemStack(stack, 0, STOCK_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (stall != null) {
            stall.clearPricesForEmptySlots();
            rebuildControls();
            detectAndSendChanges();
        }
        return copy;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (world.getBlockState(pos).getBlock() != MarketContent.STALL_BLOCK) {
            return false;
        }
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
