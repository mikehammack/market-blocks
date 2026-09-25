package com.maximarcana.marketblocks;

import java.io.File;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.GameType;

/**
 * Shared constants and helpers for the 1.12.2 Forge port of Market Blocks.
 * Version 1.0.6.
 */
public final class MarketBlocks {
    public static final String MOD_ID = "marketblocks";
    public static final String MOD_NAME = "Market Blocks";
    public static final String VERSION = "1.0.7";

    private static MarketConfig config;

    private MarketBlocks() {
    }

    public static void init(File configFile) {
        MarketConfig.load(configFile);
        config = MarketConfig.get();
    }

    public static MarketConfig config() {
        return config;
    }

    public static boolean reload() {
        return config.reload();
    }

    /** True Creative check: actual game mode, not permission level. */
    public static boolean isCreative(EntityPlayerMP player) {
        return player != null && player.interactionManager.getGameType() == GameType.CREATIVE;
    }

    /** Operator check via the server's permission level (for stall moderation). */
    public static boolean isOperator(net.minecraft.entity.player.EntityPlayer player) {
        return player instanceof EntityPlayerMP
            && ((EntityPlayerMP) player).canUseCommand(2, "");
    }
}
