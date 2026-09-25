package com.maximarcana.marketblocks.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketConfig.SchematicEntry;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.economy.MarketEconomy;
import com.maximarcana.marketblocks.schematic.Schematic;
import com.maximarcana.marketblocks.schematic.SchematicLibrary;
import com.maximarcana.marketblocks.schematic.SchematicPaster;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Schematic market GUI: players buy a listed schematic and it is pasted at
 * one of 8 build slots around the block; Creative-mode operators configure
 * listings (which .schematic files are sold, at what price) live.
 * Rendered client-side by the vanilla chest screen (6 rows).
 *
 * Layout: slots 0-35 listings, 36-43 build-slot picker, 44 help,
 * 45 mode lever (Creative), 46-53 filler, 54+ player inventory.
 */
public class SchematicMarketContainer extends Container {
    private static final int ENTRY_SLOTS = 36;
    private static final int SLOT_PICKER_START = 36;
    private static final int HELP_SLOT = 44;
    private static final int MODE_SLOT = 45;
    private static final int PLAYER_INV_START = 54;

    private static final String[] SLOT_NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final int[] SLOT_DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] SLOT_DZ = {-1, -1, 0, 1, 1, 1, 0, -1};

    private enum Mode { SHOP, CONFIGURE }

    private final World world;
    private final BlockPos pos;
    private final InventoryBasic display = new InventoryBasic("schematic_market", false, 54);
    private final boolean operator; // Creative mode, not just op permission
    private Mode mode = Mode.SHOP;
    private int selectedSlot = 0;

    /** Server-side constructor. */
    public SchematicMarketContainer(InventoryPlayer playerInv, EntityPlayerMP player, World world,
            BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.operator = MarketBlocks.isCreative(player);
        build(playerInv);
        if (!world.isRemote) {
            rebuildDisplay();
        }
    }

    /** Client-side constructor (dummy inventories; the server syncs contents). */
    public SchematicMarketContainer(InventoryPlayer playerInv, World world, BlockPos pos,
            boolean operator) {
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

    private static ItemStack pane(EnumDyeColor color) {
        ItemStack pane = new ItemStack(Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 1,
            color.getMetadata());
        pane.setStackDisplayName(TextFormatting.GRAY + " ");
        return pane;
    }

    private static String shortName(String file) {
        String n = file;
        if (n.toLowerCase().endsWith(".schematic")) {
            n = n.substring(0, n.length() - ".schematic".length());
        }
        return n;
    }

    private static ItemStack entryStack(Schematic schematic, String file, Long price, boolean listed) {
        ItemStack shown = new ItemStack(Items.PAPER);
        shown.setStackDisplayName((listed ? TextFormatting.YELLOW : TextFormatting.GRAY)
            + shortName(file));
        NBTTagList lore = new NBTTagList();
        if (schematic != null) {
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "" + schematic.width + " x "
                + schematic.height + " x " + schematic.length + " ("
                + schematic.solidCount() + " blocks)"));
        } else {
            lore.appendTag(new NBTTagString(TextFormatting.RED + "File missing or unreadable"));
        }
        if (listed) {
            lore.appendTag(new NBTTagString(TextFormatting.GOLD + "Price: " + MenuUtil.money(price)));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left-click: buy and build"));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "at the selected build slot."));
        } else {
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Not listed for sale."));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left-click: list for sale."));
        }
        shown.getOrCreateSubCompound("display").setTag("Lore", lore);
        return shown;
    }

    private void rebuildDisplay() {
        MarketConfig cfg = MarketConfig.get();
        int radius = cfg.schematicSlotRadius;

        if (mode == Mode.SHOP) {
            List<SchematicEntry> entries = cfg.schematicEntries;
            for (int i = 0; i < ENTRY_SLOTS; i++) {
                if (i >= entries.size()) {
                    display.setInventorySlotContents(i, ItemStack.EMPTY);
                    continue;
                }
                SchematicEntry e = entries.get(i);
                File file = new File(SchematicLibrary.folder(), e.file);
                Schematic schematic = file.isFile() ? SchematicLibrary.parse(file) : null;
                display.setInventorySlotContents(i,
                    entryStack(schematic, e.file, e.price, true));
            }
        } else {
            // Configure mode: files on disk first, then stale listings.
            List<String> files = new ArrayList<>();
            for (File f : SchematicLibrary.scanFiles()) {
                files.add(f.getName());
            }
            for (SchematicEntry e : cfg.schematicEntries) {
                boolean present = false;
                for (String f : files) {
                    if (f.equalsIgnoreCase(e.file)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    files.add(e.file);
                }
            }
            for (int i = 0; i < ENTRY_SLOTS; i++) {
                if (i >= files.size()) {
                    display.setInventorySlotContents(i, ItemStack.EMPTY);
                    continue;
                }
                String file = files.get(i);
                Long price = cfg.schematicPrice(file);
                File f = new File(SchematicLibrary.folder(), file);
                Schematic schematic = f.isFile() ? SchematicLibrary.parse(f) : null;
                ItemStack shown = entryStack(schematic, file, price, price != null);
                if (price != null) {
                    NBTTagList lore = shown.getOrCreateSubCompound("display")
                        .getTagList("Lore", 8);
                    lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Left/right-click: +/- 1"));
                    lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Shift-click: +/- 10"));
                    lore.appendTag(new NBTTagString(TextFormatting.RED + "Drop key (Q): delist"));
                }
                display.setInventorySlotContents(i, shown);
            }
        }

        // Build-slot picker.
        for (int s = 0; s < 8; s++) {
            boolean selected = s == selectedSlot;
            ItemStack picker = pane(selected ? EnumDyeColor.LIME : EnumDyeColor.GRAY);
            picker.setStackDisplayName((selected ? TextFormatting.GREEN : TextFormatting.YELLOW)
                + "Build slot: " + SLOT_NAMES[s] + (selected ? " (selected)" : ""));
            NBTTagList lore = new NBTTagList();
            int dx = SLOT_DX[s] * radius;
            int dz = SLOT_DZ[s] * radius;
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Builds centered "
                + (dx >= 0 ? "+" : "") + dx + ", " + (dz >= 0 ? "+" : "") + dz
                + " from this block,"));
            lore.appendTag(new NBTTagString(TextFormatting.GRAY + "base at this block's height."));
            if (!selected) {
                lore.appendTag(new NBTTagString(TextFormatting.GRAY + "Click to select."));
            }
            picker.getOrCreateSubCompound("display").setTag("Lore", lore);
            display.setInventorySlotContents(SLOT_PICKER_START + s, picker);
        }

        display.setInventorySlotContents(HELP_SLOT,
            MenuUtil.display(Items.BOOK, "How it works", TextFormatting.YELLOW,
                "Buy a schematic and it is pasted",
                "at the selected build slot.",
                "Pick the slot first, then buy.",
                "Schematics come from the server's",
                "schematic folder (see config).",
                "Block ids are this server's own,",
                "so designs paste faithfully here."));
        display.setInventorySlotContents(MODE_SLOT, operator
            ? MenuUtil.display(Item.getItemFromBlock(Blocks.LEVER), "Mode: " + mode,
                TextFormatting.AQUA,
                mode == Mode.SHOP
                    ? "Click to configure listings."
                    : "Click to return to the shop.",
                "(Creative mode only)")
            : pane(EnumDyeColor.GRAY));
        ItemStack filler = pane(EnumDyeColor.GRAY);
        for (int i = MODE_SLOT + 1; i < 54; i++) {
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

        if (slotId == MODE_SLOT && clickType == ClickType.PICKUP) {
            if (operator) {
                mode = mode == Mode.SHOP ? Mode.CONFIGURE : Mode.SHOP;
                rebuildDisplay();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }
        if (slotId >= SLOT_PICKER_START && slotId < SLOT_PICKER_START + 8
                && clickType == ClickType.PICKUP) {
            selectedSlot = slotId - SLOT_PICKER_START;
            rebuildDisplay();
            detectAndSendChanges();
            MenuUtil.msgActionBar(serverPlayer,
                "Build slot: " + SLOT_NAMES[selectedSlot] + " selected.");
            return ItemStack.EMPTY;
        }
        if (slotId == HELP_SLOT || (slotId > HELP_SLOT && slotId < PLAYER_INV_START
                && clickType == ClickType.PICKUP)) {
            return ItemStack.EMPTY; // help / filler / lever handled above
        }

        boolean isEntry = slotId >= 0 && slotId < ENTRY_SLOTS;
        if (isEntry && clickType == ClickType.PICKUP && mode == Mode.SHOP) {
            shopBuy(serverPlayer, slotId);
            return ItemStack.EMPTY;
        }
        if (isEntry && operator && mode == Mode.CONFIGURE) {
            configureClick(serverPlayer, slotId, dragType, clickType);
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    // ------------------------------------------------------------------
    // Shop mode: server-authoritative buy + paste
    // ------------------------------------------------------------------

    private void shopBuy(EntityPlayerMP player, int slot) {
        MarketConfig cfg = MarketConfig.get();
        List<SchematicEntry> entries = cfg.schematicEntries;
        if (slot >= entries.size()) {
            return;
        }
        if (MarketBlocks.isCreative(player) && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }
        // Re-resolve the live price; never trust anything from the client.
        SchematicEntry entry = entries.get(slot);
        Long livePrice = cfg.schematicPrice(entry.file);
        if (livePrice == null) {
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }
        File file = new File(SchematicLibrary.folder(), entry.file);
        if (!file.isFile()) {
            MenuUtil.msgActionBar(player, "That schematic's file is missing on the server.");
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }
        Schematic schematic = SchematicLibrary.parse(file);
        if (schematic == null) {
            MenuUtil.msgActionBar(player,
                "That schematic is unreadable: " + SchematicLibrary.lastError(file));
            return;
        }
        if (schematic.volume() > cfg.schematicMaxVolume) {
            MenuUtil.msgActionBar(player, "That schematic is too large to paste here.");
            return;
        }

        MarketEconomy economy = MarketEconomy.get(player.getServer());
        long balance = economy.getBalance(player.getUniqueID());
        if (balance < livePrice) {
            MenuUtil.msgActionBar(player,
                "Not enough " + cfg.currencyName.toLowerCase() + " (need "
                    + MenuUtil.money(livePrice) + ").");
            return;
        }
        // Re-check price after the balance read; the listing may have changed.
        if (!livePrice.equals(cfg.schematicPrice(entry.file))) {
            MenuUtil.msgActionBar(player, "Price changed, try again.");
            rebuildDisplay();
            detectAndSendChanges();
            return;
        }

        int radius = cfg.schematicSlotRadius;
        int anchorX = pos.getX() + SLOT_DX[selectedSlot] * radius;
        int anchorZ = pos.getZ() + SLOT_DZ[selectedSlot] * radius;
        BlockPos minCorner = new BlockPos(
            anchorX - schematic.width / 2, pos.getY(), anchorZ - schematic.length / 2);
        SchematicPaster.Result result = SchematicPaster.paste(world, schematic, minCorner);

        economy.addBalance(player.getUniqueID(), -livePrice);
        detectAndSendChanges();
        StringBuilder msg = new StringBuilder("Built ")
            .append(shortName(entry.file))
            .append(" at slot ").append(SLOT_NAMES[selectedSlot])
            .append(": ").append(result.placed).append(" blocks placed");
        if (result.tileEntities > 0) {
            msg.append(", ").append(result.tileEntities).append(" tile entities");
        }
        if (result.skippedUnknown > 0) {
            msg.append(", ").append(result.skippedUnknown).append(" unknown blocks skipped");
        }
        if (result.skippedUnloaded > 0) {
            msg.append(", ").append(result.skippedUnloaded).append(" outside loaded area");
        }
        msg.append(" for ").append(MenuUtil.money(livePrice)).append(".");
        MenuUtil.msg(player, msg.toString());
    }

    // ------------------------------------------------------------------
    // Configure mode: live listing edits (Creative mode only)
    // ------------------------------------------------------------------

    /** File shown at an entry slot in configure mode (disk files, then stale listings). */
    private String configureFileAt(int slot) {
        MarketConfig cfg = MarketConfig.get();
        List<String> files = new ArrayList<>();
        for (File f : SchematicLibrary.scanFiles()) {
            files.add(f.getName());
        }
        for (SchematicEntry e : cfg.schematicEntries) {
            boolean present = false;
            for (String f : files) {
                if (f.equalsIgnoreCase(e.file)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                files.add(e.file);
            }
        }
        return slot < files.size() ? files.get(slot) : null;
    }

    private void configureClick(EntityPlayerMP player, int slot, int dragType, ClickType clickType) {
        MarketConfig cfg = MarketConfig.get();
        String file = configureFileAt(slot);
        if (file == null) {
            return;
        }
        Long price = cfg.schematicPrice(file);
        if (clickType == ClickType.THROW) {
            if (price != null) {
                MarketConfig.setSchematicPrice(file, null);
                MenuUtil.msgActionBar(player, "Delisted " + shortName(file) + ".");
            }
        } else if (clickType == ClickType.PICKUP) {
            if (price == null) {
                MarketConfig.setSchematicPrice(file, cfg.schematicDefaultPrice);
                MenuUtil.msg(player, "Listed " + shortName(file) + " at "
                    + MenuUtil.money(cfg.schematicDefaultPrice) + " (adjust it now).");
            } else {
                long next = Math.max(0, Math.min(10_000_000L,
                    price + (dragType == 0 ? 1 : -1)));
                MarketConfig.setSchematicPrice(file, next);
                MenuUtil.msgActionBar(player, shortName(file) + " now " + MenuUtil.money(next) + ".");
            }
        } else if (clickType == ClickType.QUICK_MOVE) {
            if (price != null) {
                long next = Math.max(0, Math.min(10_000_000L,
                    price + (dragType == 0 ? 10 : -10)));
                MarketConfig.setSchematicPrice(file, next);
                MenuUtil.msgActionBar(player, shortName(file) + " now " + MenuUtil.money(next) + ".");
            }
        }
        SchematicLibrary.invalidate();
        rebuildDisplay();
        detectAndSendChanges();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // All shift-click behavior is handled in slotClick().
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (world.getBlockState(pos).getBlock() != MarketContent.SCHEMATIC_MARKET_BLOCK) {
            return false;
        }
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
