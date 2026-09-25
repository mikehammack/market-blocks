package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-only stub of Fabric API's server-side block-break events.
 * Signature verified against the real
 * {@code fabric-events-interaction-v0} sources on the fabric-api 26.3
 * branch: {@code Before.beforeBlockBreak(Level, Player, BlockPos,
 * BlockState, BlockEntity)} returns boolean (false cancels).
 */
public final class PlayerBlockBreakEvents {
    private PlayerBlockBreakEvents() {
    }

    public static final Event<Before> BEFORE = null;

    @FunctionalInterface
    public interface Before {
        boolean beforeBlockBreak(Level level, Player player, BlockPos pos,
            BlockState state, BlockEntity blockEntity);
    }
}
