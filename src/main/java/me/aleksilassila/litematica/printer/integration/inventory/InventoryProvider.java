package me.aleksilassila.litematica.printer.integration.inventory;

public interface InventoryProvider {
    String id();

    MaterialReservation request(MaterialRequest request);

    default MaterialReservation status(MaterialRequest request) {
        return new MaterialReservation(request.token(), MaterialReservation.State.UNAVAILABLE);
    }

    default long pendingTimeoutTicks() {
        return 80L;
    }

    default boolean blocksPrinterWhilePending() {
        return true;
    }

    default void tick() {
    }

    default void reset() {
    }
}
