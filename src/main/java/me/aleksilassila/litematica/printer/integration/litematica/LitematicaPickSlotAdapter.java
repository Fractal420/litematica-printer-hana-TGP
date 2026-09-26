package me.aleksilassila.litematica.printer.integration.litematica;

import me.aleksilassila.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import net.minecraft.world.entity.player.Player;

public final class LitematicaPickSlotAdapter {
    private LitematicaPickSlotAdapter() {
    }

    public static int selectNextAvailable(Player player) {
        if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
            return -1;
        }
        int slotCount = InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().size();
        int nextIndex = InventoryUtilsAccessor.getNextPickSlotIndex();
        if (nextIndex >= slotCount) {
            nextIndex = 0;
        }
        for (int checked = 0; checked < slotCount; checked++) {
            int slot = InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().get(nextIndex);
            nextIndex = (nextIndex + 1) % slotCount;
            InventoryUtilsAccessor.setNextPickSlotIndex(nextIndex);
            if (InventoryUtilsAccessor.canPickToSlot(player.getInventory(), slot)) {
                return slot;
            }
        }
        return -1;
    }
}
