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
 * operator can break it (enforced by the break-protection event handlers). Breaking
 * returns stock and earnings to the owner: stock drops at the block (or is
 * queued for offline owners), earnings are credited to their balance.
 */
public class MarketStallBlock extends Block implements EntityBlock {
    public MarketStallBlock() {
        // 1.21.1: no setId() on Properties (that was a 26.x requirement).
        // The id comes from the registry name at registration time.
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
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
                player.drop(new ItemStack(this), false);
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

    /**
     * 1.21.1 note: the 26.x build hooked {@code affectNeighborsAfterRemoval};
     * that method does not exist in 1.21.1. {@code onRemove} is the
     * equivalent: it runs while the block entity is still present, before
     * the chunk unloads it.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MarketStallBlockEntity stall) {
                recoverContents(level, pos, stall);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void recoverContents(Level level, BlockPos pos, MarketStallBlockEntity stall) {
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

    /** Used by the break-protection handlers (Forge event / Fabric event). */
    public static boolean canBreak(Player player, MarketStallBlockEntity stall) {
        return MarketBlocks.isOperator(player)
            || (stall != null && stall.isOwner(player.getUUID()));
    }
}
