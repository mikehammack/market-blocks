package com.maximarcana.marketblocks.menu;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Buyer GUI for a market stall. Slots are display-only; every click is
 * re-validated server-side (price, stock, balance, creative mode) before
 * anything moves. Rendered client-side by the vanilla chest screen (3 rows).
 */
public class StallBuyerContainer extends Container {
    private static final int LISTING_SLOTS = MarketStallTileEntity.STOCK_SLOTS; // 27
    private static final int PLAYER_INV_START = LISTING_SLOTS;

    private final MarketStallTileEntity stall; // null on the client
    private final InventoryBasic display = new InventoryBasic("listings", false, LISTING_SLOTS);
    private final World world;
    private final BlockPos pos;

    /** Server-side constructor. */
    public StallBuyerContainer(InventoryPlayer playerInv, MarketStallTileEntity stall) {
        this.stall = stall;
        this.world = stall.getWorld();
        this.pos = stall.getPos();
        build(playerInv, display);
        if (!world.isRemote) {
            rebuildDisplay();
        }
    }

    /** Client-side constructor (dummy inventories; the server syncs contents). */
    public StallBuyerContainer(InventoryPlayer playerInv, World world, BlockPos pos) {
        this.stall = null;
        this.world = world;
        this.pos = pos;
        build(playerInv, new InventoryBasic("listings", false, LISTING_SLOTS));
    }

    private void build(InventoryPlayer playerInv, IInventory listings) {
        for (int i = 0; i < LISTING_SLOTS; i++) {
            addSlotToContainer(MenuUtil.ghost(listings, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        addPlayerInventory(playerInv, 8, 86);
    }

    private void addPlayerInventory(InventoryPlayer playerInv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new net.minecraft.inventory.Slot(playerInv, col + row * 9 + 9,
                    x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new net.minecraft.inventory.Slot(playerInv, col, x + col * 18, y + 58));
        }
    }

    private void rebuildDisplay() {
        if (stall == null) {
            return;
        }
        MarketConfig cfg = MarketConfig.get();
        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack stock = stall.getStock().getStackInSlot(i);
            Long price = stall.getPrice(i);
            if (stock.isEmpty() || price == null) {
                display.setInventorySlotContents(i, ItemStack.EMPTY);
                continue;
            }
            ItemStack shown = stock.copy();
            shown.setCount(1);
            shown.setStackDisplayName(stock.getDisplayName());
            NBTTagList lore = new NBTTagList();
            lore.appendTag(new NBTTagString(TextFormatting.GOLD + "Price: " + MenuUtil.money(price) + " each"));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Stock: " + stock.getCount()));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left-click: buy 1"));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Right-click: buy a stack"));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Shift-click: buy as many as possible"));
            lore.appendTag(new NBTTagString(TextFormatting.DARK_GRAY + "Seller keeps "
                + MenuUtil.money(Math.round(price * (1.0 - cfg.playerStallTaxPercent / 100.0)))
                + " per item after " + cfg.playerStallTaxPercent + "% tax"));
            shown.getOrCreateSubCompound("display").setTag("Lore", lore);
            display.setInventorySlotContents(i, shown);
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || stall == null) {
            return super.slotClick(slotId, dragType, clickType, player);
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        if (slotId >= 0 && slotId < LISTING_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                // Left = buy 1, right = buy a stack.
                buy(serverPlayer, slotId, dragType == 0 ? 1 : 64);
            } else if (clickType == ClickType.QUICK_MOVE) {
                buy(serverPlayer, slotId, Integer.MAX_VALUE);
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    /**
     * Server-authoritative purchase. Price and stock are re-read from the
     * block entity on every click; the client never supplies them.
     */
    private void buy(EntityPlayerMP player, int slot, int wantQty) {
        MarketConfig cfg = MarketConfig.get();

        if (MarketBlocks.isCreative(player) && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }

        ItemStack stock = stall.getStock().getStackInSlot(slot);
        Long price = stall.getPrice(slot);
        if (stock.isEmpty() || price == null) {
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }

        MarketEconomy economy = MarketEconomy.get(player.getServer());
        long balance = economy.getBalance(player.getUniqueID());

        int qty = Math.min(wantQty, stock.getCount());
        if (price > 0) {
            qty = (int) Math.min(qty, balance / price);
        }
        if (qty <= 0) {
            MenuUtil.msgActionBar(player,
                "Not enough " + cfg.currencyName.toLowerCase() + " (need " + MenuUtil.money(price) + ").");
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }
        qty = Math.min(qty, MenuUtil.maxFit(player.inventory, stock));
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "No room in your inventory.");
            return;
        }

        long total = price * qty;
        // Re-check everything atomically before committing.
        ItemStack fresh = stall.getStock().getStackInSlot(slot);
        Long freshPrice = stall.getPrice(slot);
        if (fresh.isEmpty() || freshPrice == null || !freshPrice.equals(price)
            || fresh.getCount() < qty || economy.getBalance(player.getUniqueID()) < total) {
            rebuildDisplay();
            detectAndSendChanges();
            MenuUtil.msgActionBar(player, "Sale changed, try again.");
            return;
        }

        economy.addBalance(player.getUniqueID(), -total);
        String boughtName = stock.getDisplayName();
        stall.getStock().decrStackSize(slot, qty);
        long tax = Math.round(total * cfg.playerStallTaxPercent / 100.0);
        stall.addEarnings(total - tax);
        stall.clearPricesForEmptySlots();

        ItemStack give = stock.copy();
        give.setCount(qty);
        int given = MenuUtil.giveOrDrop(player, give);

        rebuildDisplay();
        detectAndSendChanges();
        MenuUtil.msg(player, "Bought " + given + "x " + boughtName
            + " for " + MenuUtil.money(total) + ".");
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // Shift-clicking is handled in slotClick(); nothing moves by default.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (world.getBlockState(pos).getBlock() != MarketContent.STALL_BLOCK) {
            return false;
        }
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
