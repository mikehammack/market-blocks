package com.maximarcana.marketblocks.event;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketContent;
import com.maximarcana.marketblocks.block.MarketStallBlock;
import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Replaces the 26.x break-protection mixin with a Forge event:
 * - Market stalls break only for the owner or an operator.
 * - The Admin Market breaks only in Creative mode (game mode, not op level).
 */
public class BreakProtectionHandler {
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Block block = event.getState().getBlock();
        EntityPlayer player = event.getPlayer();
        if (block == MarketContent.STALL_BLOCK) {
            TileEntity te = event.getWorld().getTileEntity(event.getPos());
            MarketStallTileEntity stall = te instanceof MarketStallTileEntity
                ? (MarketStallTileEntity) te : null;
            if (!MarketStallBlock.canBreak(player, stall)) {
                event.setCanceled(true);
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).sendMessage(new TextComponentTranslation(
                        "message.marketblocks.stall_break_denied"));
                }
            }
        } else if (block == MarketContent.ADMIN_MARKET_BLOCK
                || block == MarketContent.SCHEMATIC_MARKET_BLOCK) {
            if (!(player instanceof EntityPlayerMP)
                || !MarketBlocks.isCreative((EntityPlayerMP) player)) {
                event.setCanceled(true);
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).sendMessage(new TextComponentTranslation(
                        block == MarketContent.SCHEMATIC_MARKET_BLOCK
                            ? "message.marketblocks.schematic_market_creative_only"
                            : "message.marketblocks.admin_market_creative_only"));
                }
            }
        }
    }
}
