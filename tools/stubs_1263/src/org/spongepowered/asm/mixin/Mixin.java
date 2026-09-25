package org.spongepowered.asm.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-only stub of the Sponge Mixin {@code @Mixin} annotation.
 * The real annotation is provided at runtime by the mod loader; this stub
 * exists solely so the mod compiles without the full Mixin library.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Mixin {
    Class<?>[] value() default {};

    String[] targets() default {};
}
