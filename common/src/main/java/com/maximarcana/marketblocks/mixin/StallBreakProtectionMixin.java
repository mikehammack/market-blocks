package com.maximarcana.marketblocks.mixin;

import java.util.UUID;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the stall owner or an operator may break a market stall, in any
 * game mode. Cancelled before the block is touched, so no duplication or
 * loss can occur.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class StallBreakProtectionMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void marketblocks$protectStall(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = this.player.level();
        BlockState state = level.getBlockState(pos);
        if (state.is(MarketContent.ADMIN_MARKET_BLOCK)) {
            if (!MarketBlocks.isCreative(this.player)) {
                this.player.sendSystemMessage(
                    Component.literal("Only players in Creative mode can break the Admin Market."),
                    true);
                MarketBlocks.LOGGER.debug("Blocked admin market break by {}",
                    this.player.getScoreboardName());
                cir.setReturnValue(false);
            }
            return;
        }
        if (!state.is(MarketContent.STALL_BLOCK)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        MarketStallBlockEntity stall = be instanceof MarketStallBlockEntity s ? s : null;
        if (!MarketStallBlock.canBreak(this.player, stall)) {
            UUID owner = stall == null ? null : stall.getOwner();
            String who = stall == null || stall.getOwnerName().isEmpty()
                ? "another player" : stall.getOwnerName();
            this.player.sendSystemMessage(
                Component.literal("This market stall belongs to " + who
                    + ". Only the owner or an operator can break it."),
                true);
            MarketBlocks.LOGGER.debug("Blocked stall break by {} (owner {})",
                this.player.getScoreboardName(), owner);
            cir.setReturnValue(false);
        }
    }
}
