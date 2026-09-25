package com.maximarcana.marketblocks.block;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.blockentity.MarketStallBlockEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;
import com.maximarcana.marketblocks.menu.StallBuyerMenu;
import com.maximarcana.marketblocks.menu.StallOwnerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Player-owned shop. The placer becomes the owner; only the owner or an
 * operator can break it (enforced by the loader break event via
 * BreakProtection). Breaking
 * returns stock and earnings to the owner: stock drops at the block (or is
 * queued for offline owners), earnings are credited to their balance.
 */
public class MarketStallBlock extends Block implements EntityBlock {
    public MarketStallBlock() {
        // MC 26.x: the id must be stamped on Properties before construction,
        // otherwise BlockBehaviour's constructor throws (default loot table
        // derivation needs the id).
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).setId(MarketContent.STALL_BLOCK_ID));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketStallBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------
    // Interaction: owner gets the management GUI, everyone else the shop GUI
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MarketStallBlockEntity stall) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        boolean owner = stall.isOwner(player.getUUID()) || MarketBlocks.isOperator(player);
        if (owner) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new StallOwnerMenu(id, inv, stall),
                Component.translatable("menu.marketblocks.stall_owner")));
        } else {
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new StallBuyerMenu(id, inv, stall),
                Component.translatable("menu.marketblocks.stall_buyer",
                    stall.getOwnerName().isEmpty() ? "?" : stall.getOwnerName())));
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Placement: claim ownership, enforce the per-player stall cap
    // ------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        MarketEconomy economy = MarketEconomy.get(player.level().getServer());
        int max = MarketConfig.get().maxStallsPerPlayer;
        if (economy.getStallCount(player.getUUID()) >= max && !MarketBlocks.isOperator(player)) {
            level.removeBlock(pos, false);
            if (!player.getInventory().add(new ItemStack(this))) {
                player.drop(new ItemStack(this), false, Prediction.SERVER_ONLY);
            }
            player.sendSystemMessage(Component.translatable("message.marketblocks.stall_limit", max), true);
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MarketStallBlockEntity stall) {
            stall.setOwner(player.getUUID(), player.getScoreboardName());
        }
        economy.incrementStallCount(player.getUUID());
    }

    // ------------------------------------------------------------------
    // Breaking: safe recovery of stock and earnings, exactly once
    // ------------------------------------------------------------------

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MarketStallBlockEntity stall) {
            recoverContents(level, pos, stall);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    private static void recoverContents(ServerLevel level, BlockPos pos, MarketStallBlockEntity stall) {
        MarketEconomy economy = MarketEconomy.get(level.getServer());
        var ownerId = stall.getOwner();

        // Earnings always go straight to the owner's virtual balance (safe offline).
        long earnings = stall.withdrawEarnings();
        if (ownerId != null && earnings > 0) {
            economy.addBalance(ownerId, earnings);
        }
        if (ownerId != null) {
            economy.decrementStallCount(ownerId);
        }

        // Stock: online owner gets it into their inventory, else it drops at
        // the block; if the owner is offline it is queued for delivery on login.
        ServerPlayer ownerPlayer = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        for (int i = 0; i < MarketStallBlockEntity.STOCK_SLOTS; i++) {
            ItemStack stack = stall.getStock().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ownerPlayer != null) {
                if (!ownerPlayer.getInventory().add(stack.copy())) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        stack.copy());
                }
            } else if (ownerId != null) {
                economy.addPendingDelivery(ownerId, stack);
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    stack.copy());
            }
        }
        stall.getStock().clearContent();
    }

    /** Used by the break-protection event handler (BreakProtection). */
    public static boolean canBreak(Player player, MarketStallBlockEntity stall) {
        return MarketBlocks.isOperator(player)
            || (stall != null && stall.isOwner(player.getUUID()));
    }
}
