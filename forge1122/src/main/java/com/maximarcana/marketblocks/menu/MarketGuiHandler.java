package com.maximarcana.marketblocks.menu;

import com.maximarcana.marketblocks.MarketBlocks;
import com.maximarcana.marketblocks.blockentity.MarketStallTileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Server containers + client chest screens for the three market GUIs. */
public class MarketGuiHandler implements IGuiHandler {
    public static final int STALL_OWNER = 0;
    public static final int STALL_BUYER = 1;
    public static final int ADMIN_MARKET = 2;
    public static final int SCHEMATIC_MARKET = 3;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (!(player instanceof EntityPlayerMP)) {
            return null;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        BlockPos pos = new BlockPos(x, y, z);
        if (id == STALL_OWNER || id == STALL_BUYER) {
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof MarketStallTileEntity)) {
                return null;
            }
            MarketStallTileEntity stall = (MarketStallTileEntity) te;
            // Re-check the owner rule server-side; the block already did, but
            // a stale client click must never open the wrong GUI.
            boolean owner = stall.isOwner(player.getUniqueID()) || MarketBlocks.isOperator(player);
            if (id == STALL_OWNER && owner) {
                return new StallOwnerContainer(mp.inventory, stall);
            }
            if (id == STALL_BUYER && !owner) {
                return new StallBuyerContainer(mp.inventory, stall);
            }
            return null;
        }
        if (id == ADMIN_MARKET) {
            return new AdminMarketContainer(mp.inventory, mp, world, pos);
        }
        if (id == SCHEMATIC_MARKET) {
            return new SchematicMarketContainer(mp.inventory, mp, world, pos);
        }
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        InventoryPlayer inv = player.inventory;
        if (id == STALL_OWNER) {
            Container c = new StallOwnerContainer(inv, world, pos);
            return new MarketGui(inv, title("menu.marketblocks.stall_owner", 54), c);
        }
        if (id == STALL_BUYER) {
            Container c = new StallBuyerContainer(inv, world, pos);
            return new MarketGui(inv, title("menu.marketblocks.stall_buyer", 27), c);
        }
        if (id == ADMIN_MARKET) {
            // Client-side Creative check so the configure-mode lever renders
            // correctly; the server re-checks authoritatively on every click.
            boolean creative = Minecraft.getMinecraft().playerController.getCurrentGameType()
                == GameType.CREATIVE;
            Container c = new AdminMarketContainer(inv, world, pos, creative);
            return new MarketGui(inv, title("menu.marketblocks.admin_market", 54), c);
        }
        if (id == SCHEMATIC_MARKET) {
            boolean creative = Minecraft.getMinecraft().playerController.getCurrentGameType()
                == GameType.CREATIVE;
            Container c = new SchematicMarketContainer(inv, world, pos, creative);
            return new MarketGui(inv, title("menu.marketblocks.schematic_market", 54), c);
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    private static IInventory title(String key, int size) {
        return new InventoryBasic(new TextComponentTranslation(key), size);
    }

    /** Vanilla chest screen bound to one of our server containers. */
    @SideOnly(Side.CLIENT)
    public static class MarketGui extends GuiChest {
        public MarketGui(InventoryPlayer playerInv, IInventory titleInv, Container container) {
            super(playerInv, titleInv);
            this.inventorySlots = container;
        }
    }
}
