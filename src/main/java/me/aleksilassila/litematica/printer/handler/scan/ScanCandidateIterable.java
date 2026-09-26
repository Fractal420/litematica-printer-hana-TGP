package me.aleksilassila.litematica.printer.handler.scan;

import net.minecraft.core.BlockPos;

public interface ScanCandidateIterable extends Iterable<BlockPos> {
    ScanAvailability availability();

    default boolean isBuffered() {
        return false;
    }
}
