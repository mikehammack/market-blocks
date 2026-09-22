package com.maximarcana.marketblocks.command;

import com.maximarcana.marketblocks.MarketConfig;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** /market reload - hot-reload the config (operators only). */
public class MarketCommand extends CommandBase {
    @Override
    public String getName() {
        return "market";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/market reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 1 || !args[0].equals("reload")) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (MarketConfig.reload()) {
            sender.sendMessage(new TextComponentString("Market Blocks config reloaded ("
                + MarketConfig.get().entries().size() + " price entries)."));
        } else {
            throw new CommandException("Config reload failed, previous config kept. "
                + "See the server log for details.");
        }
    }
}
