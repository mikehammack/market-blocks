package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Compile-only stub of Fabric API's command registration callback.
 * Signature matches fabric-api 0.160.5+26.3 (the mod passes the context
 * straight into {@code MarketCommands.register(dispatcher, context)}).
 */
@FunctionalInterface
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = null;

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
            Commands.CommandSelection selection);
}
