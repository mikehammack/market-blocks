package com.maximarcana.marketblocks.command;

import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.economy.MarketEconomy;
import com.maximarcana.marketblocks.menu.MenuUtil;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** /balance - show your virtual balance. */
public class BalanceCommand extends CommandBase {
    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/balance";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        MarketConfig cfg = MarketConfig.get();
        long balance = MarketEconomy.get(server).getBalance(player.getUniqueID());
        sender.sendMessage(new TextComponentString(
            "Balance: " + MenuUtil.money(balance) + " " + cfg.currencyName));
    }
}
