package net.fabricmc.fabric.api.event;

/**
 * Compile-only stub of Fabric API's event base class. Must be a CLASS (not
 * an interface) to match the real {@code Event}, otherwise call sites
 * compiled with invokeinterface fail at runtime with
 * IncompatibleClassChangeError. Only {@link #register} is needed to compile;
 * the real implementation is provided by Fabric API at runtime.
 */
public abstract class Event<T> {
    public abstract void register(T listener);
}
