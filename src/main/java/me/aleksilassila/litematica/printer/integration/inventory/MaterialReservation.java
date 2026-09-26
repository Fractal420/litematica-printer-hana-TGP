package me.aleksilassila.litematica.printer.integration.inventory;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public record MaterialReservation(long token, State state, @Nullable Item selectedItem) {
    public MaterialReservation(long token, State state) {
        this(token, state, null);
    }

    public static MaterialReservation available(MaterialRequest request, Item selectedItem) {
        if (!request.accepts(selectedItem)) {
            throw new IllegalArgumentException("selectedItem must be accepted by request");
        }
        return new MaterialReservation(request.token(), State.AVAILABLE, selectedItem);
    }

    public enum State {
        AVAILABLE,
        PENDING,
        UNAVAILABLE
    }
}
