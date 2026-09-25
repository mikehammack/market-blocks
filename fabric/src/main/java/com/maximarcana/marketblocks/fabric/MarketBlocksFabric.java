package com.maximarcana.marketblocks.fabric;


import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.BreakProtection;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.command.MarketCommands;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;

public final class MarketBlocksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MarketBlocks.init(
            FabricLoader.getInstance().getConfigDir().resolve("marketblocks.toml"));

        Registry.register(BuiltInRegistries.BLOCK, MarketBlocks.id("market_stall"),
            MarketContent.STALL_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, MarketBlocks.id("admin_market"),
            MarketContent.ADMIN_MARKET_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, MarketBlocks.id("schematic_market"),
            MarketContent.SCHEMATIC_MARKET_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, MarketBlocks.id("market_stall"),
            MarketContent.STALL_ITEM);
        Registry.register(BuiltInRegistries.ITEM, MarketBlocks.id("admin_market"),
            MarketContent.ADMIN_MARKET_ITEM);
        Registry.register(BuiltInRegistries.ITEM, MarketBlocks.id("schematic_market"),
            MarketContent.SCHEMATIC_MARKET_ITEM);
        MarketContent.STALL_BLOCK_ENTITY_TYPE = MarketContent.createStallBlockEntityType();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MarketBlocks.id("market_stall"),
            MarketContent.STALL_BLOCK_ENTITY_TYPE);

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, context, selection) -> MarketCommands.register(dispatcher, context));

        ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> MarketEconomy.deliverPending(handler.player));

        // Break protection via event (same code path as NeoForge).
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            Component denial = BreakProtection.checkBreak(player, world, pos);
            if (denial != null) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(denial, true);
                }
                return false;
            }
            return true;
        });

        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("functional_blocks")))
            .register(output -> {
                output.accept(MarketContent.STALL_ITEM);
                output.accept(MarketContent.ADMIN_MARKET_ITEM);
                output.accept(MarketContent.SCHEMATIC_MARKET_ITEM);
            });

        MarketBlocks.LOGGER.info("Market Blocks Fabric entrypoint initialized");
    }
}
