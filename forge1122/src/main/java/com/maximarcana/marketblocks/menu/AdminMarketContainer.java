package com.maximarcana.marketblocks.menu;

import java.util.List;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketConfig.PriceEntry;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Admin market GUI: infinite buy/sell shop for everyone, plus a
 * Creative-mode-only configure mode that edits the config price list live
 * (changes are written back to the config file). Rendered client-side by
 * the vanilla chest screen (6 rows).
 */
public class AdminMarketContainer extends Container {
    private static final int ENTRY_SLOTS = 45;
    private static final int MODE_SLOT = 45;
    private static final int TARGET_SLOT = 46;
    private static final int HELP_SLOT = 47;
    private static final int PLAYER_INV_START = 54;

    private enum Mode { SHOP, CONFIGURE }
    private enum AdjustTarget { BUY, SELL }

    private final World world;
    private final BlockPos pos;
    private final InventoryBasic display = new InventoryBasic("admin_market", false, 54);
    private final boolean operator; // Creative mode, not just op permission
    private Mode mode = Mode.SHOP;
    private AdjustTarget target = AdjustTarget.BUY;

    /** Server-side constructor. */
    public AdminMarketContainer(InventoryPlayer playerInv, EntityPlayerMP player, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.operator = MarketBlocks.isCreative(player);
        build(playerInv);
        if (!world.isRemote) {
            rebuildDisplay();
        }
    }

    /** Client-side constructor (dummy inventories; the server syncs contents). */
    public AdminMarketContainer(InventoryPlayer playerInv, World world, BlockPos pos, boolean operator) {
        this.world = world;
        this.pos = pos;
        this.operator = operator;
        build(playerInv);
    }

