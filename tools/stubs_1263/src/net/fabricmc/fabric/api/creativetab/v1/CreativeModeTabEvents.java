package net.fabricmc.fabric.api.creativetab.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Compile-only stub of Fabric API's creative tab events.
 * Signatures verified against fabric-api 0.160.5+26.3 sources.
 */
public final class CreativeModeTabEvents {
    private CreativeModeTabEvents() {
    }

    public static Event<ModifyOutput> modifyOutputEvent(ResourceKey<CreativeModeTab> resourceKey) {
        return null;
    }

    @FunctionalInterface
    public interface ModifyOutput {
        void modifyOutput(FabricCreativeModeTabOutput output);
    }
}
