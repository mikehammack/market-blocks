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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Schematic market GUI: players buy a listed schematic and it is pasted at
 * one of 8 build slots around the block; operators configure listings
 * (which files are sold, at what price) live. Rendered client-side by the
 * vanilla chest screen (GENERIC_9x6).
 *
 * Layout: slots 0-35 listings, 36-43 build-slot picker, 44 help,
 * 45 mode lever (operators), 46-53 filler, 54+ player inventory.
 */
public class SchematicMarketMenu extends AbstractContainerMenu {
    private static final int ENTRY_SLOTS = 36;
    private static final int SLOT_PICKER_START = 36;
    private static final int HELP_SLOT = 44;
    private static final int MODE_SLOT = 45;
    private static final int PLAYER_INV_START = 54;

    private static final String[] SLOT_NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final int[] SLOT_DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] SLOT_DZ = {-1, -1, 0, 1, 1, 1, 0, -1};

    private enum Mode { SHOP, CONFIGURE }

    private final ServerLevel level;
    private final BlockPos pos;
    private final SimpleContainer display = new SimpleContainer(54);
    private final boolean operator;
    private Mode mode = Mode.SHOP;
    private int selectedSlot = 0;

    public SchematicMarketMenu(int containerId, Inventory playerInventory, ServerLevel level, BlockPos pos) {
        super(MenuType.GENERIC_9x6, containerId);
        this.level = level;
        this.pos = pos;
        this.operator = MarketBlocks.isCreative(playerInventory.player);
        for (int i = 0; i < 54; i++) {
            addSlot(MenuUtil.ghost(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        // 26.x: addStandardInventorySlots is an AbstractContainerMenu method.
        addStandardInventorySlots(playerInventory, 8, 140);
        rebuildDisplay();
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private static String shortName(String file) {
        String n = file;
        String lower = n.toLowerCase();
        if (lower.endsWith(".schematic")) {
            n = n.substring(0, n.length() - ".schematic".length());
        } else if (lower.endsWith(".schem")) {
            n = n.substring(0, n.length() - ".schem".length());
        }
        return n;
    }

    private static ItemStack entryStack(Schematic schematic, String file, Long price, boolean listed) {
        ItemStack shown = new ItemStack(Items.PAPER);
        shown.set(DataComponents.CUSTOM_NAME,
            Component.literal(shortName(file))
                .withStyle(listed ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        List<Component> lore = new ArrayList<>();
        if (schematic != null) {
            lore.add(Component.literal(schematic.width + " x " + schematic.height + " x "
                + schematic.length + " (" + schematic.solidCount() + " blocks)")
                .withStyle(ChatFormatting.GRAY));
            if (schematic.unknownCount > 0) {
                lore.add(Component.literal(schematic.unknownCount + " blocks can't be mapped")
                    .withStyle(ChatFormatting.RED));
            }
        } else {
            lore.add(Component.literal("File missing or unreadable").withStyle(ChatFormatting.RED));
        }
        if (listed) {
            lore.add(Component.literal("Price: " + MenuUtil.money(price)).withStyle(ChatFormatting.GOLD));
            lore.add(Component.literal("Left-click: buy and build").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("at the selected build slot.").withStyle(ChatFormatting.GRAY));
        } else {
            lore.add(Component.literal("Not listed for sale.").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Left-click: list for sale.").withStyle(ChatFormatting.GRAY));
        }
        shown.set(DataComponents.LORE, new ItemLore(lore));
        return shown;
    }

    private void rebuildDisplay() {
        MarketConfig cfg = MarketConfig.get();
        int radius = cfg.schematicSlotRadius;

        if (mode == Mode.SHOP) {
            List<SchematicEntry> entries = cfg.schematicEntries;
            for (int i = 0; i < ENTRY_SLOTS; i++) {
                if (i >= entries.size()) {
                    display.setItem(i, ItemStack.EMPTY);
                    continue;
                }
                SchematicEntry e = entries.get(i);
                File file = new File(SchematicLibrary.folder(), e.file);
                Schematic schematic = file.isFile() ? SchematicLibrary.parse(file) : null;
                display.setItem(i, entryStack(schematic, e.file, e.price, true));
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
                    display.setItem(i, ItemStack.EMPTY);
                    continue;
                }
                String file = files.get(i);
                Long price = cfg.schematicPrice(file);
                File f = new File(SchematicLibrary.folder(), file);
                Schematic schematic = f.isFile() ? SchematicLibrary.parse(f) : null;
                ItemStack shown = entryStack(schematic, file, price, price != null);
                if (price != null) {
                    List<Component> lore = new ArrayList<>(shown.get(DataComponents.LORE).lines());
                    lore.add(Component.literal("Left/right-click: +/- 1").withStyle(ChatFormatting.GRAY));
                    lore.add(Component.literal("Shift-click: +/- 10").withStyle(ChatFormatting.GRAY));
                    lore.add(Component.literal("Drop key (Q): delist").withStyle(ChatFormatting.RED));
                    shown.set(DataComponents.LORE, new ItemLore(lore));
                }
                display.setItem(i, shown);
            }
        }

        // Build-slot picker.
        for (int s = 0; s < 8; s++) {
            boolean selected = s == selectedSlot;
            int dx = SLOT_DX[s] * radius;
            int dz = SLOT_DZ[s] * radius;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Builds centered " + (dx >= 0 ? "+" : "") + dx + ", "
                + (dz >= 0 ? "+" : "") + dz + " from this block,").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("base at this block's height.").withStyle(ChatFormatting.GRAY));
            if (!selected) {
                lore.add(Component.literal("Click to select.").withStyle(ChatFormatting.GRAY));
            }
            ItemStack picker = MenuUtil.display(
                selected ? Items.STAINED_GLASS_PANE.pick(DyeColor.LIME)
                    : Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY),
                Component.literal("Build slot: " + SLOT_NAMES[s] + (selected ? " (selected)" : ""))
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                lore);
            display.setItem(SLOT_PICKER_START + s, picker);
        }

        display.setItem(HELP_SLOT,
            MenuUtil.display(Items.BOOK, "How it works", ChatFormatting.YELLOW,
                "Buy a schematic and it is pasted",
                "at the selected build slot.",
                "Pick the slot first, then buy.",
                "Schematics come from the server's",
                "schematic folder (see config).",
                ".schematic: vanilla blocks mapped",
                "from 1.12.2 IDs; .schem: full",
                "named palette, mods included."));
        display.setItem(MODE_SLOT, operator
            ? MenuUtil.display(Items.LEVER, "Mode: " + mode, ChatFormatting.AQUA,
                mode == Mode.SHOP
                    ? "Click to configure listings."
                    : "Click to return to the shop.",
                "(Creative mode only)")
            : MenuUtil.display(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", ChatFormatting.GRAY));
        ItemStack filler = MenuUtil.display(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", ChatFormatting.GRAY);
        for (int i = MODE_SLOT + 1; i < 54; i++) {
            display.setItem(i, filler.copy());
        }
    }

    // ------------------------------------------------------------------
    // Interaction (server-authoritative)
    // ------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        if (slotId == MODE_SLOT && clickType == ContainerInput.PICKUP) {
            if (operator) {
                mode = mode == Mode.SHOP ? Mode.CONFIGURE : Mode.SHOP;
                rebuildDisplay();
                broadcastChanges();
            }
            return;
        }
        if (slotId >= SLOT_PICKER_START && slotId < SLOT_PICKER_START + 8
                && clickType == ContainerInput.PICKUP) {
            selectedSlot = slotId - SLOT_PICKER_START;
            rebuildDisplay();
            broadcastChanges();
            MenuUtil.msgActionBar(serverPlayer,
                "Build slot: " + SLOT_NAMES[selectedSlot] + " selected.");
            return;
        }
        if (slotId == HELP_SLOT || (slotId > HELP_SLOT && slotId < PLAYER_INV_START
                && clickType == ContainerInput.PICKUP)) {
            return; // help / filler / lever handled above
        }

        boolean isEntry = slotId >= 0 && slotId < ENTRY_SLOTS;
        if (isEntry && clickType == ContainerInput.PICKUP && mode == Mode.SHOP) {
            shopBuy(serverPlayer, slotId);
            return;
        }
        if (isEntry && operator && mode == Mode.CONFIGURE) {
            configureClick(serverPlayer, slotId, button, clickType);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    // ------------------------------------------------------------------
    // Shop mode: server-authoritative buy + paste
    // ------------------------------------------------------------------

    private void shopBuy(ServerPlayer player, int slot) {
        MarketConfig cfg = MarketConfig.get();
        List<SchematicEntry> entries = cfg.schematicEntries;
        if (slot >= entries.size()) {
            return;
        }
        if (player.isCreative() && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }
        // Re-resolve the live price; never trust anything from the client.
        SchematicEntry entry = entries.get(slot);
        Long livePrice = cfg.schematicPrice(entry.file);
        if (livePrice == null) {
            rebuildDisplay();
            broadcastChanges();
            return;
        }
        File file = new File(SchematicLibrary.folder(), entry.file);
        if (!file.isFile()) {
            MenuUtil.msgActionBar(player, "That schematic's file is missing on the server.");
            rebuildDisplay();
            broadcastChanges();
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

        MarketEconomy economy = MarketEconomy.get(player.level().getServer());
        long balance = economy.getBalance(player.getUUID());
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
            broadcastChanges();
            return;
        }

        int radius = cfg.schematicSlotRadius;
        int anchorX = pos.getX() + SLOT_DX[selectedSlot] * radius;
        int anchorZ = pos.getZ() + SLOT_DZ[selectedSlot] * radius;
        BlockPos minCorner = new BlockPos(
            anchorX - schematic.width / 2, pos.getY(), anchorZ - schematic.length / 2);
        SchematicPaster.Result result = SchematicPaster.paste(level, schematic, minCorner);

        economy.addBalance(player.getUUID(), -livePrice);
        broadcastChanges();
        StringBuilder msg = new StringBuilder("Built ")
            .append(shortName(entry.file))
            .append(" at slot ").append(SLOT_NAMES[selectedSlot])
            .append(": ").append(result.placed).append(" blocks placed");
        if (result.tileEntities > 0) {
            msg.append(", ").append(result.tileEntities).append(" block entities");
        }
        if (result.skippedUnknown > 0) {
            msg.append(", ").append(result.skippedUnknown).append(" unknown blocks skipped");
        }
        if (result.skippedUnloaded > 0) {
            msg.append(", ").append(result.skippedUnloaded).append(" outside loaded area");
        }
        if (schematic.unknownCount > 0) {
            msg.append(" (").append(schematic.unknownCount).append(" unmappable in file)");
        }
        msg.append(" for ").append(MenuUtil.money(livePrice)).append(".");
        MenuUtil.msg(player, msg.toString());
    }

    // ------------------------------------------------------------------
    // Configure mode: live listing edits (operators only)
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

    private void configureClick(ServerPlayer player, int slot, int button, ContainerInput clickType) {
        MarketConfig cfg = MarketConfig.get();
        String file = configureFileAt(slot);
        if (file == null) {
            return;
        }
        Long price = cfg.schematicPrice(file);
        if (clickType == ContainerInput.THROW) {
            if (price != null) {
                MarketConfig.setSchematicPrice(file, null);
                MenuUtil.msgActionBar(player, "Delisted " + shortName(file) + ".");
            }
        } else if (clickType == ContainerInput.PICKUP) {
            if (price == null) {
                MarketConfig.setSchematicPrice(file, cfg.schematicDefaultPrice);
                MenuUtil.msg(player, "Listed " + shortName(file) + " at "
                    + MenuUtil.money(cfg.schematicDefaultPrice) + " (adjust it now).");
            } else {
                long next = Math.max(0, Math.min(10_000_000L,
                    price + (button == 0 ? 1 : -1)));
                MarketConfig.setSchematicPrice(file, next);
                MenuUtil.msgActionBar(player, shortName(file) + " now " + MenuUtil.money(next) + ".");
            }
        } else if (clickType == ContainerInput.QUICK_MOVE) {
            if (price != null) {
                long next = Math.max(0, Math.min(10_000_000L,
                    price + (button == 0 ? 10 : -10)));
                MarketConfig.setSchematicPrice(file, next);
                MenuUtil.msgActionBar(player, shortName(file) + " now " + MenuUtil.money(next) + ".");
            }
        }
        SchematicLibrary.invalidate();
        rebuildDisplay();
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // All shift-click behavior is handled in clicked().
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level.isClientSide()) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(MarketContent.SCHEMATIC_MARKET_BLOCK)
            && player.blockPosition().closerThan(pos, 8.0);
    }
}
