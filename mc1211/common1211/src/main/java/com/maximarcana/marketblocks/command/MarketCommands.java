package com.maximarcana.marketblocks.command;

import com.maximarcana.marketblocks.MarketConfig;
import com.maximarcana.marketblocks.economy.MarketEconomy;
import com.maximarcana.marketblocks.menu.MenuUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /balance, /pay, /market reload. All balance mutations go through
 * MarketEconomy on the server thread.
 */
public final class MarketCommands {
    private MarketCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context) {
        dispatcher.register(Commands.literal("balance")
            .executes(MarketCommands::showBalance));

        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(MarketCommands::pay))));

        dispatcher.register(Commands.literal("market")
            .then(Commands.literal("reload")
                // 1.21.1: no PermissionCheck API; classic permission level 2.
                .requires(src -> src.hasPermission(2))
                .executes(MarketCommands::reload)));
    }

    private static int showBalance(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Only players have balances."));
            return 0;
        }
        MarketConfig cfg = MarketConfig.get();
        long balance = MarketEconomy.get(player.level().getServer()).getBalance(player.getUUID());
        ctx.getSource().sendSuccess(
            () -> Component.literal("Balance: " + MenuUtil.money(balance)
                + " " + cfg.currencyName),
            false);
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        long amount = LongArgumentType.getLong(ctx, "amount");
        MarketConfig cfg = MarketConfig.get();

        if (amount <= 0) {
            ctx.getSource().sendFailure(Component.literal("Amount must be positive."));
            return 0;
        }
        if (sender.getUUID().equals(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }
        MarketEconomy economy = MarketEconomy.get(sender.level().getServer());
        if (!economy.transfer(sender.getUUID(), target.getUUID(), amount)) {
            ctx.getSource().sendFailure(Component.literal(
                "Insufficient funds (balance: " + MenuUtil.money(economy.getBalance(sender.getUUID())) + ")."));
            return 0;
        }
        ctx.getSource().sendSuccess(
            () -> Component.literal("Paid " + MenuUtil.money(amount) + " to "
                + target.getScoreboardName() + "."),
            false);
        target.sendSystemMessage(Component.literal("Received " + MenuUtil.money(amount) + " from "
            + sender.getScoreboardName() + "."));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        String error = MarketConfig.reload();
        if (error == null) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("Market Blocks config reloaded ("
                    + MarketConfig.get().prices.size() + " price entries)."),
                true);
            return 1;
        }
        ctx.getSource().sendFailure(
            Component.literal("Config reload failed, previous config kept: " + error));
        return 0;
    }
}
