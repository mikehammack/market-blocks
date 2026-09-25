package com.maximarcana.marketblocks;

import java.util.Set;

import com.maximarcana.marketblocks.block.AdminMarketBlock;
import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.block.SchematicMarketBlock;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Loader-neutral content holders. Both loaders register these same instances;
 * Fabric registers them directly, NeoForge via DeferredRegister suppliers.
 *
 * <p>MC 26.x requires the registry id to be stamped on {@code Properties}
 * BEFORE a Block/Item is constructed ({@code BlockBehaviour} derives the
 * default loot table from the id in its constructor), so each id is defined
 * here and applied in the block/item constructors.
 */
public final class MarketContent {
    public static final ResourceKey<Block> STALL_BLOCK_ID =
        ResourceKey.create(Registries.BLOCK, MarketBlocks.id("market_stall"));
    public static final ResourceKey<Block> ADMIN_MARKET_BLOCK_ID =
        ResourceKey.create(Registries.BLOCK, MarketBlocks.id("admin_market"));
    public static final ResourceKey<Block> SCHEMATIC_MARKET_BLOCK_ID =
        ResourceKey.create(Registries.BLOCK, MarketBlocks.id("schematic_market"));
    public static final ResourceKey<Item> STALL_ITEM_ID =
        ResourceKey.create(Registries.ITEM, MarketBlocks.id("market_stall"));
    public static final ResourceKey<Item> ADMIN_MARKET_ITEM_ID =
        ResourceKey.create(Registries.ITEM, MarketBlocks.id("admin_market"));
    public static final ResourceKey<Item> SCHEMATIC_MARKET_ITEM_ID =
        ResourceKey.create(Registries.ITEM, MarketBlocks.id("schematic_market"));

    public static MarketStallBlock STALL_BLOCK;
    public static AdminMarketBlock ADMIN_MARKET_BLOCK;
    public static SchematicMarketBlock SCHEMATIC_MARKET_BLOCK;
    public static BlockItem STALL_ITEM;
    public static BlockItem ADMIN_MARKET_ITEM;
    public static BlockItem SCHEMATIC_MARKET_ITEM;
    public static BlockEntityType<MarketStallBlockEntity> STALL_BLOCK_ENTITY_TYPE;

    private MarketContent() {
    }

    public static void create() {
        STALL_BLOCK = new MarketStallBlock();
        ADMIN_MARKET_BLOCK = new AdminMarketBlock();
        SCHEMATIC_MARKET_BLOCK = new SchematicMarketBlock();
        STALL_ITEM = new BlockItem(STALL_BLOCK, new Item.Properties().setId(STALL_ITEM_ID));
        ADMIN_MARKET_ITEM = new BlockItem(ADMIN_MARKET_BLOCK, new Item.Properties().setId(ADMIN_MARKET_ITEM_ID));
        SCHEMATIC_MARKET_ITEM = new BlockItem(SCHEMATIC_MARKET_BLOCK,
            new Item.Properties().setId(SCHEMATIC_MARKET_ITEM_ID));
        // NOTE: the block entity type is NOT created here. BlockEntityType's
        // constructor calls builtInRegistryHolder() on its valid blocks, which
        // requires them to be registered already. Loaders must call
        // createStallBlockEntityType() after registering the blocks.
    }

    /**
     * Creates the stall block entity type. Call only after the blocks have
     * been registered (see note on {@link #create()}).
     */
    public static BlockEntityType<MarketStallBlockEntity> createStallBlockEntityType() {
        STALL_BLOCK_ENTITY_TYPE = new BlockEntityType<>(MarketStallBlockEntity::new, Set.of(STALL_BLOCK));
        return STALL_BLOCK_ENTITY_TYPE;
    }
}
