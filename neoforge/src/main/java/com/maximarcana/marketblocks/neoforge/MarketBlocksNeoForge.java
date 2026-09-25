package com.maximarcana.marketblocks.neoforge;

import com.maximarcana.marketblocks.BreakProtection;
import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.command.MarketCommands;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MarketBlocks.MOD_ID)
public final class MarketBlocksNeoForge {
    public MarketBlocksNeoForge() {
        MarketBlocks.init(FMLPaths.CONFIGDIR.get().resolve("marketblocks.toml"));

        IEventBus modBus = ModList.get().getModContainerById(MarketBlocks.MOD_ID)
            .orElseThrow().getEventBus();

        DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(MarketBlocks.MOD_ID);
        DeferredRegister.Items items = DeferredRegister.createItems(MarketBlocks.MOD_ID);
        DeferredRegister<BlockEntityType<?>> blockEntities =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MarketBlocks.MOD_ID);

        blocks.register("market_stall", () -> MarketContent.STALL_BLOCK);
        blocks.register("admin_market", () -> MarketContent.ADMIN_MARKET_BLOCK);
        blocks.register("schematic_market", () -> MarketContent.SCHEMATIC_MARKET_BLOCK);
        items.register("market_stall", () -> MarketContent.STALL_ITEM);
        items.register("admin_market", () -> MarketContent.ADMIN_MARKET_ITEM);
        items.register("schematic_market", () -> MarketContent.SCHEMATIC_MARKET_ITEM);
        // The supplier runs at registration time, after the blocks themselves
        // are registered, so the BlockEntityType can resolve intrusive holders.
        blockEntities.register("market_stall", MarketContent::createStallBlockEntityType);

        blocks.register(modBus);
        items.register(modBus);
        blockEntities.register(modBus);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            MarketCommands.register(event.getDispatcher(), event.getBuildContext()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MarketEconomy.deliverPending(player);
            }
        });
        // Break protection via event (replaces the old StallBreakProtectionMixin).
        // NeoForge 26.x moved the break event to event.level.block.BreakBlockEvent.
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
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
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("functional_blocks")))) {
                event.accept(MarketContent.STALL_ITEM);
                event.accept(MarketContent.ADMIN_MARKET_ITEM);
                event.accept(MarketContent.SCHEMATIC_MARKET_ITEM);
            }
        });

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            MarketBlocks.LOGGER.info("Market Blocks client running (vanilla chest screens used)");
        }
        MarketBlocks.LOGGER.info("Market Blocks NeoForge entrypoint initialized");
    }
}
