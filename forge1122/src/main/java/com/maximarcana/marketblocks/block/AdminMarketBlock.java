package com.maximarcana.marketblocks.block;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketBlocksForge;
import com.maximarcana.marketblocks.menu.MarketGuiHandler;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;

/**
 * Server-run infinite buy/sell shop driven by the config price list.
 * Placement, breaking, and configuration are Creative-mode-only (game mode,
 * not permission level); the shop itself is stateless and usable by
 * everyone in any mode (no block entity needed).
 */
public class AdminMarketBlock extends Block {
    public AdminMarketBlock() {
        super(Material.ROCK);
        setHardness(5.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (player instanceof EntityPlayerMP) {
            FMLNetworkHandler.openGui((EntityPlayerMP) player, MarketBlocksForge.instance,
                MarketGuiHandler.ADMIN_MARKET, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) placer;
        if (!MarketBlocks.isCreative(player)) {
            world.setBlockToAir(pos);
            ItemStack refund = new ItemStack(this);
            if (!player.inventory.addItemStackToInventory(refund)) {
                player.dropItem(refund, false);
            }
            player.sendMessage(
                new TextComponentTranslation("message.marketblocks.admin_market_creative_only"));
        }
    }
}
