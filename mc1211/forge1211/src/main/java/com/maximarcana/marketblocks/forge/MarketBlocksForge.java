package com.maximarcana.marketblocks.forge;

import com.maximarcana.marketblocks.BreakProtection;
import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.command.MarketCommands;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Standard Forge 1.21.1 entrypoint for Market Blocks v1.0.6.
 *
 * <p>1.21.1 notes vs the 26.x (NeoForge) build:
 * <ul>
 *   <li>No {@code DeferredRegister.Blocks}/{@code .Items} helpers in Forge
 *       1.21.1; the generic {@code DeferredRegister.create} is used.</li>
 *   <li>Break protection is a {@code BlockEvent.BreakEvent} handler instead
 *       of the 26.x mixin.</li>
 *   <li>No item-model registration: Forge 1.21.1 has no
 *       {@code ModelRegistryEvent}/{@code ModelLoader} (removed); item models
 *       in {@code models/item/*.json} resolve automatically, same as Fabric.</li>
 *   <li>Forge 1.21.1 ships Mojang-official names: compile official, ship
 *       official (no reobfuscation step).</li>
 * </ul>
 */
@Mod(MarketBlocks.MOD_ID)
public final class MarketBlocksForge {
    public MarketBlocksForge() {
        // 1.21.1: config only here. Constructing blocks/items now would hit
        // "Registry is already frozen"; content is built lazily by the
        // DeferredRegister suppliers below, during the registry events.
        MarketBlocks.loadConfig(FMLPaths.CONFIGDIR.get().resolve("marketblocks.toml"));

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        DeferredRegister<Block> blocks =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MarketBlocks.MOD_ID);
        DeferredRegister<Item> items =
            DeferredRegister.create(ForgeRegistries.ITEMS, MarketBlocks.MOD_ID);
        DeferredRegister<BlockEntityType<?>> blockEntities =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MarketBlocks.MOD_ID);

        blocks.register("market_stall", MarketContent::createStallBlock);
        blocks.register("admin_market", MarketContent::createAdminMarketBlock);
        blocks.register("schematic_market", MarketContent::createSchematicMarketBlock);
        items.register("market_stall", MarketContent::createStallItem);
        items.register("admin_market", MarketContent::createAdminMarketItem);
        items.register("schematic_market", MarketContent::createSchematicMarketItem);
        // The supplier runs at registration time, after the blocks themselves
        // are registered, so the BlockEntityType can be created safely.
        blockEntities.register("market_stall", MarketContent::createStallBlockEntityType);

        blocks.register(modBus);
        items.register(modBus);
        blockEntities.register(modBus);

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            MarketCommands.register(event.getDispatcher(), event.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MarketEconomy.deliverPending(player);
            }
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath("minecraft", "functional_blocks")))) {
                event.accept(MarketContent::createStallItem);
                event.accept(MarketContent::createAdminMarketItem);
                event.accept(MarketContent::createSchematicMarketItem);
            }
        });

        MarketBlocks.LOGGER.info("Market Blocks Forge entrypoint initialized");
    }

    /** Stall owner/operator break rule + Creative-only admin market break rule. */
    @Mod.EventBusSubscriber(modid = MarketBlocks.MOD_ID)
    public static class BreakEvents {
        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof Level level)) {
                return;
            }
            Component denial = BreakProtection.checkBreak(event.getPlayer(), level, event.getPos());
            if (denial != null) {
                if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(denial, true);
                }
                event.setCanceled(true);
            }
        }
    }
}
