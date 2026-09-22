package com.maximarcana.marketblocks.fabric;


import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.command.MarketCommands;
import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
        Registry.register(BuiltInRegistries.ITEM, MarketBlocks.id("market_stall"),
            MarketContent.STALL_ITEM);
        Registry.register(BuiltInRegistries.ITEM, MarketBlocks.id("admin_market"),
            MarketContent.ADMIN_MARKET_ITEM);
        MarketContent.STALL_BLOCK_ENTITY_TYPE = MarketContent.createStallBlockEntityType();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MarketBlocks.id("market_stall"),
            MarketContent.STALL_BLOCK_ENTITY_TYPE);

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, context, selection) -> MarketCommands.register(dispatcher, context));

        ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> MarketEconomy.deliverPending(handler.player));

        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("functional_blocks")))
            .register(output -> {
                output.accept(MarketContent.STALL_ITEM);
                output.accept(MarketContent.ADMIN_MARKET_ITEM);
            });

        MarketBlocks.LOGGER.info("Market Blocks Fabric entrypoint initialized");
    }
}
