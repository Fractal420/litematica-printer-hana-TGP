package me.aleksilassila.litematica.printer.core.scan;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanHandle implements AutoCloseable {
    private final ScanGeneration generation;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ScanHandle(ScanGeneration generation) {
        if (generation == null) throw new IllegalArgumentException("generation must not be null");
        this.generation = generation;
    }

    public ScanGeneration generation() {
        return this.generation;
    }

    public boolean accepts(ScanGeneration candidate) {
        return !this.cancelled.get() && this.generation.equals(candidate);
    }

    public boolean isCancelled() {
        return this.cancelled.get();
    }

    @Override
    public void close() {
        this.cancelled.set(true);
    }
}
