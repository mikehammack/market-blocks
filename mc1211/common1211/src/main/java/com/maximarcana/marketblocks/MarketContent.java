package com.maximarcana.marketblocks;

import java.util.Set;

import com.maximarcana.marketblocks.block.AdminMarketBlock;
import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.block.SchematicMarketBlock;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral content holders. Both loaders register these same instances;
 * Fabric registers them directly, Forge via DeferredRegister suppliers.
 *
 * <p>Unlike the 26.x build, 1.21.1 needs no {@code setId(...)} on
 * Properties: ids come from the registry name at registration time.
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
        createStallBlock();
        createAdminMarketBlock();
        createSchematicMarketBlock();
        createStallItem();
        createAdminMarketItem();
        createSchematicMarketItem();
        // NOTE: the block entity type is NOT created here. BlockEntityType's
        // constructor calls builtInRegistryHolder() on its valid blocks, which
        // requires them to be registered already. Loaders must call
        // createStallBlockEntityType() after registering the blocks.
    }

    /**
     * Lazy factories for the Forge 1.21.1 path: DeferredRegister suppliers
     * run during the registry events (registries unfrozen), so construction
     * happens there instead of in the mod constructor (where it crashes with
     * "Registry is already frozen"). Idempotent: repeat calls return the
     * existing instance, so registration order between blocks/items does not
     * matter and the Fabric eager path is unaffected.
     */
    public static MarketStallBlock createStallBlock() {
        if (STALL_BLOCK == null) {
            STALL_BLOCK = new MarketStallBlock();
        }
        return STALL_BLOCK;
    }

    public static AdminMarketBlock createAdminMarketBlock() {
        if (ADMIN_MARKET_BLOCK == null) {
            ADMIN_MARKET_BLOCK = new AdminMarketBlock();
        }
        return ADMIN_MARKET_BLOCK;
    }

    public static SchematicMarketBlock createSchematicMarketBlock() {
        if (SCHEMATIC_MARKET_BLOCK == null) {
            SCHEMATIC_MARKET_BLOCK = new SchematicMarketBlock();
        }
        return SCHEMATIC_MARKET_BLOCK;
    }

    public static BlockItem createStallItem() {
        if (STALL_ITEM == null) {
            STALL_ITEM = new BlockItem(createStallBlock(), new Item.Properties());
        }
        return STALL_ITEM;
    }

    public static BlockItem createAdminMarketItem() {
        if (ADMIN_MARKET_ITEM == null) {
            ADMIN_MARKET_ITEM = new BlockItem(createAdminMarketBlock(), new Item.Properties());
        }
        return ADMIN_MARKET_ITEM;
    }

    public static BlockItem createSchematicMarketItem() {
        if (SCHEMATIC_MARKET_ITEM == null) {
            SCHEMATIC_MARKET_ITEM = new BlockItem(createSchematicMarketBlock(), new Item.Properties());
        }
        return SCHEMATIC_MARKET_ITEM;
    }

    /**
     * Creates the stall block entity type. Call only after the blocks have
     * been registered (see note on {@link #create()}).
     *
     * <p>1.21.1 note: {@code BlockEntityType.BlockEntitySupplier} is
     * package-private, so the Builder cannot be fed a lambda from mod code
     * (javac rejects it). Instead we subclass and override the public
     * {@code create} method; the supplier constructor argument is unused.
     */
    public static BlockEntityType<MarketStallBlockEntity> createStallBlockEntityType() {
        STALL_BLOCK_ENTITY_TYPE = new BlockEntityType<MarketStallBlockEntity>(
            null, Set.of(createStallBlock()), null) {
            @Override
            public MarketStallBlockEntity create(BlockPos pos, BlockState state) {
                return new MarketStallBlockEntity(pos, state);
            }
        };
        return STALL_BLOCK_ENTITY_TYPE;
    }
}
