package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Compile-only stub of Fabric API's server play connection events.
 */
public final class ServerPlayConnectionEvents {
    private ServerPlayConnectionEvents() {
    }

    public static final Event<Join> JOIN = null;

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }
}
