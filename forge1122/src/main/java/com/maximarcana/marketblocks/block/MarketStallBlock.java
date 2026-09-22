package com.maximarcana.marketblocks.block;

import java.util.UUID;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.MarketBlocksForge;
import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;
import com.maximarcana.marketblocks.economy.MarketEconomy;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;

/**
 * Player-owned shop. The placer becomes the owner; only the owner or an
 * operator can break it (enforced by the break-protection event handler).
 * Breaking returns stock and earnings to the owner: stock drops at the block
 * (or is queued for offline owners), earnings are credited to their balance.
 */
public class MarketStallBlock extends Block implements net.minecraft.block.ITileEntityProvider {
    public MarketStallBlock() {
        super(Material.WOOD);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MarketStallTileEntity();
    }

    // ------------------------------------------------------------------
    // Interaction: owner gets the management GUI, everyone else the shop GUI
    // ------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof MarketStallTileEntity) || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        MarketStallTileEntity stall = (MarketStallTileEntity) te;
        int guiId = (stall.isOwner(player.getUniqueID()) || MarketBlocks.isOperator(player))
            ? MarketGuiHandler.STALL_OWNER
            : MarketGuiHandler.STALL_BUYER;
        FMLNetworkHandler.openGui((EntityPlayerMP) player, MarketBlocksForge.instance, guiId,
            world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    // ------------------------------------------------------------------
    // Placement: claim ownership, enforce the per-player stall cap
    // ------------------------------------------------------------------

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) placer;
        MarketEconomy economy = MarketEconomy.get(player.getServer());
        int max = MarketConfig.get().maxStallsPerPlayer;
        if (economy.getStallCount(player.getUniqueID()) >= max && !MarketBlocks.isOperator(player)) {
            world.setBlockToAir(pos);
            ItemStack refund = new ItemStack(this);
            if (!player.inventory.addItemStackToInventory(refund)) {
                player.dropItem(refund, false);
            }
            player.sendMessage(new TextComponentTranslation("message.marketblocks.stall_limit", max));
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof MarketStallTileEntity) {
            ((MarketStallTileEntity) te).setOwner(player.getUniqueID(), player.getName());
        }
        economy.incrementStallCount(player.getUniqueID());
    }

    // ------------------------------------------------------------------
    // Breaking: safe recovery of stock and earnings, exactly once
    // ------------------------------------------------------------------

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof MarketStallTileEntity && !world.isRemote) {
            recoverContents(world, pos, (MarketStallTileEntity) te);
        }
        super.breakBlock(world, pos, state);
    }

    static void recoverContents(World world, BlockPos pos, MarketStallTileEntity stall) {
        MarketEconomy economy = MarketEconomy.get(world.getMinecraftServer());
        UUID ownerId = stall.getOwner();

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
        EntityPlayerMP ownerPlayer = ownerId == null ? null
            : world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerId);
        for (int i = 0; i < MarketStallTileEntity.STOCK_SLOTS; i++) {
            ItemStack stack = stall.getStock().getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ownerPlayer != null) {
                if (!ownerPlayer.inventory.addItemStackToInventory(stack.copy())) {
                    Block.spawnAsEntity(world, pos, stack.copy());
                }
            } else if (ownerId != null) {
                economy.addPendingDelivery(ownerId, stack);
            } else {
                Block.spawnAsEntity(world, pos, stack.copy());
            }
        }
        for (int i = 0; i < MarketStallTileEntity.STOCK_SLOTS; i++) {
            stall.getStock().setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    /** Used by the break-protection event handler. */
    public static boolean canBreak(EntityPlayer player, MarketStallTileEntity stall) {
        return MarketBlocks.isOperator(player)
            || (stall != null && stall.isOwner(player.getUniqueID()));
    }
}
