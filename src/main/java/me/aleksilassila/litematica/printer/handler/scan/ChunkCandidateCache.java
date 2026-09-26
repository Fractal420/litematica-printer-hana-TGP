package me.aleksilassila.litematica.printer.handler.scan;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import java.util.function.BooleanSupplier;

final class ChunkCandidateCache {
    private final Long2ByteOpenHashMap values = new Long2ByteOpenHashMap();

    boolean getOrCompute(int chunkX, int chunkZ, BooleanSupplier source) {
        long key = key(chunkX, chunkZ);
        byte cached = this.values.getOrDefault(key, (byte) -1);
        if (cached == -1) {
            cached = (byte) (source.getAsBoolean() ? 1 : 0);
            this.values.put(key, cached);
        }
        return cached == 1;
    }

    void invalidate(int chunkX, int chunkZ) {
        this.values.remove(key(chunkX, chunkZ));
    }

    void clear() {
        this.values.clear();
    }

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
    }
}