    private void build(InventoryPlayer playerInv) {
        for (int i = 0; i < 54; i++) {
            addSlotToContainer(MenuUtil.ghost(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private List<PriceEntry> entries() {
        List<PriceEntry> all = MarketConfig.get().entries();
        return all.subList(0, Math.min(all.size(), ENTRY_SLOTS));
    }

    private static ItemStack entryStack(PriceEntry entry) {
        if (!entry.isOreDict) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.key));
            if (item != null) {
                return new ItemStack(item);
            }
        } else {
            NonNullList<ItemStack> ores = OreDictionary.getOres(entry.key.substring(1));
            if (!ores.isEmpty() && !ores.get(0).isEmpty()) {
                return ores.get(0).copy();
            }
        }
        return new ItemStack(barrierItem());
    }

    /** 1.12.2 has no Items.BARRIER field; resolve the barrier ItemBlock directly. */
    private static Item barrierItem() {
        return Item.getItemFromBlock(Blocks.BARRIER);
    }

    private static ItemStack grayPane() {
        ItemStack pane = new ItemStack(Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 1,
            EnumDyeColor.GRAY.getMetadata());
        pane.setStackDisplayName(TextFormatting.GRAY + " ");
        return pane;
    }

    private void rebuildDisplay() {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();

        for (int i = 0; i < ENTRY_SLOTS; i++) {
            if (i >= entries.size()) {
                display.setInventorySlotContents(i, ItemStack.EMPTY);
                continue;
            }
            PriceEntry e = entries.get(i);
            ItemStack shown = entryStack(e);
            boolean unresolved = shown.getItem() == barrierItem();
            shown.setStackDisplayName(
                (unresolved ? TextFormatting.RED : TextFormatting.YELLOW) + e.key);
            NBTTagList lore = new NBTTagList();
            if (mode == Mode.SHOP) {
                lore.appendTag(new NBTTagString(TextFormatting.GOLD + "Buy: " + MenuUtil.money(e.buy) + " each"));
                long sell = cfg.resolveSell(shown);
                lore.appendTag(new NBTTagString(TextFormatting.GREEN + (sell >= 0
                    ? "Sell: " + MenuUtil.money(sell) + " each"
                    : "Cannot be sold here")));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left-click: buy 1"));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Right-click: buy a stack"));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Shift-click: sell all from your inventory"));
            } else {
                lore.appendTag(new NBTTagString(TextFormatting.GOLD + "Buy price: " + MenuUtil.money(e.buy)));
                long sell = cfg.resolveSell(shown);
                lore.appendTag(new NBTTagString(TextFormatting.GREEN + "Sell price: " + MenuUtil.money(sell)
                    + (e.sell == null ? " (auto)" : " (set)")));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left-click: +1 " + target));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Right-click: -1 " + target));
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Shift-click: +/- 10 " + target));
                lore.appendTag(new NBTTagString(TextFormatting.RED + "Drop key (Q): remove entry"));
            }
            shown.getOrCreateSubCompound("display").setTag("Lore", lore);
            display.setInventorySlotContents(i, shown);
        }

        display.setInventorySlotContents(MODE_SLOT, operator
            ? MenuUtil.display(Item.getItemFromBlock(Blocks.LEVER), "Mode: " + mode, TextFormatting.AQUA,
                mode == Mode.SHOP
                    ? "Click to configure prices."
                    : "Click to return to the shop.",
                "(Creative mode only)")
            : grayPane());
        display.setInventorySlotContents(TARGET_SLOT, mode == Mode.CONFIGURE && operator
            ? MenuUtil.display(Items.REPEATER, "Adjusting: " + target, TextFormatting.AQUA,
                "Click to switch between",
                "BUY and SELL price editing.")
            : grayPane());
        display.setInventorySlotContents(HELP_SLOT,
            MenuUtil.display(Items.BOOK, "How it works", TextFormatting.YELLOW,
                "The server buys and sells here",
                "at fixed prices, forever.",
                "Shift-click any priced item in",
                "your inventory to sell it."));
        ItemStack filler = grayPane();
        for (int i = HELP_SLOT + 1; i < 54; i++) {
            display.setInventorySlotContents(i, filler.copy());
        }
    }

    // ------------------------------------------------------------------
    // Interaction (server-authoritative)
    // ------------------------------------------------------------------

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return super.slotClick(slotId, dragType, clickType, player);
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        boolean isEntry = slotId >= 0 && slotId < ENTRY_SLOTS;
        boolean isPlayerInv = slotId >= PLAYER_INV_START && slotId < this.inventorySlots.size();

        if (slotId == MODE_SLOT && clickType == ClickType.PICKUP) {
            if (operator) {
                mode = mode == Mode.SHOP ? Mode.CONFIGURE : Mode.SHOP;
                rebuildDisplay();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }
        if (slotId == TARGET_SLOT && clickType == ClickType.PICKUP) {
            if (operator && mode == Mode.CONFIGURE) {
                target = target == AdjustTarget.BUY ? AdjustTarget.SELL : AdjustTarget.BUY;
                rebuildDisplay();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }
        if (slotId == HELP_SLOT || (slotId > HELP_SLOT && slotId < PLAYER_INV_START
            && clickType == ClickType.PICKUP)) {
            return ItemStack.EMPTY; // help / filler
        }

        if (isEntry && mode == Mode.SHOP) {
            if (clickType == ClickType.PICKUP) {
                shopBuy(serverPlayer, slotId, dragType == 0 ? 1 : 64);
            } else if (clickType == ClickType.QUICK_MOVE) {
                shopSellAll(serverPlayer, slotId);
            }
            return ItemStack.EMPTY;
        }
        if (isEntry && mode == Mode.CONFIGURE && operator) {
            if (clickType == ClickType.PICKUP) {
                adjustEntry(serverPlayer, slotId, dragType == 0 ? 1 : -1);
            } else if (clickType == ClickType.QUICK_MOVE) {
                adjustEntry(serverPlayer, slotId, dragType == 0 ? 10 : -10);
            } else if (clickType == ClickType.THROW) {
                removeEntry(serverPlayer, slotId);
            }
            return ItemStack.EMPTY;
        }
        if (isPlayerInv && clickType == ClickType.QUICK_MOVE) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.getHasStack()) {
                if (mode == Mode.CONFIGURE && operator) {
                    addEntry(serverPlayer, slot.getStack());
                } else {
                    shopSellStack(serverPlayer, slot.getStack());
                }
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    // ------------------------------------------------------------------
    // Shop mode: server-authoritative buy/sell
    // ------------------------------------------------------------------

    private void shopBuy(EntityPlayerMP player, int slot, int wantQty) {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        if (MarketBlocks.isCreative(player) && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }
        PriceEntry entry = entries.get(slot);
        ItemStack template = entryStack(entry);
        if (template.getItem() == barrierItem()) {
            MenuUtil.msgActionBar(player, "That entry no longer resolves to an item.");
            return;
        }
        // Re-resolve the live price; never trust anything from the client.
        long buy = cfg.resolveBuy(template);
        if (buy < 0) {
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }

        MarketEconomy economy = MarketEconomy.get(player.getServer());
        long balance = economy.getBalance(player.getUniqueID());
        int qty = (int) Math.min(wantQty, balance / buy);
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "Not enough " + cfg.currencyName.toLowerCase() + ".");
            return;
        }
        qty = Math.min(qty, MenuUtil.maxFit(player.inventory, template));
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "No room in your inventory.");
            return;
        }
        long total = buy * qty;
        if (economy.getBalance(player.getUniqueID()) < total
            || cfg.resolveBuy(template) != buy) {
            MenuUtil.msgActionBar(player, "Price changed, try again.");
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }
        economy.addBalance(player.getUniqueID(), -total);
        ItemStack give = template.copy();
        give.setCount(qty);
        int given = MenuUtil.giveOrDrop(player, give);
        detectAndSendChanges();
        MenuUtil.msg(player, "Bought " + given + "x " + template.getDisplayName()
            + " for " + MenuUtil.money(total) + ".");
    }

    private void shopSellStack(EntityPlayerMP player, ItemStack stack) {
        MarketConfig cfg = MarketConfig.get();
        long sell = cfg.resolveSell(stack);
        if (sell < 0) {
            MenuUtil.msgActionBar(player, "The market doesn't buy that.");
            return;
        }
        int removed = MenuUtil.removeMatching(player.inventory, stack, stack.getCount());
        if (removed <= 0) {
            return;
        }
        MarketEconomy.get(player.getServer()).addBalance(player.getUniqueID(), sell * removed);
        MenuUtil.msg(player, "Sold " + removed + "x " + stack.getDisplayName()
            + " for " + MenuUtil.money(sell * removed) + ".");
        detectAndSendChanges();
    }

    private void shopSellAll(EntityPlayerMP player, int slot) {
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        ItemStack template = entryStack(entries.get(slot));
        if (template.getItem() == barrierItem()) {
            return;
        }
        int count = MenuUtil.countMatching(player.inventory, template);
        if (count <= 0) {
            MenuUtil.msgActionBar(player, "You don't have any of those.");
            return;
        }
        ItemStack sell = template.copy();
        sell.setCount(count);
        shopSellStack(player, sell);
    }

    // ------------------------------------------------------------------
    // Configure mode: live price editing (Creative mode only)
    // ------------------------------------------------------------------

    private void adjustEntry(EntityPlayerMP player, int slot, long delta) {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        PriceEntry e = entries.get(slot);
        if (target == AdjustTarget.BUY) {
            long next = Math.max(1, Math.min(10_000_000L, e.buy + delta));
            MarketConfig.setPrice(e.key, next, e.sell);
        } else {
            ItemStack template = entryStack(e);
            long currentSell = e.sell != null ? e.sell : cfg.resolveSell(template);
            long next = Math.max(1, Math.min(10_000_000L, currentSell + delta));
            MarketConfig.setPrice(e.key, e.buy, next);
        }
        rebuildDisplay();
        detectAndSendChanges();
        MenuUtil.msgActionBar(player, "Updated " + e.key + ".");
    }

    private void removeEntry(EntityPlayerMP player, int slot) {
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        PriceEntry e = entries.get(slot);
        MarketConfig.setPrice(e.key, null, null);
        rebuildDisplay();
        detectAndSendChanges();
        MenuUtil.msgActionBar(player, "Removed " + e.key + ".");
    }

    private void addEntry(EntityPlayerMP player, ItemStack stack) {
        MarketConfig cfg = MarketConfig.get();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String key = id.toString();
        for (PriceEntry e : cfg.entries()) {
            if (e.key.equals(key)) {
                MenuUtil.msgActionBar(player, key + " is already priced.");
                return;
            }
        }
        if (cfg.entries().size() >= ENTRY_SLOTS) {
            MenuUtil.msgActionBar(player, "Entry list is full (" + ENTRY_SLOTS + ").");
            return;
        }
        MarketConfig.setPrice(key, 10L, null);
        rebuildDisplay();
        detectAndSendChanges();
        MenuUtil.msg(player, "Added " + key + " at " + MenuUtil.money(10)
            + " (adjust it now, or edit the config file).");
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // All shift-click behavior is handled in slotClick().
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (world.getBlockState(pos).getBlock() != MarketContent.ADMIN_MARKET_BLOCK) {
            return false;
        }
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
