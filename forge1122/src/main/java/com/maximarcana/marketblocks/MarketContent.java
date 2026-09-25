package com.maximarcana.marketblocks;

import com.maximarcana.marketblocks.block.AdminMarketBlock;
import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.block.SchematicMarketBlock;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;

/** Block/item instances, created once and registered via registry events. */
public final class MarketContent {
    public static MarketStallBlock STALL_BLOCK;
    public static AdminMarketBlock ADMIN_MARKET_BLOCK;
    public static SchematicMarketBlock SCHEMATIC_MARKET_BLOCK;
    public static ItemBlock STALL_ITEM;
    public static ItemBlock ADMIN_MARKET_ITEM;
    public static ItemBlock SCHEMATIC_MARKET_ITEM;

    private MarketContent() {
    }

    public static void create() {
        // NOTE (1.12.2): setRegistryName is invoked through an
        // IForgeRegistryEntry-typed reference, and tools/AddForgeInterfaces.java
        // makes the compile-time Block/Item extend
        // IForgeRegistryEntry$Impl<X> -- exactly what Forge's runtime binary
        // patches do. Both layers matter: the erased runtime descriptor is
        // (LResourceLocation;)LIForgeRegistryEntry;, and a covariant
        // (LResourceLocation;)LBlock; reference does not exist at runtime
        // (NoSuchMethodError on mod load). Do not "simplify" either side.
        IForgeRegistryEntry<Block> stallEntry = new MarketStallBlock();
        stallEntry.setRegistryName(new ResourceLocation(MarketBlocks.MOD_ID, "market_stall"));
        STALL_BLOCK = (MarketStallBlock) stallEntry;
        STALL_BLOCK.setTranslationKey(MarketBlocks.MOD_ID + ".market_stall");

        IForgeRegistryEntry<Block> adminEntry = new AdminMarketBlock();
        adminEntry.setRegistryName(new ResourceLocation(MarketBlocks.MOD_ID, "admin_market"));
        ADMIN_MARKET_BLOCK = (AdminMarketBlock) adminEntry;
        ADMIN_MARKET_BLOCK.setTranslationKey(MarketBlocks.MOD_ID + ".admin_market");

        IForgeRegistryEntry<Item> stallItemEntry = new ItemBlock(STALL_BLOCK);
        stallItemEntry.setRegistryName(STALL_BLOCK.getRegistryName());
        STALL_ITEM = (ItemBlock) stallItemEntry;
        STALL_ITEM.setTranslationKey(STALL_BLOCK.getTranslationKey());

        IForgeRegistryEntry<Item> adminItemEntry = new ItemBlock(ADMIN_MARKET_BLOCK);
        adminItemEntry.setRegistryName(ADMIN_MARKET_BLOCK.getRegistryName());
        ADMIN_MARKET_ITEM = (ItemBlock) adminItemEntry;
        ADMIN_MARKET_ITEM.setTranslationKey(ADMIN_MARKET_BLOCK.getTranslationKey());

        IForgeRegistryEntry<Block> schematicEntry = new SchematicMarketBlock();
        schematicEntry.setRegistryName(new ResourceLocation(MarketBlocks.MOD_ID, "schematic_market"));
        SCHEMATIC_MARKET_BLOCK = (SchematicMarketBlock) schematicEntry;
        SCHEMATIC_MARKET_BLOCK.setTranslationKey(MarketBlocks.MOD_ID + ".schematic_market");

        IForgeRegistryEntry<Item> schematicItemEntry = new ItemBlock(SCHEMATIC_MARKET_BLOCK);
        schematicItemEntry.setRegistryName(SCHEMATIC_MARKET_BLOCK.getRegistryName());
        SCHEMATIC_MARKET_ITEM = (ItemBlock) schematicItemEntry;
        SCHEMATIC_MARKET_ITEM.setTranslationKey(SCHEMATIC_MARKET_BLOCK.getTranslationKey());
    }

    public static void registerBlocks(
            net.minecraftforge.event.RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(STALL_BLOCK, ADMIN_MARKET_BLOCK, SCHEMATIC_MARKET_BLOCK);
    }

    public static void registerItems(
            net.minecraftforge.event.RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(STALL_ITEM, ADMIN_MARKET_ITEM, SCHEMATIC_MARKET_ITEM);
    }
}
