package me.aleksilassila.litematica.printer.core.runtime;

public interface RuntimeComponent extends AutoCloseable {
    default void onEpochChanged(RuntimeEvent.EpochChanged event) {
    }

    @Override
    default void close() {
    }
}
