package me.aleksilassila.litematica.printer.integration.inventory;

public final class PickBlockRequestPolicy {
    private PickBlockRequestPolicy() {
    }

    public static boolean shouldConsume(
            MaterialReservation.State state,
            boolean takeItOutLoaded
    ) {
        return state != MaterialReservation.State.UNAVAILABLE || takeItOutLoaded;
    }
}
