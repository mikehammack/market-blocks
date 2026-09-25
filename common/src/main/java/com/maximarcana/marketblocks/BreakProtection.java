package com.maximarcana.marketblocks;

import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Break rules for the market blocks, shared by the loader-specific break
 * handlers (Fabric {@code PlayerBlockBreakEvents.BEFORE}, NeoForge
 * {@code BlockEvent.BreakEvent}). Replaces the old
 * {@code StallBreakProtectionMixin}, which is gone: its config was never
 * packaged for NeoForge, and an event covers both loaders with one
 * code path.
 *
 * <p>Returns a denial message when the break must be blocked, or null when
 * it is allowed. Callers display the message and cancel the break.
 */
public final class BreakProtection {
    private BreakProtection() {
    }

    public static Component checkBreak(Player player, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(MarketContent.ADMIN_MARKET_BLOCK)) {
            if (!MarketBlocks.isCreative(player)) {
                MarketBlocks.LOGGER.debug("Blocked admin market break by {}",
                    player.getScoreboardName());
                return Component.literal("Only players in Creative mode can break the Admin Market.");
            }
            return null;
        }
        if (!state.is(MarketContent.STALL_BLOCK)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        MarketStallBlockEntity stall = be instanceof MarketStallBlockEntity s ? s : null;
        if (!MarketStallBlock.canBreak(player, stall)) {
            String who = stall == null || stall.getOwnerName().isEmpty()
                ? "another player" : stall.getOwnerName();
            MarketBlocks.LOGGER.debug("Blocked stall break by {} (owner {})",
                player.getScoreboardName(), stall == null ? null : stall.getOwner());
            return Component.literal("This market stall belongs to " + who
                + ". Only the owner or an operator can break it.");
        }
        return null;
    }
}
