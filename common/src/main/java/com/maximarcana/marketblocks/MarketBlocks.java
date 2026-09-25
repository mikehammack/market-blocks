package com.maximarcana.marketblocks;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Market Blocks - a dead-simple server market: two blocks, sane configs,
 * and a working economy in five minutes.
 */
public final class MarketBlocks {
    public static final String MOD_ID = "marketblocks";
    public static final String MOD_NAME = "Market Blocks";
    public static final String VERSION = "1.0.7";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private MarketBlocks() {
    }

    /** Loader entry point: loads (or creates) the config, then builds content holders. */
    public static void init(Path configFile) {
        MarketConfig.load(configFile);
        MarketContent.create();
        LOGGER.info("{} v{} initialized", MOD_NAME, VERSION);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Operator check used for stall breaking, the stall cap bypass, and /market reload. */
    public static boolean isOperator(Player player) {
        return player != null && Commands.LEVEL_MODERATORS.check(player.permissions());
    }

    /**
     * Creative-mode check used for admin-market placement, breaking, and
     * configuration. This is deliberately game mode, not permission level:
     * in singleplayer with cheats on (or for an op playing survival on a
     * server) the player still has operator permissions, so a permission
     * check alone cannot tell survival play apart from admin setup.
     */
    public static boolean isCreative(Player player) {
        return player instanceof ServerPlayer sp
            && sp.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }
}
