package me.aleksilassila.litematica.printer.handler.scan;

import net.minecraft.core.BlockPos;

record ScanDirtyPosition(BlockPos pos, long distanceSqr) implements Comparable<ScanDirtyPosition> {
    @Override
    public int compareTo(ScanDirtyPosition other) {
        int result = Long.compare(this.distanceSqr, other.distanceSqr);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.pos.getX(), other.pos.getX());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.pos.getY(), other.pos.getY());
        if (result != 0) {
            return result;
        }
        return Integer.compare(this.pos.getZ(), other.pos.getZ());
    }
}
