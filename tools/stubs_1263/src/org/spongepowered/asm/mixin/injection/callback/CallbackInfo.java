package org.spongepowered.asm.mixin.injection.callback;

/**
 * Compile-only stub of Mixin's {@code CallbackInfo} base class.
 */
public class CallbackInfo {
    private final String name;
    private final boolean cancellable;
    private boolean cancelled;

    public CallbackInfo(String name, boolean cancellable) {
        this.name = name;
        this.cancellable = cancellable;
    }

    public final boolean isCancellable() {
        return this.cancellable;
    }

    public final boolean isCancelled() {
        return this.cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
