package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-only stub of the Sponge Mixin {@code @At} annotation. */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface At {
    String value() default "";

    String target() default "";

    int ordinal() default -1;
}
