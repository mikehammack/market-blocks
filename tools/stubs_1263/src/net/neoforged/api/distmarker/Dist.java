package net.neoforged.api.distmarker;

/**
 * Compile-only stub of NeoForge's distribution marker enum.
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
