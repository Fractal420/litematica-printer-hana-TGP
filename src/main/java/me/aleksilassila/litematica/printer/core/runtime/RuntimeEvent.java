package me.aleksilassila.litematica.printer.core.runtime;

public sealed interface RuntimeEvent permits RuntimeEvent.EpochChanged, RuntimeEvent.BlockUpdated {
    record EpochChanged(RuntimeEpoch previous, RuntimeEpoch current, String reason) implements RuntimeEvent {
    }

    record BlockUpdated(int x, int y, int z) implements RuntimeEvent {
    }
}
