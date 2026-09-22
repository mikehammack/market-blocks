package com.maximarcana.marketblocks.block;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.menu.AdminMarketMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Server-run infinite buy/sell shop driven by the config price list.
 * Placement, breaking, and configuration are Creative-mode-only (game mode,
 * not permission level); the shop itself is stateless and usable by
 * everyone in any mode (no block entity needed).
 */
public class AdminMarketBlock extends Block {
    public AdminMarketBlock() {
        // 1.21.1: no setId() on Properties (that was a 26.x requirement).
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.EMERALD_BLOCK));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos immutable = pos.immutable();
            // 1.21.1: the menu holds a ServerLevel; the cast is safe here
            // (server side only, and the player is a ServerPlayer).
            ServerLevel serverLevel = (ServerLevel) serverPlayer.level();
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new AdminMarketMenu(id, inv, serverLevel, immutable),
                Component.translatable("menu.marketblocks.admin_market")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        if (!MarketBlocks.isCreative(player)) {
            level.removeBlock(pos, false);
            if (player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.getInventory().add(new ItemStack(this))) {
                    serverPlayer.drop(new ItemStack(this), false);
                }
                serverPlayer.sendSystemMessage(
                    Component.translatable("message.marketblocks.admin_market_creative_only"), true);
            }
        }
    }
}
