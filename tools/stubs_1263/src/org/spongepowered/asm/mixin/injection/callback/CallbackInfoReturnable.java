package org.spongepowered.asm.mixin.injection.callback;

/**
 * Compile-only stub of Mixin's {@code CallbackInfoReturnable}.
 */
public class CallbackInfoReturnable<T> extends CallbackInfo {
    private T returnValue;

    public CallbackInfoReturnable(String name, boolean cancellable) {
        super(name, cancellable);
    }

    public void setReturnValue(T value) {
        this.returnValue = value;
        cancel();
    }

    public T getReturnValue() {
        return this.returnValue;
    }
}
