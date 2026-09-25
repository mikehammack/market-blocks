package com.maximarcana.marketblocks.block;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.menu.SchematicMarketMenu;

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
 * Server-run schematic shop: operators list .schematic/.schem files at a
 * price, players pay and the structure is pasted at one of 8 build slots
 * around the block. Placement and breaking are Creative-mode-only (game
 * mode, not permission level); the shop itself is stateless (no block entity).
 */
public class SchematicMarketBlock extends Block {
    public SchematicMarketBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos immutable = pos.immutable();
            ServerLevel serverLevel = (ServerLevel) serverPlayer.level();
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new SchematicMarketMenu(id, inv, serverLevel, immutable),
                Component.translatable("menu.marketblocks.schematic_market")));
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
                    Component.translatable("message.marketblocks.schematic_market_creative_only"), true);
            }
        }
    }
}
