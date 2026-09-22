package com.maximarcana.marketblocks.event;

import com.maximarcana.marketblocks.economy.MarketEconomy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Delivers pending stall-stock recovery to players when they log in. */
public class LoginHandler {
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            MarketEconomy.deliverPending((EntityPlayerMP) event.player);
        }
    }
}
