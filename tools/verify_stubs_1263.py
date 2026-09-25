#!/usr/bin/env python3
"""Regression check: stub class/interface/enum kinds must match the real APIs.

The 1.0.6 Fabric 26.3 crash (IncompatibleClassChangeError: Found class
net.fabricmc.fabric.api.event.Event, but interface was expected) was caused
by a stub declaring `interface Event` when the real Fabric API declares
`abstract class Event`. javac then emits invokeinterface at the call site,
which the JVM rejects against the real class at runtime.

Expected kinds below were verified against the real sources on 2026-09-24:
- Sponge Mixin 0.8.7 (github.com/SpongePowered/Mixin, tag releases/0.8.7)
- Fabric API 0.160.5+26.3 (github.com/FabricMC/fabric-api, tag 0.160.5+26.3)

Run: python3 tools/verify_stubs_1263.py <compiled-stub-classes-dir>
Exits non-zero on any kind mismatch.
"""
import subprocess
import sys
from pathlib import Path

# dotted name -> expected kind: "class", "abstract_class", "interface", "enum"
EXPECTED = {
    "org.spongepowered.asm.mixin.Mixin": "interface",  # annotation
    "org.spongepowered.asm.mixin.Shadow": "interface",  # annotation
    "org.spongepowered.asm.mixin.Final": "interface",  # annotation
    "org.spongepowered.asm.mixin.injection.Inject": "interface",  # annotation
    "org.spongepowered.asm.mixin.injection.At": "interface",  # annotation
    "org.spongepowered.asm.mixin.injection.callback.CallbackInfo": "class",
    "org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable": "class",
    "net.fabricmc.fabric.api.event.Event": "abstract_class",
    "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback": "interface",
    "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents": "class",
    "net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput": "class",
    "net.fabricmc.fabric.api.networking.v1.PacketSender": "interface",
    "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents": "class",
    "net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents": "class",
    "net.neoforged.api.distmarker.Dist": "enum",
}

JAVAP = str(Path.home() / ".jdks/jdk-25.0.4.1+1/bin/javap")


def kind_of(classes_dir: Path, dotted: str) -> str:
    out = subprocess.run(
        [JAVAP, "-cp", str(classes_dir), dotted],
        capture_output=True, text=True, check=True,
    ).stdout
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("public abstract @interface") or s.startswith("@interface"):
            return "interface"  # annotation
        if "public abstract class" in s:
            return "abstract_class"
        if "extends java.lang.Enum" in s:
            return "enum"
        if " public interface " in f" {s} " or s.startswith("public interface "):
            return "interface"
        if " enum " in f" {s} " or s.startswith("public enum "):
            return "enum"
        if " class " in f" {s} " or s.startswith("public class ") or s.startswith("public final class "):
            return "class"
    raise ValueError(f"unrecognized javap output for {dotted}:\n{out[:300]}")


def main() -> int:
    classes_dir = Path(sys.argv[1])
    failures = []
    for dotted, expected in EXPECTED.items():
        try:
            actual = kind_of(classes_dir, dotted)
        except Exception as e:  # noqa: BLE001
            failures.append(f"{dotted}: could not inspect ({e})")
            continue
        # annotations are interfaces for kind purposes
        if actual != expected:
            failures.append(f"{dotted}: stub is {actual}, real API is {expected}")
    if failures:
        print("STUB KIND MISMATCHES (would break at runtime):")
        for f in failures:
            print("  -", f)
        return 1
    print(f"OK: all {len(EXPECTED)} stub kinds match the real APIs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
