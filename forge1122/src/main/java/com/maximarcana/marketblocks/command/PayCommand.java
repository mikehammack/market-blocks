package com.maximarcana.marketblocks.command;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.maximarcana.marketblocks.economy.MarketEconomy;
import com.maximarcana.marketblocks.menu.MenuUtil;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** /pay <player> <amount> - transfer currency to another player. */
public class PayCommand extends CommandBase {
    @Override
    public String getName() {
        return "pay";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/pay <player> <amount>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP from = getCommandSenderAsPlayer(sender);
        EntityPlayerMP to = getPlayer(server, sender, args[0]);
        long amount = parseLong(args[1], 1, Long.MAX_VALUE);

        if (from.getUniqueID().equals(to.getUniqueID())) {
            throw new CommandException("You cannot pay yourself.");
        }
        MarketEconomy economy = MarketEconomy.get(server);
        if (!economy.transfer(from.getUniqueID(), to.getUniqueID(), amount)) {
            throw new CommandException("Insufficient funds (balance: "
                + MenuUtil.money(economy.getBalance(from.getUniqueID())) + ").");
        }
        sender.sendMessage(new TextComponentString(
            "Paid " + MenuUtil.money(amount) + " to " + to.getName() + "."));
        to.sendMessage(new TextComponentString(
            "Received " + MenuUtil.money(amount) + " from " + from.getName() + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
