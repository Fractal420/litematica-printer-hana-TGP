package me.aleksilassila.litematica.printer.integration.vanilla_refill;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.integration.inventory.InventoryProvider;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequest;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialReservation;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class ManualVanillaRefillAdapter implements InventoryProvider {
    @Override
    public String id() {
        return "manual_vanilla_refill";
    }

    @Override
    public MaterialReservation request(MaterialRequest request) {
        if (!Configs.Special.MANUAL_VANILLA_REFILL.getBooleanValue()) {
            return unavailable(request);
        }
        ManualVanillaRefillController controller = RuntimeAccess.get().manualVanillaRefill();
        if (controller.isBusy()) {
            return new MaterialReservation(request.token(), MaterialReservation.State.PENDING);
        }
        List<Item> items = new ArrayList<>(request.acceptedItems());
        controller.requestItems(items);
        if (controller.isBusy()) {
            return new MaterialReservation(request.token(), MaterialReservation.State.PENDING);
        }
        return unavailable(request);
    }

    @Override
    public MaterialReservation status(MaterialRequest request) {
        ManualVanillaRefillController controller = RuntimeAccess.get().manualVanillaRefill();
        if (controller.isBusy()) {
            return new MaterialReservation(request.token(), MaterialReservation.State.PENDING);
        }
        return unavailable(request);
    }

    @Override
    public boolean blocksPrinterWhilePending() {
        return true;
    }

    @Override
    public long pendingTimeoutTicks() {
        return 400L;
    }

    @Override
    public void reset() {
        RuntimeAccess.get().manualVanillaRefill().reset();
    }

    private static MaterialReservation unavailable(MaterialRequest request) {
        return new MaterialReservation(request.token(), MaterialReservation.State.UNAVAILABLE);
    }
}
