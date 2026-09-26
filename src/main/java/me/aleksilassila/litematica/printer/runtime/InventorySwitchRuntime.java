package me.aleksilassila.litematica.printer.runtime;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.utils.InventorySwitchGuard;
import me.aleksilassila.litematica.printer.utils.mods.TakeItOutUtils;

final class InventorySwitchRuntime implements RuntimeComponent {
    private final InventorySwitchGuard guard;

    InventorySwitchRuntime(InventorySwitchGuard guard) {
        this.guard = guard;
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        this.guard.reset();
        TakeItOutUtils.resetPending();
    }
}
