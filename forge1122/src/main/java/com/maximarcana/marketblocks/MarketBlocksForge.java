package com.maximarcana.marketblocks;

import java.io.File;

import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;
import com.maximarcana.marketblocks.command.BalanceCommand;
import com.maximarcana.marketblocks.command.MarketCommand;
import com.maximarcana.marketblocks.command.PayCommand;
import com.maximarcana.marketblocks.event.BreakProtectionHandler;
import com.maximarcana.marketblocks.event.LoginHandler;
import com.maximarcana.marketblocks.menu.MarketGuiHandler;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Forge 1.12.2 entrypoint for Market Blocks v1.0.6.
 */
@Mod(modid = MarketBlocks.MOD_ID, name = MarketBlocks.MOD_NAME, version = MarketBlocks.VERSION,
    acceptableRemoteVersions = "*")
public class MarketBlocksForge {
    @Mod.Instance(MarketBlocks.MOD_ID)
    public static MarketBlocksForge instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MarketBlocks.init(new File(event.getModConfigurationDirectory(), "marketblocks.cfg"));
        MarketContent.create();
        GameRegistry.registerTileEntity(MarketStallTileEntity.class,
            new ResourceLocation(MarketBlocks.MOD_ID, "market_stall"));
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new MarketGuiHandler());
        MinecraftForge.EVENT_BUS.register(new BreakProtectionHandler());
        MinecraftForge.EVENT_BUS.register(new LoginHandler());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BalanceCommand());
        event.registerServerCommand(new PayCommand());
        event.registerServerCommand(new MarketCommand());
    }

    @Mod.EventBusSubscriber(modid = MarketBlocks.MOD_ID)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocks(RegistryEvent.Register<Block> event) {
            MarketContent.registerBlocks(event);
        }

        @SubscribeEvent
        public static void onItems(RegistryEvent.Register<Item> event) {
            MarketContent.registerItems(event);
        }
    }

    /**
     * 1.12.2 does not auto-resolve item models from models/item/ the way
     * blocks resolve through blockstates -- each item's model location must
     * be registered explicitly during ModelRegistryEvent, or the item
     * renders as the missing texture in inventories. Client-only: this
     * subscriber is never loaded on a dedicated server (Side.CLIENT).
     */
    @Mod.EventBusSubscriber(modid = MarketBlocks.MOD_ID, value = Side.CLIENT)
    public static class ModelEvents {
        @SubscribeEvent
        public static void onModels(ModelRegistryEvent event) {
            ModelLoader.setCustomModelResourceLocation(MarketContent.STALL_ITEM, 0,
                new ModelResourceLocation(
                    new ResourceLocation(MarketBlocks.MOD_ID, "market_stall"), "inventory"));
            ModelLoader.setCustomModelResourceLocation(MarketContent.ADMIN_MARKET_ITEM, 0,
                new ModelResourceLocation(
                    new ResourceLocation(MarketBlocks.MOD_ID, "admin_market"), "inventory"));
            ModelLoader.setCustomModelResourceLocation(MarketContent.SCHEMATIC_MARKET_ITEM, 0,
                new ModelResourceLocation(
                    new ResourceLocation(MarketBlocks.MOD_ID, "schematic_market"), "inventory"));
        }
    }
}
