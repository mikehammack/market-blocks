package com.maximarcana.marketblocks.menu;

import java.util.ArrayList;
import java.util.List;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketConfig.PriceEntry;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Admin market GUI: infinite buy/sell shop for everyone, plus an
 * operator-only configure mode that edits the config price list live
 * (changes are written back to the config file). Rendered client-side
 * by the vanilla chest screen (GENERIC_9x6).
 */
public class AdminMarketMenu extends AbstractContainerMenu {
    private static final int ENTRY_SLOTS = 45;
    private static final int MODE_SLOT = 45;
    private static final int TARGET_SLOT = 46;
    private static final int HELP_SLOT = 47;
    private static final int PLAYER_INV_START = 54;

    private enum Mode { SHOP, CONFIGURE }
    private enum AdjustTarget { BUY, SELL }

    private final ServerLevel level;
    private final BlockPos pos;
    private final SimpleContainer display = new SimpleContainer(54);
    private final boolean operator;
    private Mode mode = Mode.SHOP;
    private AdjustTarget target = AdjustTarget.BUY;

    public AdminMarketMenu(int containerId, Inventory playerInventory, ServerLevel level, BlockPos pos) {
        super(MenuType.GENERIC_9x6, containerId);
        this.level = level;
        this.pos = pos;
        this.operator = MarketBlocks.isCreative(playerInventory.player);
        for (int i = 0; i < 54; i++) {
            addSlot(MenuUtil.ghost(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        addStandardInventorySlots(playerInventory, 8, 140);
        rebuildDisplay();
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private List<PriceEntry> entries() {
        List<PriceEntry> all = MarketConfig.get().prices;
        return all.subList(0, Math.min(all.size(), ENTRY_SLOTS));
    }

    private static ItemStack entryStack(PriceEntry entry) {
        if (!entry.isTag()) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.key()));
            if (item != null) {
                return new ItemStack(item);
            }
        } else {
            var tag = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM,
                Identifier.parse(entry.key().substring(1))));
            if (tag.isPresent()) {
                var first = tag.get().stream().findFirst();
                if (first.isPresent()) {
                    return new ItemStack(first.get().value());
                }
            }
        }
        return new ItemStack(Items.BARRIER);
    }

    private void rebuildDisplay() {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();

        for (int i = 0; i < ENTRY_SLOTS; i++) {
            if (i >= entries.size()) {
                display.setItem(i, ItemStack.EMPTY);
                continue;
            }
            PriceEntry e = entries.get(i);
            ItemStack shown = entryStack(e);
            boolean unresolved = shown.is(Items.BARRIER);
            shown.set(DataComponents.CUSTOM_NAME,
                Component.literal(e.key()).withStyle(unresolved ? ChatFormatting.RED : ChatFormatting.YELLOW));
            List<Component> lore = new ArrayList<>();
            if (mode == Mode.SHOP) {
                lore.add(Component.literal("Buy: " + MenuUtil.money(e.buy()) + " each")
                    .withStyle(ChatFormatting.GOLD));
                long sell = cfg.resolveSell(shown);
                lore.add(Component.literal(sell >= 0
                    ? "Sell: " + MenuUtil.money(sell) + " each"
                    : "Cannot be sold here").withStyle(ChatFormatting.GREEN));
                lore.add(Component.literal("Left-click: buy 1").withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Right-click: buy a stack").withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Shift-click: sell all from your inventory")
                    .withStyle(ChatFormatting.GRAY));
            } else {
                lore.add(Component.literal("Buy price: " + MenuUtil.money(e.buy()))
                    .withStyle(ChatFormatting.GOLD));
                long sell = cfg.resolveSell(shown);
                lore.add(Component.literal("Sell price: " + MenuUtil.money(sell)
                    + (e.sell() == null ? " (auto)" : " (set)")).withStyle(ChatFormatting.GREEN));
                lore.add(Component.literal("Left-click: +1 " + target).withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Right-click: -1 " + target).withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Shift-click: +/- 10 " + target).withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Drop key (Q): remove entry").withStyle(ChatFormatting.RED));
            }
            shown.set(DataComponents.LORE, new ItemLore(lore));
            display.setItem(i, shown);
        }

        display.setItem(MODE_SLOT, operator
            ? MenuUtil.display(Items.LEVER, "Mode: " + mode, ChatFormatting.AQUA,
                mode == Mode.SHOP
                    ? "Click to configure prices."
                    : "Click to return to the shop.",
                "(Operators only)")
            : MenuUtil.display(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", ChatFormatting.GRAY));
        display.setItem(TARGET_SLOT, mode == Mode.CONFIGURE && operator
            ? MenuUtil.display(Items.REPEATER, "Adjusting: " + target, ChatFormatting.AQUA,
                "Click to switch between",
                "BUY and SELL price editing.")
            : MenuUtil.display(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", ChatFormatting.GRAY));
        display.setItem(HELP_SLOT,
            MenuUtil.display(Items.BOOK, "How it works", ChatFormatting.YELLOW,
                "The server buys and sells here",
                "at fixed prices, forever.",
                "Shift-click any priced item in",
                "your inventory to sell it."));
        ItemStack filler = MenuUtil.display(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", ChatFormatting.GRAY);
        for (int i = HELP_SLOT + 1; i < 54; i++) {
            display.setItem(i, filler);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        boolean isEntry = slotId >= 0 && slotId < ENTRY_SLOTS;
        boolean isPlayerInv = slotId >= PLAYER_INV_START && slotId < this.slots.size();

        if (slotId == MODE_SLOT && clickType == ContainerInput.PICKUP) {
            if (operator) {
                mode = mode == Mode.SHOP ? Mode.CONFIGURE : Mode.SHOP;
                rebuildDisplay();
                broadcastChanges();
            }
            return;
        }
        if (slotId == TARGET_SLOT && clickType == ContainerInput.PICKUP) {
            if (operator && mode == Mode.CONFIGURE) {
                target = target == AdjustTarget.BUY ? AdjustTarget.SELL : AdjustTarget.BUY;
                rebuildDisplay();
                broadcastChanges();
            }
            return;
        }
        if (slotId == HELP_SLOT || (slotId >= HELP_SLOT && slotId < PLAYER_INV_START
            && clickType == ContainerInput.PICKUP)) {
            return; // help / filler
        }

        if (isEntry && mode == Mode.SHOP) {
            if (clickType == ContainerInput.PICKUP) {
                shopBuy(serverPlayer, slotId, button == 0 ? 1 : 64);
            } else if (clickType == ContainerInput.QUICK_MOVE) {
                shopSellAll(serverPlayer, slotId);
            }
            return;
        }
        if (isEntry && mode == Mode.CONFIGURE && operator) {
            if (clickType == ContainerInput.PICKUP) {
                adjustEntry(serverPlayer, slotId, button == 0 ? 1 : -1);
            } else if (clickType == ContainerInput.QUICK_MOVE) {
                adjustEntry(serverPlayer, slotId, button == 0 ? 10 : -10);
            } else if (clickType == ContainerInput.THROW) {
                removeEntry(serverPlayer, slotId);
            }
            return;
        }
        if (isPlayerInv && clickType == ContainerInput.QUICK_MOVE) {
            Slot slot = getSlot(slotId);
            if (slot.hasItem()) {
                if (mode == Mode.CONFIGURE && operator) {
                    addEntry(serverPlayer, slot.getItem());
                } else {
                    shopSellStack(serverPlayer, slot.getItem());
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    // ------------------------------------------------------------------
    // Shop mode: server-authoritative buy/sell
    // ------------------------------------------------------------------

    private void shopBuy(ServerPlayer player, int slot, int wantQty) {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        if (player.isCreative() && !cfg.allowCreativePurchases) {
            MenuUtil.msgActionBar(player, "Creative-mode players cannot buy here.");
            return;
        }
        PriceEntry entry = entries.get(slot);
        ItemStack template = entryStack(entry);
        if (template.is(Items.BARRIER)) {
            MenuUtil.msgActionBar(player, "That entry no longer resolves to an item.");
            return;
        }
        // Re-resolve the live price; never trust anything from the client.
        long buy = cfg.resolveBuy(template);
        if (buy < 0) {
            rebuildDisplay();
            broadcastChanges();
            return;
        }

        MarketEconomy economy = MarketEconomy.get(player.level().getServer());
        long balance = economy.getBalance(player.getUUID());
        int qty = (int) Math.min(wantQty, balance / buy);
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "Not enough " + cfg.currencyName.toLowerCase() + ".");
            return;
        }
        qty = Math.min(qty, MenuUtil.maxFit(player.getInventory(), template));
        if (qty <= 0) {
            MenuUtil.msgActionBar(player, "No room in your inventory.");
            return;
        }
        long total = buy * qty;
        if (economy.getBalance(player.getUUID()) < total
            || cfg.resolveBuy(template) != buy) {
            MenuUtil.msgActionBar(player, "Price changed, try again.");
            rebuildDisplay();
            broadcastChanges();
            return;
        }
        economy.addBalance(player.getUUID(), -total);
        int given = MenuUtil.giveOrDrop(player, template.copyWithCount(qty));
        broadcastChanges();
        MenuUtil.msg(player, "Bought " + given + "x " + template.getHoverName().getString()
            + " for " + MenuUtil.money(total) + ".");
    }

    private void shopSellStack(ServerPlayer player, ItemStack stack) {
        MarketConfig cfg = MarketConfig.get();
        long sell = cfg.resolveSell(stack);
        if (sell < 0) {
            MenuUtil.msgActionBar(player, "The market doesn't buy that.");
            return;
        }
        int removed = MenuUtil.removeMatching(player.getInventory(), stack, stack.getCount());
        if (removed <= 0) {
            return;
        }
        MarketEconomy.get(player.level().getServer()).addBalance(player.getUUID(), sell * removed);
        MenuUtil.msg(player, "Sold " + removed + "x " + stack.getHoverName().getString()
            + " for " + MenuUtil.money(sell * removed) + ".");
        broadcastChanges();
    }

    private void shopSellAll(ServerPlayer player, int slot) {
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        ItemStack template = entryStack(entries.get(slot));
        if (template.is(Items.BARRIER)) {
            return;
        }
        int count = MenuUtil.countMatching(player.getInventory(), template);
        if (count <= 0) {
            MenuUtil.msgActionBar(player, "You don't have any of those.");
            return;
        }
        shopSellStack(player, template.copyWithCount(count));
    }

    // ------------------------------------------------------------------
    // Configure mode: live price editing (operator only)
    // ------------------------------------------------------------------

    private void adjustEntry(ServerPlayer player, int slot, long delta) {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        PriceEntry e = entries.get(slot);
        if (target == AdjustTarget.BUY) {
            long next = Math.max(1, Math.min(10_000_000L, e.buy() + delta));
            cfg.setPrice(e.key().startsWith("#") ? e.key().substring(1) : e.key(), e.isTag(), next,
                e.sell());
        } else {
            ItemStack template = entryStack(e);
            long currentSell = e.sell() != null ? e.sell() : cfg.resolveSell(template);
            long next = Math.max(1, Math.min(10_000_000L, currentSell + delta));
            cfg.setPrice(e.key().startsWith("#") ? e.key().substring(1) : e.key(), e.isTag(), e.buy(),
                next);
        }
        rebuildDisplay();
        broadcastChanges();
        MenuUtil.msgActionBar(player, "Updated " + e.key() + ".");
    }

    private void removeEntry(ServerPlayer player, int slot) {
        MarketConfig cfg = MarketConfig.get();
        List<PriceEntry> entries = entries();
        if (slot >= entries.size()) {
            return;
        }
        PriceEntry e = entries.get(slot);
        cfg.removePrice(e.key().startsWith("#") ? e.key().substring(1) : e.key(), e.isTag());
        rebuildDisplay();
        broadcastChanges();
        MenuUtil.msgActionBar(player, "Removed " + e.key() + ".");
    }

    private void addEntry(ServerPlayer player, ItemStack stack) {
        MarketConfig cfg = MarketConfig.get();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        if (cfg.findEntry(id.toString(), false) != null) {
            MenuUtil.msgActionBar(player, id + " is already priced.");
            return;
        }
        if (cfg.prices.size() >= ENTRY_SLOTS) {
            MenuUtil.msgActionBar(player, "Entry list is full (" + ENTRY_SLOTS + ").");
            return;
        }
        cfg.setPrice(id.toString(), false, 10, null);
        rebuildDisplay();
        broadcastChanges();
        MenuUtil.msg(player, "Added " + id + " at " + MenuUtil.money(10)
            + " (adjust it now, or edit the config file).");
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
        return state.is(MarketContent.ADMIN_MARKET_BLOCK)
            && player.blockPosition().closerThan(pos, 8.0);
    }
}
