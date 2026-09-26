package me.aleksilassila.litematica.printer.core.runtime;

public record RuntimeEpoch(long value) {
    public static final RuntimeEpoch INITIAL = new RuntimeEpoch(0L);

    public RuntimeEpoch next() {
        return new RuntimeEpoch(this.value + 1L);
    }
}
