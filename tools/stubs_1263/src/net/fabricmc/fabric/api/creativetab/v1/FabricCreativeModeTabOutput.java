package net.fabricmc.fabric.api.creativetab.v1;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Compile-only stub of Fabric API's creative tab output wrapper.
 * The real class implements the protected {@code CreativeModeTab.Output}
 * interface; this stub exposes the {@code accept} overloads the mod uses so
 * the mod compiles without the full Fabric API jar. The method descriptors
 * match the real class (which inherits these overloads from
 * {@code CreativeModeTab.Output}), so the compiled mod links correctly at
 * runtime.
 */
public class FabricCreativeModeTabOutput {
    public void accept(ItemStack stack) {
    }

    public void accept(ItemLike itemLike) {
    }
}
